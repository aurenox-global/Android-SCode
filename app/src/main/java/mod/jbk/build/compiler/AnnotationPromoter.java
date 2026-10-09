package mod.jbk.build.compiler;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.ascode.android.utility.FileUtil;
import mod.jbk.util.LogUtil;

/**
 * Post-compile / post-shrink bytecode fix-up for the Java &rarr; JavaScript bridge.
 *
 * <p>Some toolchains that compile the in-app project (notably the bundled ECJ 3.26.0 used by
 * {@link a.a.a.ProjectBuilder#compileJavaCode()}), and some shrinkers in release mode, can emit
 * {@code @android.webkit.JavascriptInterface} as a compile-time-only annotation
 * ({@code RuntimeInvisibleAnnotations}, i.e. {@code VISIBILITY_BUILD}). When that happens the
 * Android WebView never discovers the bridge methods and the built app runs "mute"
 * ({@code window.AndroidBridge.*} is {@code undefined}).</p>
 *
 * <p>This helper walks every {@code .class} file under the compiled classes directory, or every
 * {@code .class} entry inside a shrinker output JAR, and, using ASM, forces the annotation with
 * descriptor {@code Landroid/webkit/JavascriptInterface;} to be written back as
 * {@code RuntimeVisibleAnnotations}. Only classes that actually reference that descriptor are
 * rewritten, and every failure is non-fatal (logged and skipped).</p>
 *
 * <p>It is called at every stage between the ECJ compile and the dexer, so that <b>whatever the
 * dexer actually consumes</b> (the raw ECJ output when not shrinking, or the shrinker output
 * {@code classes_proguard.jar} when shrinking) carries the annotation as runtime-visible.</p>
 */
public final class AnnotationPromoter {

    private static final String TAG = "AnnotationPromoter";

    /** The annotation whose retention we force to RUNTIME. */
    private static final String JAVASCRIPT_INTERFACE_DESC = "Landroid/webkit/JavascriptInterface;";

    private AnnotationPromoter() {
    }

    /**
     * Rewrites the given target so that {@code @android.webkit.JavascriptInterface} is stored as a
     * runtime-visible annotation. The target may be either a directory holding {@code .class} files
     * (the ECJ output) or a JAR file (a shrinker output such as {@code classes_proguard.jar}).
     * There is no need for the caller to catch anything; per-entry errors are logged and skipped,
     * and outside errors (e.g. an unreadable file) are swallowed too.
     *
     * @param target the directory or JAR holding compiled {@code .class} files
     * @return the number of class files that were actually rewritten
     */
    public static int promote(File target) {
        if (target == null) {
            LogUtil.w(TAG, "Nothing to promote: target is null");
            return 0;
        }

        if (target.isDirectory()) {
            return promoteDirectory(target);
        }

        if (target.isFile() && target.getName().toLowerCase().endsWith(".jar")) {
            return promoteJar(target);
        }

        if (!target.exists()) {
            LogUtil.w(TAG, "Nothing to promote, target does not exist: " + target);
        } else {
            LogUtil.w(TAG, "Nothing to promote, target is neither a directory nor a JAR: " + target);
        }
        return 0;
    }

    /**
     * Backwards-compatible convenience for a compiled classes directory.
     *
     * @param classesDirectory the directory holding the compiled {@code .class} files
     * @return the number of class files that were actually rewritten
     */
    public static int promoteDirectory(File classesDirectory) {
        if (classesDirectory == null || !classesDirectory.isDirectory()) {
            LogUtil.w(TAG, "Compiled classes directory does not exist, nothing to promote: " + classesDirectory);
            return 0;
        }

        int rewritten = 0;
        int scanned = 0;

        try {
            for (File classFile : FileUtil.listFilesRecursively(classesDirectory, ".class")) {
                scanned++;
                try {
                    if (rewriteClassBytesIfNeeded(classFile)) {
                        rewritten++;
                    }
                } catch (Throwable t) {
                    // Never let a single bad file break the whole build.
                    LogUtil.e(TAG, "Failed to promote annotations in " + classFile.getAbsolutePath(), t);
                }
            }
        } catch (Throwable t) {
            LogUtil.e(TAG, "Failed while scanning compiled classes for annotation promotion", t);
        }

        LogUtil.d(TAG, "Scanned " + scanned + " class file(s), rewrote " + rewritten
                + " containing " + JAVASCRIPT_INTERFACE_DESC);
        return rewritten;
    }

    /**
     * Rewrites every {@code .class} entry inside a JAR so that
     * {@code @android.webkit.JavascriptInterface} is stored as runtime-visible. Class entries that
     * do not reference the descriptor are copied verbatim; every other entry is copied unchanged.
     *
     * @param jarFile the JAR to rewrite in place
     * @return the number of class entries that were actually rewritten
     */
    public static int promoteJar(File jarFile) {
        if (jarFile == null || !jarFile.isFile()) {
            LogUtil.w(TAG, "JAR does not exist, nothing to promote: " + jarFile);
            return 0;
        }

        File temporary = new File(jarFile.getParentFile(), jarFile.getName() + ".promoted.tmp");
        int rewritten = 0;
        int scanned = 0;

        try (ZipFile zipFile = new ZipFile(jarFile);
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(temporary))) {

            java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                if (entry.isDirectory()) {
                    out.putNextEntry(new ZipEntry(entry.getName()));
                    out.closeEntry();
                    continue;
                }

                byte[] data;
                try (InputStream in = zipFile.getInputStream(entry)) {
                    data = readFully(in);
                }

                if (entry.getName().endsWith(".class")) {
                    scanned++;
                    try {
                        byte[] promoted = rewriteClassBytes(data);
                        if (promoted != null) {
                            data = promoted;
                            rewritten++;
                        }
                    } catch (Throwable t) {
                        // Keep the original bytes on any failure.
                        LogUtil.e(TAG, "Failed to promote annotations in JAR entry " + entry.getName(), t);
                    }
                }

                ZipEntry copy = new ZipEntry(entry.getName());
                if (entry.getTime() >= 0) {
                    copy.setTime(entry.getTime());
                }
                out.putNextEntry(copy);
                out.write(data);
                out.closeEntry();
            }
        } catch (Throwable t) {
            LogUtil.e(TAG, "Failed to promote annotations in JAR " + jarFile.getAbsolutePath(), t);
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
            return rewritten;
        }

        try {
            if (temporary.length() > 0) {
                Files.move(temporary.toPath(), jarFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else {
                //noinspection ResultOfMethodCallIgnored
                temporary.delete();
            }
        } catch (Throwable t) {
            LogUtil.e(TAG, "Failed to replace JAR " + jarFile.getAbsolutePath() + " with promoted copy", t);
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
        }

        LogUtil.d(TAG, "JAR " + jarFile.getName() + ": scanned " + scanned + " class entrie(s), rewrote "
                + rewritten + " containing " + JAVASCRIPT_INTERFACE_DESC);
        return rewritten;
    }

    /**
     * @return {@code true} if the class file contained the JavascriptInterface descriptor and was
     * rewritten, {@code false} if it was left untouched
     */
    private static boolean rewriteClassBytesIfNeeded(File classFile) throws IOException {
        byte[] original = Files.readAllBytes(classFile.toPath());
        byte[] rewritten = rewriteClassBytes(original);
        if (rewritten == null) {
            return false;
        }

        try (OutputStream out = new FileOutputStream(classFile)) {
            out.write(rewritten);
        }
        return true;
    }

    /**
     * @param original the original class bytes
     * @return the rewritten class bytes, or {@code null} when the class did not reference the
     * JavascriptInterface descriptor (so the caller can keep the original untouched)
     */
    private static byte[] rewriteClassBytes(byte[] original) {
        /* Cheap pre-check: the descriptor only appears if the constant pool references it. Skip
         * untouched classes so we don't churn unrelated bytecode through an ASM round-trip. */
        if (!new String(original, StandardCharsets.ISO_8859_1).contains(JAVASCRIPT_INTERFACE_DESC)) {
            return null;
        }

        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(0);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM5, writer) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                return super.visitAnnotation(descriptor, forceVisible(descriptor, visible));
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                                           String signature, Object value) {
                FieldVisitor fieldVisitor = super.visitField(access, name, descriptor, signature, value);
                if (fieldVisitor == null) {
                    return null;
                }
                return new FieldVisitor(Opcodes.ASM5, fieldVisitor) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        return super.visitAnnotation(descriptor, forceVisible(descriptor, visible));
                    }
                };
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor methodVisitor =
                        super.visitMethod(access, name, descriptor, signature, exceptions);
                if (methodVisitor == null) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM5, methodVisitor) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        return super.visitAnnotation(descriptor, forceVisible(descriptor, visible));
                    }

                    @Override
                    public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor,
                                                                      boolean visible) {
                        return super.visitParameterAnnotation(parameter, descriptor,
                                forceVisible(descriptor, visible));
                    }
                };
            }
        };

        reader.accept(visitor, 0);
        return writer.toByteArray();
    }

    private static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private static boolean forceVisible(String descriptor, boolean visible) {
        return JAVASCRIPT_INTERFACE_DESC.equals(descriptor) || visible;
    }
}

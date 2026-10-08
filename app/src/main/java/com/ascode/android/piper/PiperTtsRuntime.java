package com.ascode.android.piper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Stages an installed, offline Piper voice and the bundled sherpa-onnx runtime into a project's
 * build inputs, so that an app generated with the WebView TTS helper in Piper mode actually ships
 * the model, the native library and the Java classes it needs.
 *
 * <p>This class is intentionally free of Android APIs so the staging logic is unit-testable on the
 * JVM. The Android-facing orchestration lives in {@link PiperTtsPackaging}.</p>
 *
 * <h3>Where each piece goes (matching {@code ProjectBuilder})</h3>
 * <ul>
 *   <li><b>Assets</b> — copied into {@code yq.assetsPath}, which
 *       {@code ResourceCompiler.compile()} passes to AAPT2 via {@code -A}, so the model ends up in
 *       the final APK's {@code assets/}.</li>
 *   <li><b>Native library</b> — copied into
 *       {@code FilePathUtil.getPathNativelibs(sc_id)}/{@code <abi>/}; {@code ProjectBuilder.buildApk()}
 *       packages that directory with {@code apkBuilder.addNativeLibraries(...)}. Only the phone
 *       ABIs ({@link #DEFAULT_PACKAGING_ABIS}) plus any ABI the project itself targets are copied,
 *       so x86/x86_64 do not bloat real-device APKs.</li>
 *   <li><b>Classes</b> — the sherpa {@code classes.jar} and {@code kotlin-stdlib.jar} are copied into
 *       the project's {@code files/classpath/} (picked up by {@code ProjectBuilder.getClasspath()}),
 *       and a pre-dexed {@code .dex} is dropped into {@code yq.binDirectoryPath} (picked up by
 *       {@code ProjectBuilder.buildApk()}'s dex scan) for the runtime.</li>
 * </ul>
 */
public final class PiperTtsRuntime {

    /** Asset sub-directory the generated helper reads the voice from ({@code assets/piper/...}). */
    public static final String ASSETS_SUBDIR = "piper";
    public static final String MODEL_ASSET = ASSETS_SUBDIR + "/model.onnx";
    public static final String TOKENS_ASSET = ASSETS_SUBDIR + "/tokens.txt";
    public static final String ESPEAK_ASSET_DIR = ASSETS_SUBDIR + "/espeak-ng-data";

    public static final String SO_NAME = "libsherpa-onnx-jni.so";

    /**
     * Every ABI the downloaded sherpa-onnx runtime may contain. {@link PiperInstaller} keeps all of
     * them on the device, so enabling another ABI later needs no re-download: only an entry in
     * {@link #DEFAULT_PACKAGING_ABIS} (or a project that targets it) is required.
     */
    public static final String[] SUPPORTED_ABIS = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"};

    /**
     * ABIs packaged into a generated app by default: the ones real phones use.
     *
     * <p>x86/x86_64 are deliberately left out. They are emulator-only and, being the two biggest
     * runtime payloads, would add roughly 33 MB <em>uncompressed</em> to every project that enables
     * Piper. To ship another ABI by default, add it here; a project that needs one only because it
     * targets it itself is handled by {@link #packagingAbis(java.util.Collection)}.</p>
     */
    public static final String[] DEFAULT_PACKAGING_ABIS = {"arm64-v8a", "armeabi-v7a"};

    private PiperTtsRuntime() {
    }

    /**
     * @param voiceDir a voice directory such as {@code .AndroidSCode/data/<sc_id>/tts/}
     * @return {@code true} when it contains a {@code *.onnx}, a {@code tokens.txt} and an
     *         {@code espeak-ng-data/} directory, i.e. everything the helper needs.
     */
    public static boolean isVoiceInstalled(File voiceDir) {
        return findVoiceModel(voiceDir) != null
                && new File(voiceDir, "tokens.txt").isFile()
                && new File(voiceDir, "espeak-ng-data").isDirectory();
    }

    /**
     * @return the voice's {@code *.onnx} file (ignoring the {@code *.onnx.json} config) or
     *         {@code null} when none is present.
     */
    public static File findVoiceModel(File voiceDir) {
        if (voiceDir == null || !voiceDir.isDirectory()) {
            return null;
        }
        File[] files = voiceDir.listFiles();
        if (files == null) {
            return null;
        }
        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (file.isFile() && name.endsWith(".onnx")) {
                return file;
            }
        }
        return null;
    }

    /**
     * Copies the installed voice into {@code assetsDir/piper} under the fixed names the generated
     * helper expects ({@code model.onnx}, {@code tokens.txt}, {@code espeak-ng-data/}).
     *
     * @return {@code true} when something was staged
     */
    public static boolean stageAssets(File voiceDir, File assetsDir) throws IOException {
        if (!isVoiceInstalled(voiceDir) || assetsDir == null) {
            return false;
        }
        File outDir = new File(assetsDir, ASSETS_SUBDIR);
        if (!outDir.isDirectory() && !outDir.mkdirs()) {
            throw new IOException("Cannot create " + outDir);
        }
        copyFile(findVoiceModel(voiceDir), new File(outDir, "model.onnx"));
        copyFile(new File(voiceDir, "tokens.txt"), new File(outDir, "tokens.txt"));
        copyDir(new File(voiceDir, "espeak-ng-data"), new File(outDir, "espeak-ng-data"));
        return true;
    }

    /**
     * Copies {@code runtimeDir/<abi>/libsherpa-onnx-jni.so} into
     * {@code nativeLibsDir/<abi>/libsherpa-onnx-jni.so} for the default ABIs actually present.
     *
     * @return the ABIs that were staged
     */
    public static List<String> stageNativeLibs(File runtimeDir, File nativeLibsDir) throws IOException {
        return stageNativeLibs(runtimeDir, nativeLibsDir, null);
    }

    /**
     * Copies {@code runtimeDir/<abi>/libsherpa-onnx-jni.so} into
     * {@code nativeLibsDir/<abi>/libsherpa-onnx-jni.so} for every ABI that is both requested and
     * present in {@code runtimeDir}.
     *
     * @param projectAbis ABIs the project itself targets (see {@link #projectOwnAbis(File)}); they
     *                    are added to {@link #DEFAULT_PACKAGING_ABIS}. May be {@code null}.
     * @return the ABIs that were staged
     */
    public static List<String> stageNativeLibs(File runtimeDir, File nativeLibsDir,
                                               java.util.Collection<String> projectAbis) throws IOException {
        List<String> staged = new ArrayList<>();
        if (runtimeDir == null || nativeLibsDir == null) {
            return staged;
        }
        for (String abi : packagingAbis(projectAbis)) {
            File so = new File(new File(runtimeDir, abi), SO_NAME);
            if (!so.isFile()) {
                continue;
            }
            File abiDir = new File(nativeLibsDir, abi);
            if (!abiDir.isDirectory() && !abiDir.mkdirs()) {
                throw new IOException("Cannot create " + abiDir);
            }
            copyFile(so, new File(abiDir, SO_NAME));
            staged.add(abi);
        }
        return staged;
    }

    /**
     * Resolves the ABIs to package for one project: {@link #DEFAULT_PACKAGING_ABIS} plus any ABI
     * the project itself targets ({@code projectAbis}), de-duplicated and order-preserving. A
     * project that targets nothing extra therefore gets exactly the phone defaults.
     */
    public static List<String> packagingAbis(java.util.Collection<String> projectAbis) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        for (String abi : DEFAULT_PACKAGING_ABIS) {
            result.add(abi);
        }
        if (projectAbis != null) {
            for (String abi : projectAbis) {
                if (abi != null && !abi.trim().isEmpty()) {
                    result.add(abi.trim());
                }
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * ABIs the project targets through its own native libraries: the sub-directories of
     * {@code nativeLibsDir} that already hold a {@code .so} other than Piper's own
     * {@link #SO_NAME}. Piper's {@code .so} is copied into that very directory by this packaging
     * mechanism, so it must not count as a pre-existing project target.
     *
     * @return the ABI directory names, or an empty list when the project ships no native code
     */
    public static List<String> projectOwnAbis(File nativeLibsDir) {
        List<String> abis = new ArrayList<>();
        File[] dirs = nativeLibsDir == null ? null : nativeLibsDir.listFiles();
        if (dirs == null) {
            return abis;
        }
        for (File dir : dirs) {
            if (!dir.isDirectory()) {
                continue;
            }
            File[] sos = dir.listFiles((d, name) -> name.endsWith(".so") && !name.equals(SO_NAME));
            if (sos != null && sos.length > 0) {
                abis.add(dir.getName());
            }
        }
        return abis;
    }

    /**
     * @return the {@code .jar} files bundled with the runtime (sherpa {@code classes.jar},
     *         {@code kotlin-stdlib.jar}, ...), which belong on the project's compile classpath.
     */
    public static List<File> runtimeJars(File runtimeDir) {
        List<File> jars = new ArrayList<>();
        File[] files = runtimeDir == null ? null : runtimeDir.listFiles();
        if (files == null) {
            return jars;
        }
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase().endsWith(".jar")) {
                jars.add(file);
            }
        }
        return jars;
    }

    /**
     * @return the pre-dexed runtime ({@code *.dex}) that must ship inside the APK, or {@code null}.
     */
    public static File runtimeDex(File runtimeDir) {
        File[] files = runtimeDir == null ? null : runtimeDir.listFiles();
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase().endsWith(".dex")) {
                return file;
            }
        }
        return null;
    }

    /** Recursively copies {@code sourceDir} into {@code destDir}. */
    public static void copyDir(File sourceDir, File destDir) throws IOException {
        if (!destDir.isDirectory() && !destDir.mkdirs()) {
            throw new IOException("Cannot create " + destDir);
        }
        File[] children = sourceDir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            File dest = new File(destDir, child.getName());
            if (child.isDirectory()) {
                copyDir(child, dest);
            } else {
                copyFile(child, dest);
            }
        }
    }

    private static void copyFile(File source, File dest) throws IOException {
        try (InputStream in = new java.io.FileInputStream(source);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        }
    }
}

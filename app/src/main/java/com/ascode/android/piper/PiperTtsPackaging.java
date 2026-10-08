package com.ascode.android.piper;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import a.a.a.ProjectBuilder;
import com.ascode.android.AscodeApplication;
import com.ascode.android.utility.FileUtil;
import com.ascode.android.webview.ProjectWebViewSettings;

/**
 * Android-facing orchestration that wires {@link PiperTtsRuntime} into a project build.
 *
 * <p>Called once, from {@link ProjectBuilder#compileResources()} (i.e. before AAPT2 links assets and
 * before javac runs), so a single hook covers every build flow (Run, Export, Flutter orchestrator).
 * It is a no-op unless the project explicitly enabled the offline Piper engine, so projects that
 * never touched the switch keep the exact previous build.</p>
 */
public final class PiperTtsPackaging {

    private static final String TAG = "PiperTtsPackaging";

    /** Zip bundled in the IDE's own {@code assets/} carrying the sherpa-onnx runtime. */
    private static final String RUNTIME_ZIP_ASSET = "piper_runtime.zip";
    private static final String RUNTIME_DIR_NAME = "piper_runtime";

    private PiperTtsPackaging() {
    }

    /**
     * Stages voice assets + native library + runtime classes/dex for the given project when its
     * "offline Piper" setting is on and a voice is installed. Never throws: any failure is logged
     * and the build proceeds with the system engine (the generated helper also degrades at runtime).
     */
    public static void prepareIfEnabled(ProjectBuilder builder) {
        try {
            String scId = builder.yq.sc_id;
            if (!new ProjectWebViewSettings(scId).isTtsPiperEnabled()) {
                return;
            }

            File dataDir = new File(Environment.getExternalStorageDirectory(), ".AndroidSCode/data/" + scId);
            File voiceDir = new File(dataDir, "tts");
            if (!PiperTtsRuntime.isVoiceInstalled(voiceDir)) {
                Log.w(TAG, "Piper activado pero no hay voz instalada en " + voiceDir + "; se usará el motor del sistema.");
                return;
            }

            File runtimeDir = ensureRuntimeExtracted();
            if (runtimeDir == null) {
                Log.w(TAG, "Piper activado pero falta el runtime (" + RUNTIME_ZIP_ASSET + "); se usará el motor del sistema.");
                return;
            }

            PiperTtsRuntime.stageAssets(voiceDir, new File(builder.yq.assetsPath));

            File nativeLibsDir = new File(builder.fpu.getPathNativelibs(scId));
            List<String> abis = PiperTtsRuntime.stageNativeLibs(runtimeDir, nativeLibsDir);

            File classpathDir = new File(dataDir, "files/classpath");
            if (!classpathDir.isDirectory() && !classpathDir.mkdirs()) {
                Log.w(TAG, "No se pudo crear " + classpathDir);
            }
            for (File jar : PiperTtsRuntime.runtimeJars(runtimeDir)) {
                FileUtil.copyFile(jar.getAbsolutePath(), new File(classpathDir, jar.getName()).getAbsolutePath());
            }

            File dex = PiperTtsRuntime.runtimeDex(runtimeDir);
            if (dex != null) {
                FileUtil.copyFile(dex.getAbsolutePath(), new File(builder.yq.binDirectoryPath, dex.getName()).getAbsolutePath());
            }

            Log.i(TAG, "Piper empaquetado: abis=" + abis + " assets=piper/ voz=" + voiceDir.getName());
        } catch (Throwable t) {
            Log.w(TAG, "Piper: fallo al empaquetar, sigo con el motor del sistema: " + t);
        }
    }

    /** Extracts {@code assets/piper_runtime.zip} into {@code filesDir/piper_runtime} once. */
    private static File ensureRuntimeExtracted() {
        Context context = AscodeApplication.getContext();
        File destDir = new File(context.getFilesDir(), RUNTIME_DIR_NAME);
        File marker = new File(destDir, ".extracted");
        if (marker.isFile()) {
            return destDir;
        }
        try (InputStream raw = context.getAssets().open(RUNTIME_ZIP_ASSET)) {
            if (!destDir.isDirectory() && !destDir.mkdirs()) {
                return null;
            }
            byte[] buffer = new byte[16384];
            try (ZipInputStream zip = new ZipInputStream(raw)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    File out = new File(destDir, entry.getName());
                    if (entry.isDirectory()) {
                        out.mkdirs();
                        continue;
                    }
                    File parent = out.getParentFile();
                    if (parent != null) {
                        parent.mkdirs();
                    }
                    try (OutputStream os = new FileOutputStream(out)) {
                        int read;
                        while ((read = zip.read(buffer)) > 0) {
                            os.write(buffer, 0, read);
                        }
                    }
                }
            }
            marker.createNewFile();
            return destDir;
        } catch (Exception e) {
            Log.w(TAG, "No se pudo extraer " + RUNTIME_ZIP_ASSET + ": " + e);
            return null;
        }
    }
}

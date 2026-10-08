package com.ascode.android.piper;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.List;

import a.a.a.ProjectBuilder;
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
                // The offline Piper engine was retired: make sure nothing it staged earlier is left
                // inside the project. Its jars/dex used to be copied into the project's persistent
                // classpath/dex folders, which made R8 run out of memory on-device when shrinking.
                cleanupStagedArtifacts(builder);
                return;
            }

            File voiceDir = PiperInstaller.voiceDir(scId);
            if (!PiperTtsRuntime.isVoiceInstalled(voiceDir)) {
                Log.w(TAG, "Piper activado pero no hay voz instalada en " + voiceDir + "; se usará el motor del sistema.");
                return;
            }

            File runtimeDir = PiperInstaller.runtimeDir(scId);
            if (!PiperInstaller.isRuntimeInstalled(runtimeDir)) {
                Log.w(TAG, "Piper activado pero falta el runtime descargable en " + runtimeDir
                        + "; descárgalo desde Ajustes de WebView. Se usará el motor del sistema.");
                return;
            }

            PiperTtsRuntime.stageAssets(voiceDir, new File(builder.yq.assetsPath));

            File nativeLibsDir = new File(builder.fpu.getPathNativelibs(scId));
            // Only the phone ABIs ship by default (arm64-v8a + armeabi-v7a). If the project already
            // targets another ABI with its own native code (e.g. an x86_64 .so), package Piper for
            // it too so the generated app keeps working on those devices.
            List<String> projectAbis = PiperTtsRuntime.projectOwnAbis(nativeLibsDir);
            List<String> abis = PiperTtsRuntime.stageNativeLibs(runtimeDir, nativeLibsDir, projectAbis);

            File classpathDir = new File(
                    new File(Environment.getExternalStorageDirectory(), ".AndroidSCode/data/" + scId),
                    "files/classpath");
            if (!classpathDir.isDirectory() && !classpathDir.mkdirs()) {
                Log.w(TAG, "No se pudo crear " + classpathDir);
            }
            for (File jar : PiperTtsRuntime.runtimeJars(runtimeDir)) {
                FileUtil.copyFile(jar.getAbsolutePath(), new File(classpathDir, jar.getName()).getAbsolutePath());
            }

            // Pre-dexed runtime classes: drop them next to the project's own dex output so
            // ProjectBuilder.getDexFilesReady() picks them up in both the merge and the
            // debug/multi-dex paths (see ProjectBuilder#buildApk).
            File dex = PiperTtsRuntime.runtimeDex(runtimeDir);
            if (dex != null) {
                File dexDir = new File(builder.yq.binDirectoryPath, "dex");
                if (!dexDir.isDirectory() && !dexDir.mkdirs()) {
                    Log.w(TAG, "No se pudo crear " + dexDir);
                } else {
                    FileUtil.copyFile(dex.getAbsolutePath(), new File(dexDir, dex.getName()).getAbsolutePath());
                }
            }

            Log.i(TAG, "Piper empaquetado: abis=" + abis
                    + (projectAbis.isEmpty() ? "" : " (abis del proyecto=" + projectAbis + ")")
                    + " assets=piper/ voz=" + voiceDir.getName());
        } catch (Throwable t) {
            Log.w(TAG, "Piper: fallo al empaquetar, sigo con el motor del sistema: " + t);
        }
    }

    /**
     * Removes everything a previous build may have staged inside the project for the offline Piper
     * engine: its jars (project classpath), its pre-dexed {@code .dex} (project dex folder), its
     * {@code .so} (project native libs) and its assets folder. Runs on every build while the engine
     * is disabled, so a project that once enabled it goes back to a clean, R8-friendly state.
     */
    public static void cleanupStagedArtifacts(ProjectBuilder builder) {
        try {
            String scId = builder.yq.sc_id;

            File classpathDir = new File(
                    new File(Environment.getExternalStorageDirectory(), ".AndroidSCode/data/" + scId),
                    "files/classpath");
            deleteMatching(classpathDir, name -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".jar")
                        && (lower.contains("sherpa") || lower.contains("kotlin-stdlib") || lower.contains("piper"));
            });

            File dexDir = new File(builder.yq.binDirectoryPath, "dex");
            deleteMatching(dexDir, name -> name.toLowerCase().contains("piper"));

            File nativeLibsDir = new File(builder.fpu.getPathNativelibs(scId));
            File[] abiDirs = nativeLibsDir.listFiles();
            if (abiDirs != null) {
                for (File abiDir : abiDirs) {
                    if (abiDir.isDirectory()) {
                        File so = new File(abiDir, PiperTtsRuntime.SO_NAME);
                        if (so.isFile() && so.delete()) {
                            Log.i(TAG, "Piper: eliminado " + so);
                        }
                    }
                }
            }

            File assetsPiper = new File(builder.yq.assetsPath, PiperTtsRuntime.ASSETS_SUBDIR);
            if (assetsPiper.exists()) {
                deleteRecursively(assetsPiper);
                Log.i(TAG, "Piper: eliminados los assets de " + assetsPiper);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Piper: fallo al limpiar restos: " + t);
        }
    }

    private static void deleteMatching(File dir, NameFilter filter) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && filter.matches(file.getName()) && file.delete()) {
                Log.i(TAG, "Piper: eliminado " + file);
            }
        }
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private interface NameFilter {
        boolean matches(String name);
    }
}

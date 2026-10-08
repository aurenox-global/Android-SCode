package com.ascode.android.utility;

import android.os.Environment;
import android.util.Log;

import java.io.File;

import a.a.a.ProjectBuilder;

/**
 * Removes leftovers of the retired built-in offline TTS engine (Piper / sherpa-onnx) from a project.
 *
 * <p>The engine used to copy its jars into the project's persistent classpath folder, a pre-dexed
 * {@code .dex} into the project's dex output, {@code libsherpa-onnx-jni.so} into the project's
 * native libs and the voice files under an {@code assets/piper} folder. Those files survive the
 * feature being removed, so a project that once enabled it kept shipping a ~16 MB bigger APK and
 * could even make R8 run out of memory while shrinking.</p>
 *
 * <p>This runs on every build, so such a project goes back to a clean state by itself. It only ever
 * deletes files it recognises as belonging to that engine; anything else in those folders is left
 * untouched.</p>
 */
public final class StaleTtsArtifacts {

    private static final String TAG = "StaleTtsArtifacts";

    /** Native library staged by the retired engine. */
    private static final String ENGINE_SO = "libsherpa-onnx-jni.so";
    /** Assets subfolder staged by the retired engine. */
    private static final String ENGINE_ASSETS_SUBDIR = "piper";
    /** Classpath jars staged by the retired engine (sherpa classes, kotlin stdlib). */
    private static final String[] ENGINE_JAR_HINTS = {"sherpa", "kotlin-stdlib", "piper"};

    private StaleTtsArtifacts() {
    }

    public static void clean(ProjectBuilder builder) {
        try {
            String scId = builder.yq.sc_id;

            File classpathDir = new File(
                    new File(Environment.getExternalStorageDirectory(), ".AndroidSCode/data/" + scId),
                    "files/classpath");
            deleteFiles(classpathDir, name -> {
                String lower = name.toLowerCase();
                if (!lower.endsWith(".jar")) {
                    return false;
                }
                for (String hint : ENGINE_JAR_HINTS) {
                    if (lower.contains(hint)) {
                        return true;
                    }
                }
                return false;
            });

            deleteFiles(new File(builder.yq.binDirectoryPath, "dex"),
                    name -> name.toLowerCase().contains("piper"));

            File nativeLibsDir = new File(builder.fpu.getPathNativelibs(scId));
            File[] abiDirs = nativeLibsDir.listFiles();
            if (abiDirs != null) {
                for (File abiDir : abiDirs) {
                    if (!abiDir.isDirectory()) {
                        continue;
                    }
                    File so = new File(abiDir, ENGINE_SO);
                    if (so.isFile() && so.delete()) {
                        Log.i(TAG, "eliminado " + so);
                    }
                }
            }

            File engineAssets = new File(builder.yq.assetsPath, ENGINE_ASSETS_SUBDIR);
            if (engineAssets.exists()) {
                deleteRecursively(engineAssets);
                Log.i(TAG, "eliminados los assets de " + engineAssets);
            }
        } catch (Throwable t) {
            // Never break a build because of this cleanup.
            Log.w(TAG, "no se pudieron limpiar los restos del motor retirado: " + t);
        }
    }

    private static void deleteFiles(File dir, NameFilter filter) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && filter.matches(file.getName()) && file.delete()) {
                Log.i(TAG, "eliminado " + file);
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

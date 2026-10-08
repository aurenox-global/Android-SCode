package com.ascode.android.piper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Verifies the offline Piper packaging mechanism ({@link PiperTtsRuntime}) without Android: given a
 * voice directory shaped like {@code .AndroidSCode/data/<sc_id>/tts/} and a runtime directory shaped
 * like the bundled sherpa runtime, the staging must place the model/tokens/espeak data into the
 * project assets, the {@code .so} per ABI into the native-libs directory and the runtime jars/dex
 * where {@code ProjectBuilder} expects them.
 */
public class PiperTtsRuntimeTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File write(File file, String content) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    private File buildVoiceDir() throws Exception {
        File voice = tmp.newFolder("tts");
        write(new File(voice, "es_MX-ald-medium.onnx"), "onnx-bytes");
        write(new File(voice, "es_MX-ald-medium.onnx.json"), "{\"sample_rate\":22050}");
        write(new File(voice, "tokens.txt"), "tokens");
        write(new File(voice, "MODEL_CARD"), "unlicense");
        write(new File(voice, "espeak-ng-data/es_dict"), "es");
        write(new File(voice, "espeak-ng-data/en_dict"), "en");
        return voice;
    }

    private File buildRuntimeDir() throws Exception {
        File runtime = tmp.newFolder("piper_runtime");
        write(new File(runtime, "arm64-v8a/libsherpa-onnx-jni.so"), "arm64-so");
        write(new File(runtime, "armeabi-v7a/libsherpa-onnx-jni.so"), "v7a-so");
        write(new File(runtime, "sherpa-classes.jar"), "jar");
        write(new File(runtime, "kotlin-stdlib.jar"), "jar");
        write(new File(runtime, "sherpa-runtime.dex"), "dex");
        return runtime;
    }

    @Test
    public void detectsInstalledVoiceAndIgnoresTheOnnxJson() throws Exception {
        File voice = buildVoiceDir();
        assertTrue(PiperTtsRuntime.isVoiceInstalled(voice));
        File model = PiperTtsRuntime.findVoiceModel(voice);
        assertNotNull(model);
        assertTrue(model.getName().endsWith(".onnx"));
        assertFalse(model.getName().endsWith(".onnx.json"));
    }

    @Test
    public void incompleteVoiceIsRejected() throws Exception {
        File voice = tmp.newFolder("empty");
        assertFalse(PiperTtsRuntime.isVoiceInstalled(voice));
    }

    @Test
    public void stagesAssetsUnderPiperWithTheFixedNames() throws Exception {
        File voice = buildVoiceDir();
        File assets = tmp.newFolder("assets");

        assertTrue(PiperTtsRuntime.stageAssets(voice, assets));

        File piper = new File(assets, "piper");
        assertTrue(new File(piper, "model.onnx").isFile());
        assertTrue(new File(piper, "tokens.txt").isFile());
        assertTrue(new File(piper, "espeak-ng-data/es_dict").isFile());
        assertTrue(new File(piper, "espeak-ng-data/en_dict").isFile());
        // The generated helper is only given the fixed path: the differently-named source must not leak.
        assertFalse(new File(piper, "es_MX-ald-medium.onnx").exists());
    }

    @Test
    public void stagesNativeLibsPerAbiThatExists() throws Exception {
        File runtime = buildRuntimeDir();
        File nativeLibs = tmp.newFolder("native_libs");

        List<String> abis = PiperTtsRuntime.stageNativeLibs(runtime, nativeLibs);

        assertEquals(List.of("arm64-v8a", "armeabi-v7a"), abis);
        assertTrue(new File(nativeLibs, "arm64-v8a/libsherpa-onnx-jni.so").isFile());
        assertTrue(new File(nativeLibs, "armeabi-v7a/libsherpa-onnx-jni.so").isFile());
        assertFalse(new File(nativeLibs, "x86_64").exists());
    }

    @Test
    public void exposesRuntimeJarsAndDex() throws Exception {
        File runtime = buildRuntimeDir();
        List<File> jars = PiperTtsRuntime.runtimeJars(runtime);
        assertEquals(2, jars.size());
        assertNotNull(PiperTtsRuntime.runtimeDex(runtime));
        assertEquals("sherpa-runtime.dex", PiperTtsRuntime.runtimeDex(runtime).getName());
    }
}

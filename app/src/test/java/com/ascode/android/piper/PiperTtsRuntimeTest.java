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
        write(new File(runtime, "x86/libsherpa-onnx-jni.so"), "x86-so");
        write(new File(runtime, "x86_64/libsherpa-onnx-jni.so"), "x86_64-so");
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
    public void stagesOnlyThePhoneAbisByDefault() throws Exception {
        // The runtime ships all four ABIs; only arm64-v8a and armeabi-v7a must reach the project.
        File runtime = buildRuntimeDir();
        File nativeLibs = tmp.newFolder("native_libs");

        List<String> abis = PiperTtsRuntime.stageNativeLibs(runtime, nativeLibs);

        assertEquals(List.of("arm64-v8a", "armeabi-v7a"), abis);
        assertTrue(new File(nativeLibs, "arm64-v8a/libsherpa-onnx-jni.so").isFile());
        assertTrue(new File(nativeLibs, "armeabi-v7a/libsherpa-onnx-jni.so").isFile());
        assertFalse(new File(nativeLibs, "x86").exists());
        assertFalse(new File(nativeLibs, "x86_64").exists());
    }

    @Test
    public void stagesTheProjectsOwnAbisOnTopOfTheDefaults() throws Exception {
        File runtime = buildRuntimeDir();
        File nativeLibs = tmp.newFolder("native_libs");

        List<String> abis = PiperTtsRuntime.stageNativeLibs(runtime, nativeLibs, List.of("x86_64"));

        assertEquals(List.of("arm64-v8a", "armeabi-v7a", "x86_64"), abis);
        assertTrue(new File(nativeLibs, "x86_64/libsherpa-onnx-jni.so").isFile());
        assertFalse(new File(nativeLibs, "x86").exists());
    }

    @Test
    public void packagingAbisUsesPhoneDefaultsAndUnionsProjectTargets() throws Exception {
        assertEquals(List.of("arm64-v8a", "armeabi-v7a"), PiperTtsRuntime.packagingAbis(null));
        assertEquals(List.of("arm64-v8a", "armeabi-v7a"),
                PiperTtsRuntime.packagingAbis(java.util.Collections.emptyList()));
        assertEquals(List.of("arm64-v8a", "armeabi-v7a", "x86_64"),
                PiperTtsRuntime.packagingAbis(List.of("x86_64")));
        // The project's own target must not duplicate a default and must ignore blanks.
        assertEquals(List.of("arm64-v8a", "armeabi-v7a"),
                PiperTtsRuntime.packagingAbis(List.of("arm64-v8a", " ")));
    }

    @Test
    public void projectOwnAbisSeesForeignNativeCodeButNotPipersOwn() throws Exception {
        File nativeLibs = tmp.newFolder("native_libs");
        assertTrue(PiperTtsRuntime.projectOwnAbis(nativeLibs).isEmpty());

        write(new File(nativeLibs, "x86_64/libother.so"), "other");
        write(new File(nativeLibs, "arm64-v8a/" + PiperTtsRuntime.SO_NAME), "piper");

        assertEquals(List.of("x86_64"), PiperTtsRuntime.projectOwnAbis(nativeLibs));
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

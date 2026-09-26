package io.ascode.android;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LocalAiBridge implements AutoCloseable {

    /**
     * Receives generated text incrementally while the model is still working.
     * Called from the native generation thread, so implementations must not
     * block and should hop to the UI thread before touching views.
     */
    public interface TokenCallback {
        void onToken(String token);
    }

    private static boolean loadAttempted;
    private static Throwable loadError;
    private static final Map<String, String> ARCHITECTURE_CACHE = new HashMap<>();

    private long handle;

    public static synchronized boolean isNativeAvailable() {
        try {
            ensureNativeLoaded();
            return true;
        } catch (LocalAiException ignored) {
            return false;
        }
    }

    public static synchronized String getNativeStatus() {
        if (isNativeAvailable()) {
            return "Native engine ready: lib" + LocalAiConfig.NATIVE_LIBRARY_NAME + ".so";
        }
        String detail = loadError == null ? "unknown error" : loadError.getMessage();
        return "Native engine missing: lib" + LocalAiConfig.NATIVE_LIBRARY_NAME + ".so (" + detail + ")";
    }

    private static synchronized void ensureNativeLoaded() throws LocalAiException {
        if (!loadAttempted) {
            loadAttempted = true;
            try {
                System.loadLibrary(LocalAiConfig.NATIVE_LIBRARY_NAME);
                // Comprobacion real del enlace JNI: si la libreria instalada es de otra version
                // (simbolos JNI que no coinciden con esta clase) se detecta aqui y se avisa con
                // claridad, en lugar de fallar a mitad de una generacion de texto.
                nativeRelease(0L);
            } catch (Throwable throwable) {
                loadError = throwable;
            }
        }
        if (loadError != null) {
            String raw = loadError.getMessage() == null ? loadError.toString() : loadError.getMessage();
            String detail;
            if (raw.contains("No implementation found")) {
                detail = "The installed native engine does not match this version of Android SCode "
                        + "(JNI symbol mismatch). ABI " + firstAbi()
                        + ". Uninstall and reinstall from the official APK.";
            } else if (loadError instanceof UnsatisfiedLinkError) {
                detail = "The native engine could not be loaded. ABI " + firstAbi() + ": " + raw;
            } else {
                detail = "Missing llama.cpp native engine. Add lib" + LocalAiConfig.NATIVE_LIBRARY_NAME
                        + ".so for this device ABI, then rebuild/install Android SCode.";
            }
            throw new LocalAiException(detail, loadError);
        }
    }

    private static String firstAbi() {
        try {
            String[] abis = android.os.Build.SUPPORTED_ABIS;
            return abis != null && abis.length > 0 ? abis[0] : "unknown";
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    public void load(LocalAiConfig config) throws LocalAiException {
        ensureNativeLoaded();
        LocalAiModelInfo.fromPath(config.getModelPath());
        handle = nativeLoadModel(config.getModelPath(), config.getContextSize(), config.getThreads());
        if (handle == 0L) {
            throw new LocalAiException("llama.cpp couldn't load the selected model.");
        }
    }

    public String generate(String prompt, LocalAiConfig config) throws LocalAiException {
        return generate(prompt, config, 0.0f, "");
    }

    public String generate(String prompt, LocalAiConfig config, float presencePenalty, String grammar) throws LocalAiException {
        if (handle == 0L) {
            throw new LocalAiException("Model is not loaded.");
        }
        SamplingParams params = resolveSamplingParams(config, presencePenalty, grammar);
        String response = nativeGenerate(handle, prompt, config.getMaxTokens(),
                params.temperature, params.topP, params.presence, params.repeatPenalty, params.topK,
                grammar == null ? "" : grammar);
        return response == null ? "" : response.trim();
    }

    /**
     * Like {@link #generate(String, LocalAiConfig, float, String)} but reports
     * every generated piece through {@code onToken} as soon as it is produced,
     * instead of returning the whole response at the end. Returns the full text
     * once generation finishes (so callers can keep a single code path).
     */
    public String generateStream(String prompt, LocalAiConfig config, float presencePenalty, String grammar, TokenCallback onToken) throws LocalAiException {
        if (handle == 0L) {
            throw new LocalAiException("Model is not loaded.");
        }
        if (onToken == null) {
            return generate(prompt, config, presencePenalty, grammar);
        }
        SamplingParams params = resolveSamplingParams(config, presencePenalty, grammar);
        String response = nativeGenerateStream(handle, prompt, config.getMaxTokens(),
                params.temperature, params.topP, params.presence, params.repeatPenalty, params.topK,
                grammar == null ? "" : grammar, onToken);
        return response == null ? "" : response.trim();
    }

    private static final class SamplingParams {
        final float temperature;
        final float topP;
        final float presence;
        final float repeatPenalty;
        final int topK;

        SamplingParams(float temperature, float topP, float presence, float repeatPenalty, int topK) {
            this.temperature = temperature;
            this.topP = topP;
            this.presence = presence;
            this.repeatPenalty = repeatPenalty;
            this.topK = topK;
        }
    }

    private SamplingParams resolveSamplingParams(LocalAiConfig config, float presencePenalty, String grammar) {
        String architecture = getArchitecture(config.getModelPath());
        boolean isLiquid = architecture != null && architecture.toLowerCase(Locale.US).contains("liquid");
        boolean isQwen = architecture != null && architecture.toLowerCase(Locale.US).contains("qwen");

        int archTopK;
        float repeatPenalty;
        float temperature = config.getTemperature();
        float topP = config.getTopP();
        if (isLiquid) {
            // LFM2.5 recommends top_k=50 and repeat_penalty=1.1. For JSON output
            // (grammar active) clamp sampling so actions stay deterministic.
            archTopK = 50;
            repeatPenalty = 1.1f;
            if (grammar != null && !grammar.isEmpty()) {
                temperature = Math.min(temperature, 0.2f);
                topP = Math.min(topP, 0.95f);
            }
        } else if (isQwen) {
            // Qwen3.5 recommended settings (Unsloth): top_k=20.
            archTopK = 20;
            repeatPenalty = 1.0f;
        } else {
            archTopK = 40;
            repeatPenalty = 1.0f;
        }
        int topK = config.getTopK() > 0 ? config.getTopK() : archTopK;
        float presence = presencePenalty >= 0f ? presencePenalty : config.getPresencePenalty();
        // The native sampler only adds its penalties stage when a repeat penalty other
        // than 1.0 or a positive presence penalty is requested. With the default preset
        // (presence 0.0, repeat 1.0) that stage was missing entirely, so small local
        // models fell into degenerate repetition loops (e.g. repeating the same clause).
        // Enforce a mild anti-repetition floor so the penalties stage is always active.
        if (repeatPenalty <= 1.0f && presence <= 0f) {
            repeatPenalty = 1.1f;
        }
        return new SamplingParams(temperature, topP, presence, repeatPenalty, topK);
    }

    private static String getArchitecture(String modelPath) {
        if (modelPath == null || modelPath.isEmpty()) {
            return "";
        }
        synchronized (ARCHITECTURE_CACHE) {
            String cached = ARCHITECTURE_CACHE.get(modelPath);
            if (cached != null) {
                return cached;
            }
            String architecture = "";
            try {
                architecture = LocalAiModelInfo.fromPath(modelPath).getArchitecture();
            } catch (LocalAiException ignored) {
                android.util.Log.d("Ascode", "LocalAiBridge: LocalAiException ignored", ignored);
            }
            ARCHITECTURE_CACHE.put(modelPath, architecture);
            return architecture;
        }
    }

    public static void clearArchitectureCache() {
        synchronized (ARCHITECTURE_CACHE) {
            ARCHITECTURE_CACHE.clear();
        }
    }

    public void cancel() {
        nativeCancel(handle);
    }

    @Override
    public void close() {
        if (handle != 0L) {
            nativeRelease(handle);
            handle = 0L;
        }
    }

    private static native long nativeLoadModel(String modelPath, int contextSize, int threads);

    private static native String nativeGenerate(long handle, String prompt, int maxTokens, float temperature, float topP, float presencePenalty, float repeatPenalty, int topK, String grammar);

    private static native String nativeGenerateStream(long handle, String prompt, int maxTokens, float temperature, float topP, float presencePenalty, float repeatPenalty, int topK, String grammar, TokenCallback onToken);

    private static native void nativeCancel(long handle);

    private static native void nativeRelease(long handle);
}

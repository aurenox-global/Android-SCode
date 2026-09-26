package io.ascode.android;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalAiService {
    private static final LocalAiService INSTANCE = new LocalAiService();

    /**
     * GBNF grammar (official llama.cpp json.gbnf) that constrains local generation
     * to a valid JSON object: {"reply": "...", "actions": [{...}, ...]}
     */
    public static final String AGENT_JSON_GRAMMAR =
            "root   ::= object\n"
                    + "value  ::= object | array | string | number | (\"true\" | \"false\" | \"null\") ws\n"
                    + "object ::= \"{\" ws (string \":\" ws value (\",\" ws string \":\" ws value)*)? \"}\" ws\n"
                    + "array  ::= \"[\" ws (value (\",\" ws value)*)? \"]\" ws\n"
                    + "string ::= \"\\\"\" ([^\"\\\\\\x7F\\x00-\\x1F] | \"\\\\\" ([\"\\\\bfnrt] | \"u\" [0-9a-fA-F]{4}))* \"\\\"\" ws\n"
                    + "number ::= (\"-\"? ([0-9] | [1-9] [0-9]{0,15})) (\".\" [0-9]+)? ([eE] [-+]? [0-9] [1-9]{0,15})? ws\n"
                    + "ws ::= | \" \" | \"\\n\" [ \\t]{0,20}\n";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object activeBridgeLock = new Object();
    private LocalAiBridge activeBridge;
    private LocalAiBridge loadedBridge;
    private String loadedSignature = "";

    public static LocalAiService getInstance() {
        return INSTANCE;
    }

    public void generate(Context context, String prompt, Callback callback) {
        generate(context, prompt, true, callback);
    }

    public void generate(Context context, String prompt, boolean reasoningEnabled, Callback callback) {
        generateWithConfig(context, prompt, reasoningEnabled, null, callback);
    }

    public void generateWithConfig(Context context, String prompt, boolean reasoningEnabled, LocalAiConfig config, Callback callback) {
        Context appContext = context.getApplicationContext();
        post(callback::onStarted);
        executor.execute(() -> {
            LocalAiBridge bridge = null;
            String signature = null;
            boolean success = false;
            try {
                post(() -> callback.onStatus("Reading Local AI settings..."));
                LocalAiConfig cfg = config == null ? LocalAiConfig.load(appContext) : config;
                if (cfg.getModelPath().isEmpty()) {
                    throw new LocalAiException("Select a local .gguf model in Local AI Manager first.");
                }

                LocalAiModelInfo modelInfo = LocalAiModelInfo.fromPath(cfg.getModelPath());
                signature = createLoadSignature(cfg);
                bridge = acquireBridge(cfg, signature, callback);

                post(() -> callback.onStatus("Model loaded. Context: " + cfg.getContextSize()
                        + " | Threads: " + cfg.getThreads()
                        + " | Max tokens: " + cfg.getMaxTokens()
                        + "\nEvaluating prompt..."));
                String formattedPrompt = LocalAiPromptFormatter.format(prompt, cfg, reasoningEnabled);
                final boolean streamLog = DEBUG_STREAM_LOG || isStreamDebugFlagPresent();
                final int[] textTokens = {0};
                final StringBuilder textRaw = new StringBuilder();
                LocalAiBridge.TokenCallback textSink = token -> {
                    if (token == null || token.isEmpty()) {
                        return;
                    }
                    textTokens[0]++;
                    textRaw.append(token);
                    if (streamLog) {
                        android.util.Log.i(STREAM_LOG_TAG, "text token#" + textTokens[0]
                                + " rawChars=" + textRaw.length() + " piece=" + token.replace("\n", "\\n"));
                    }
                    callback.onToken(token);
                };
                String result = bridge.generateStream(formattedPrompt, cfg, cfg.getPresencePenalty(), "", textSink);
                if (streamLog) {
                    android.util.Log.i(STREAM_LOG_TAG, "text stream done: tokens=" + textTokens[0]
                            + " resultChars=" + (result == null ? -1 : result.length()));
                }
                if (result == null || result.isEmpty()) {
                    // Streaming produced nothing visible: fall back to the blocking path.
                    result = bridge.generate(formattedPrompt, cfg, cfg.getPresencePenalty(), "");
                }
                success = true;
                final String finalResult = result;
                post(() -> callback.onStatus("Generation complete."));
                post(() -> callback.onSuccess(finalResult));
            } catch (Throwable throwable) {
                post(() -> callback.onError(throwable));
            } finally {
                finishBridge(bridge, signature, success);
                post(callback::onFinished);
            }
        });
    }

    /**
     * Generates with JSON output constrained by a grammar (local models).
     * Reasoning is forced off so the chat template does not open a think block
     * that would violate the JSON grammar.
     */
    public void generateJson(Context context, String prompt, Callback callback) {
        generateJsonWithConfig(context, prompt, null, callback);
    }

    public void generateJsonWithConfig(Context context, String prompt, LocalAiConfig config, Callback callback) {
        Context appContext = context.getApplicationContext();
        post(callback::onStarted);
        executor.execute(() -> {
            LocalAiBridge bridge = null;
            String signature = null;
            boolean success = false;
            try {
                post(() -> callback.onStatus("Reading Local AI settings..."));
                LocalAiConfig cfg = config == null ? LocalAiConfig.load(appContext) : config;
                if (cfg.getModelPath().isEmpty()) {
                    throw new LocalAiException("Select a local .gguf model in Local AI Manager first.");
                }

                LocalAiModelInfo modelInfo = LocalAiModelInfo.fromPath(cfg.getModelPath());
                signature = createLoadSignature(cfg);
                bridge = acquireBridge(cfg, signature, callback);

                post(() -> callback.onStatus("Model loaded. Context: " + cfg.getContextSize()
                        + " | Threads: " + cfg.getThreads()
                        + " | Max tokens: " + cfg.getMaxTokens()
                        + "\nGenerating agent actions (JSON)..."));
                String formattedPrompt = LocalAiPromptFormatter.format(prompt, cfg, false);
                // Qwen3.5 non-thinking instruct recommends presence_penalty = 1.5.
                String result = bridge.generate(formattedPrompt, cfg, cfg.getPresencePenalty(), AGENT_JSON_GRAMMAR);
                if (result == null || result.trim().isEmpty()) {
                    // The grammar may have forced an early stop; retry without it.
                    post(() -> callback.onStatus("Retrying without JSON grammar..."));
                    result = bridge.generate(formattedPrompt, cfg);
                }
                success = true;
                final String finalResult = result;
                post(() -> callback.onStatus("Generation complete."));
                post(() -> callback.onSuccess(finalResult));
            } catch (Throwable throwable) {
                post(() -> callback.onError(throwable));
            } finally {
                finishBridge(bridge, signature, success);
                post(callback::onFinished);
            }
        });
    }

    /**
     * Like {@link #generateJsonWithConfig(Context, String, LocalAiConfig, Callback)} but
     * streams the answer while it is still being generated: the visible text of the
     * partial JSON (the growing value of the {@code "reply"} field) is reported through
     * {@link Callback#onToken(String)} so the caller can paint it progressively.
     *
     * <p>Grammar, sampling (presence penalty, temperature/topP and reasoning off) and
     * the fallback "retry without grammar" are exactly the same as the blocking JSON
     * path, and {@code onSuccess} still receives the complete JSON string, so the agent
     * parsing/execution contract is unchanged.</p>
     *
     * <p>{@code onToken} receives the <em>accumulated</em> visible reply so far (a full
     * snapshot, not a delta), so callers just replace the text instead of appending.</p>
     */
    public void generateJsonStreamWithConfig(Context context, String prompt, LocalAiConfig config, Callback callback) {
        Context appContext = context.getApplicationContext();
        post(callback::onStarted);
        executor.execute(() -> {
            LocalAiBridge bridge = null;
            String signature = null;
            boolean success = false;
            try {
                post(() -> callback.onStatus("Reading Local AI settings..."));
                LocalAiConfig cfg = config == null ? LocalAiConfig.load(appContext) : config;
                if (cfg.getModelPath().isEmpty()) {
                    throw new LocalAiException("Select a local .gguf model in Local AI Manager first.");
                }

                LocalAiModelInfo modelInfo = LocalAiModelInfo.fromPath(cfg.getModelPath());
                signature = createLoadSignature(cfg);
                bridge = acquireBridge(cfg, signature, callback);

                post(() -> callback.onStatus("Model loaded. Context: " + cfg.getContextSize()
                        + " | Threads: " + cfg.getThreads()
                        + " | Max tokens: " + cfg.getMaxTokens()
                        + "\nGenerating agent actions (JSON, streaming)..."));
                String formattedPrompt = LocalAiPromptFormatter.format(prompt, cfg, false);

                final boolean streamLog = DEBUG_STREAM_LOG || isStreamDebugFlagPresent();
                final long[] firstTokenAt = {0L};
                final long[] lastTokenAt = {0L};
                final int[] tokenCount = {0};
                final StringBuilder rawBuffer = new StringBuilder();
                final StringBuilder lastVisible = new StringBuilder();

                // Same grammar + same sampling as the blocking JSON call; tokens are still
                // produced by the native engine, we only add an incremental JSON scan.
                String result = bridge.generateStream(formattedPrompt, cfg, cfg.getPresencePenalty(), AGENT_JSON_GRAMMAR, token -> {
                    if (token == null || token.isEmpty()) {
                        return;
                    }
                    rawBuffer.append(token);
                    tokenCount[0]++;
                    long now = System.currentTimeMillis();
                    if (firstTokenAt[0] == 0L) {
                        firstTokenAt[0] = now;
                    }
                    lastTokenAt[0] = now;
                    if (streamLog) {
                        // Log.i (not Log.d/Log.v) so the trace survives the release build's
                        // default proguard-android-optimize.txt stripping of verbose logs.
                        android.util.Log.i(STREAM_LOG_TAG, "token#" + tokenCount[0]
                                + " t=+" + (now - firstTokenAt[0]) + "ms rawChars=" + rawBuffer.length());
                    }
                    publishVisibleText(callback, rawBuffer.toString(), lastVisible, false);
                });

                if (streamLog) {
                    long span = lastTokenAt[0] > firstTokenAt[0] ? lastTokenAt[0] - firstTokenAt[0] : 0L;
                    double tps = span > 0 ? (tokenCount[0] * 1000.0 / span) : 0.0;
                    android.util.Log.i(STREAM_LOG_TAG, "stream done: tokens=" + tokenCount[0]
                            + " span=" + span + "ms tok/s=" + String.format(java.util.Locale.US, "%.2f", tps)
                            + " rawChars=" + rawBuffer.length() + " visibleChars=" + lastVisible.length());
                    String preview = result == null ? "<null>" : (result.length() > 400 ? result.substring(0, 400) + "…" : result);
                    android.util.Log.i(STREAM_LOG_TAG, "grammar result (" + (result == null ? -1 : result.length())
                            + " chars): " + preview.replace('\n', ' '));
                }

                if (result == null || result.trim().isEmpty()) {
                    // The grammar may have forced an early stop; retry without it. This retry
                    // is streamed too (instead of the old blocking call) so the user keeps
                    // seeing the answer grow token by token instead of a frozen bubble.
                    post(() -> callback.onStatus("Retrying without JSON grammar..."));
                    rawBuffer.setLength(0);
                    lastVisible.setLength(0);
                    tokenCount[0] = 0;
                    firstTokenAt[0] = 0L;
                    lastTokenAt[0] = 0L;
                    result = bridge.generateStream(formattedPrompt, cfg, cfg.getPresencePenalty(), "", token -> {
                        if (token == null || token.isEmpty()) {
                            return;
                        }
                        rawBuffer.append(token);
                        tokenCount[0]++;
                        long now = System.currentTimeMillis();
                        if (firstTokenAt[0] == 0L) {
                            firstTokenAt[0] = now;
                        }
                        lastTokenAt[0] = now;
                        if (streamLog) {
                            android.util.Log.i(STREAM_LOG_TAG, "fb token#" + tokenCount[0]
                                    + " t=+" + (now - firstTokenAt[0]) + "ms rawChars=" + rawBuffer.length());
                        }
                        // No grammar here: the model may ramble before the JSON, so show the
                        // raw text until a "reply" value is available.
                        publishVisibleText(callback, rawBuffer.toString(), lastVisible, true);
                    });
                    if (streamLog) {
                        long span = lastTokenAt[0] > firstTokenAt[0] ? lastTokenAt[0] - firstTokenAt[0] : 0L;
                        double tps = span > 0 ? (tokenCount[0] * 1000.0 / span) : 0.0;
                        android.util.Log.i(STREAM_LOG_TAG, "fallback stream done: tokens=" + tokenCount[0]
                                + " span=" + span + "ms tok/s=" + String.format(java.util.Locale.US, "%.2f", tps)
                                + " rawChars=" + rawBuffer.length() + " visibleChars=" + lastVisible.length());
                        String preview = result == null ? "<null>" : (result.length() > 400 ? result.substring(0, 400) + "…" : result);
                        android.util.Log.i(STREAM_LOG_TAG, "fallback result (" + (result == null ? -1 : result.length())
                                + " chars): " + preview.replace('\n', ' '));
                    }
                }

                if (result == null || result.trim().isEmpty()) {
                    // Both streaming attempts produced nothing: last resort, the original
                    // blocking retry (kept verbatim so behaviour never regresses).
                    result = bridge.generate(formattedPrompt, cfg);
                    if (streamLog) {
                        android.util.Log.i(STREAM_LOG_TAG, "blocking fallback result (" + result.length() + " chars): "
                                + (result.length() > 300 ? result.substring(0, 300) + "…" : result).replace('\n', ' '));
                    }
                }
                success = true;
                final String finalResult = result;
                post(() -> callback.onStatus("Generation complete."));
                post(() -> callback.onSuccess(finalResult));
            } catch (Throwable throwable) {
                post(() -> callback.onError(throwable));
            } finally {
                finishBridge(bridge, signature, success);
                post(callback::onFinished);
            }
        });
    }

    private void publishVisibleText(Callback callback, String raw, StringBuilder lastVisible, boolean allowPlainFallback) {
        String visible = extractVisibleReply(raw);
        if (visible == null && allowPlainFallback) {
            // Unconstrained retry: the model may produce free-form prose before the JSON,
            // so fall back to the raw (trimmed) text to keep the answer visibly growing.
            visible = raw.trim();
        }
        if (visible == null || visible.isEmpty() || visible.equals(lastVisible.toString())) {
            return;
        }
        lastVisible.setLength(0);
        lastVisible.append(visible);
        final String snapshot = visible;
        post(() -> callback.onToken(snapshot));
    }

    /**
     * Temporary diagnostic switch for the JSON streaming path. Left off in committed
     * builds; the trace can also be enabled at runtime without rebuilding by creating
     * the marker file {@code /sdcard/.AndroidSCode/ai/stream_debug.flag}.
     */
    public static final boolean DEBUG_STREAM_LOG = false;
    private static final String STREAM_LOG_TAG = "AscodeAiStream";

    private static boolean isStreamDebugFlagPresent() {
        try {
            java.io.File flag = new java.io.File(
                    android.os.Environment.getExternalStorageDirectory(), ".AndroidSCode/ai/stream_debug.flag");
            return flag.exists();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Lightweight incremental scanner (no full JSON parser) that returns the decoded
     * value of the {@code "reply"} string field of a possibly incomplete agent JSON,
     * or {@code null} when that field has not started yet. Tolerates truncated input,
     * incomplete escapes and the field still growing token by token.
     */
    static String extractVisibleReply(String raw) {
        if (raw == null) {
            return null;
        }
        int key = raw.indexOf("\"reply\"");
        if (key < 0) {
            return null;
        }
        int i = key + 7;
        while (i < raw.length() && Character.isWhitespace(raw.charAt(i))) {
            i++;
        }
        if (i >= raw.length() || raw.charAt(i) != ':') {
            return null;
        }
        i++;
        while (i < raw.length() && Character.isWhitespace(raw.charAt(i))) {
            i++;
        }
        if (i >= raw.length() || raw.charAt(i) != '"') {
            return null;
        }
        i++;
        StringBuilder out = new StringBuilder();
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '\\') {
                if (i + 1 >= raw.length()) {
                    break; // incomplete escape: wait for more tokens
                }
                char esc = raw.charAt(i + 1);
                switch (esc) {
                    case '"': out.append('"'); i += 2; break;
                    case '\\': out.append('\\'); i += 2; break;
                    case '/': out.append('/'); i += 2; break;
                    case 'b': out.append('\b'); i += 2; break;
                    case 'f': out.append('\f'); i += 2; break;
                    case 'n': out.append('\n'); i += 2; break;
                    case 'r': out.append('\r'); i += 2; break;
                    case 't': out.append('\t'); i += 2; break;
                    case 'u':
                        if (i + 6 > raw.length()) {
                            i = raw.length(); // incomplete unicode escape
                            break;
                        }
                        try {
                            out.append((char) Integer.parseInt(raw.substring(i + 2, i + 6), 16));
                        } catch (NumberFormatException ignored) {
                            // truncated/invalid escape: skip it
                        }
                        i += 6;
                        break;
                    default:
                        out.append(esc);
                        i += 2;
                        break;
                }
                continue;
            }
            if (c == '"') {
                break; // closing quote reached: the reply is complete
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /**
     * Reuses the model kept in RAM when the requested model/config match the
     * loaded signature; otherwise loads a new bridge (which stays cached on
     * success so the next request skips the slow model load entirely).
     */
    private LocalAiBridge acquireBridge(LocalAiConfig config, String signature, Callback callback) throws LocalAiException {
        synchronized (activeBridgeLock) {
            if (loadedBridge != null && signature.equals(loadedSignature)) {
                LocalAiBridge loaded = loadedBridge;
                activeBridge = loaded;
                post(() -> callback.onStatus("Using model loaded in RAM: " + config.getModelName()));
                return loaded;
            }
        }
        LocalAiBridge bridge = new LocalAiBridge();
        LocalAiModelInfo modelInfo = LocalAiModelInfo.fromPath(config.getModelPath());
        post(() -> callback.onStatus("Loading model: " + config.getModelName() + "\n" + modelInfo.getDisplaySummary()));
        synchronized (activeBridgeLock) {
            activeBridge = bridge;
        }
        bridge.load(config);
        return bridge;
    }

    private void finishBridge(LocalAiBridge bridge, String signature, boolean success) {
        synchronized (activeBridgeLock) {
            if (activeBridge == bridge) {
                activeBridge = null;
            }
            if (success) {
                // Keep the model in RAM: next request reuses it (huge latency win).
                if (loadedBridge != null && loadedBridge != bridge) {
                    loadedBridge.close();
                }
                loadedBridge = bridge;
                loadedSignature = signature;
            } else if (bridge != null && loadedBridge != bridge) {
                bridge.close();
            }
        }
    }

    public void loadModel(Context context, Callback callback) {
        Context appContext = context.getApplicationContext();
        post(callback::onStarted);
        executor.execute(() -> {
            LocalAiBridge bridge = new LocalAiBridge();
            boolean keepBridgeLoaded = false;
            try {
                post(() -> callback.onStatus("Reading Local AI settings..."));
                LocalAiConfig config = LocalAiConfig.load(appContext);
                if (config.getModelPath().isEmpty()) {
                    throw new LocalAiException("Select or import a local .gguf model first.");
                }

                LocalAiModelInfo modelInfo = LocalAiModelInfo.fromPath(config.getModelPath());
                String signature = createLoadSignature(config);
                synchronized (activeBridgeLock) {
                    if (loadedBridge != null && signature.equals(loadedSignature)) {
                        post(() -> callback.onStatus("Model already loaded in RAM: " + config.getModelName()));
                        post(() -> callback.onSuccess("Model already loaded in RAM.\n" + modelInfo.getDisplaySummary()));
                        return;
                    }
                    activeBridge = bridge;
                }

                post(() -> callback.onStatus("Loading model into RAM: " + config.getModelName() + "\n" + modelInfo.getDisplaySummary()));
                bridge.load(config);
                synchronized (activeBridgeLock) {
                    if (loadedBridge != null) {
                        loadedBridge.close();
                    }
                    loadedBridge = bridge;
                    loadedSignature = signature;
                    keepBridgeLoaded = true;
                    activeBridge = null;
                }
                post(() -> callback.onStatus("Model loaded in RAM."));
                post(() -> callback.onSuccess("Model loaded in RAM.\n" + modelInfo.getDisplaySummary()));
            } catch (Throwable throwable) {
                post(() -> callback.onError(throwable));
            } finally {
                synchronized (activeBridgeLock) {
                    if (activeBridge == bridge) {
                        activeBridge = null;
                    }
                }
                if (!keepBridgeLoaded) {
                    bridge.close();
                }
                post(callback::onFinished);
            }
        });
    }

    public String getLoadedModelStatus(Context context) {
        LocalAiConfig config = LocalAiConfig.load(context.getApplicationContext());
        String signature = createLoadSignature(config);
        synchronized (activeBridgeLock) {
            if (loadedBridge != null && signature.equals(loadedSignature)) {
                return "Loaded in RAM: " + config.getModelName();
            }
            if (loadedBridge != null) {
                return "A different model/config is loaded in RAM. Tap Use to reload this one.";
            }
        }
        return "Not loaded in RAM. The first request will load it and keep it in RAM afterwards.";
    }

    public void cancel() {
        synchronized (activeBridgeLock) {
            if (activeBridge != null) {
                activeBridge.cancel();
            }
        }
    }

    private void post(Runnable runnable) {
        mainHandler.post(runnable);
    }

    private String createLoadSignature(LocalAiConfig config) {
        return config.getModelPath() + "|" + config.getContextSize() + "|" + config.getThreads();
    }

    public interface Callback {
        void onStarted();

        default void onStatus(String status) {
        }

        /**
         * Called repeatedly with each generated piece of text while the model
         * is still working. Default is a no-op, so existing callers keep the
         * previous "all text at once" behaviour.
         */
        default void onToken(String token) {
        }

        void onSuccess(String response);

        void onError(Throwable throwable);

        void onFinished();
    }
}

package a.a.a;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.github.javaparser.StaticJavaParser;

import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Verifies that a WebView activity carrying an <em>older</em> TTS helper is upgraded surgically to
 * the current helper, leaving the rest of the file untouched, and that the current helper carries
 * the language-aware engine selection + JS diagnostics introduced in helper version 3.
 *
 * <p>The sample in {@code src/test/resources/legacy/MainActivity.java.txt} was generated with the
 * {@code Jx} from commit {@code d9e5423} (v1.0.41), i.e. the legacy helper (pre-v2) found in
 * projects patched by the old migration. A v2-shaped helper is also exercised (the current output
 * with only its version marker rewritten to v2), which is exactly what the v1.0.42/43 releases
 * shipped.</p>
 */
public class WebViewTtsMigrationTest {

    private String readLegacySample() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("legacy/MainActivity.java.txt")) {
            assertNotNull("legacy/MainActivity.java.txt test resource must exist", in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Full, compilable-shape activity carrying the current helper (used by the javac check). */
    private String buildFullActivity() {
        StringBuilder sb = new StringBuilder();
        Jx.appendWebViewTtsHelpers(sb, "\n");
        return "package com.ascode.check;\n"
                + "import android.app.Activity;\n"
                + "import android.webkit.*;\n"
                + "import android.os.*;\n"
                + "import android.media.*;\n"
                + "import android.speech.tts.TextToSpeech;\n"
                + "import java.util.*;\n"
                + "public class MainActivity extends Activity {\n"
                + "private WebView webview1;\n"
                + sb
                + "}\n";
    }

    @Test
    public void legacySampleIsDetectedAsOutdated() throws Exception {
        String legacy = readLegacySample();
        assertTrue("sample must contain the legacy helper field",
                legacy.contains("private TextToSpeech _tts;"));
        assertFalse("legacy sample must not look current", Jx.hasCurrentWebViewTtsHelper(legacy));
        assertTrue("legacy sample must be flagged as outdated", Jx.hasLegacyWebViewTtsHelper(legacy));
    }

    @Test
    public void migrationUpgradesHelperAndPreservesEverythingElse() throws Exception {
        String legacy = readLegacySample();

        String migrated = Jx.replaceLegacyWebViewTtsHelper(legacy, "\n");
        assertNotNull("migration must locate the legacy helper block", migrated);

        // v6: the system engine path is back to the v1.0.40 behaviour (no audio-routing extras),
        // which is the one confirmed to sound on real devices.
        assertFalse("no setAudioAttributes in the system path", migrated.contains("setAudioAttributes"));
        assertFalse("no KEY_PARAM_STREAM", migrated.contains("KEY_PARAM_STREAM"));
        assertFalse("no KEY_PARAM_VOLUME", migrated.contains("KEY_PARAM_VOLUME"));
        assertTrue("speak uses a null params bundle like v1.0.40",
                migrated.contains("TextToSpeech.QUEUE_FLUSH, null, _utteranceId"));

        // Version markers are present.
        assertTrue(migrated.contains(Jx.WEBVIEW_TTS_HELPER_BEGIN_MARKER));
        assertTrue(migrated.contains(Jx.WEBVIEW_TTS_HELPER_END_MARKER));

        // The shim is installed only when the native speechSynthesis is missing (v1.0.40 guard).
        assertTrue(migrated.contains("_shimNeeded"));
        assertTrue(migrated.contains("(typeof window.speechSynthesis==='undefined')"));

        // After migration the file is detected as current and no longer as legacy.
        assertTrue(Jx.hasCurrentWebViewTtsHelper(migrated));
        assertFalse(Jx.hasLegacyWebViewTtsHelper(migrated));

        // The header before the helper is byte-identical.
        String header = legacy.substring(0, legacy.indexOf("private TextToSpeech _tts;"));
        assertTrue("header before the helper must be untouched", migrated.startsWith(header));

        // The user code that follows the helper survives, including a final class brace.
        assertTrue(migrated.contains("private void initializeLogic() {"));
        assertTrue(migrated.contains("// USER CODE THAT MUST SURVIVE THE MIGRATION"));
        assertTrue("file must still end with the original class closing brace",
                migrated.endsWith("// USER CODE THAT MUST SURVIVE THE MIGRATION\n    }\n}\n"));

        // Exactly one helper block: no duplicated _TtsBridge.
        assertEquals(1, countOccurrences(migrated, "private class _TtsBridge"));
        assertEquals(1, countOccurrences(migrated, "private TextToSpeech _tts;"));
    }

    @Test
    public void migratedSourceStillParsesAsJava() throws Exception {
        String legacy = readLegacySample();
        String migrated = Jx.replaceLegacyWebViewTtsHelper(legacy, "\n");
        assertNotNull(migrated);
        // Throws ParseProblemException when the result is not syntactically valid Java.
        StaticJavaParser.parse(migrated);
    }

    @Test
    public void currentHelperSelectsEngineByLanguageAndWarnsHonestly() {
        StringBuilder sb = new StringBuilder();
        Jx.appendWebViewTtsHelpers(sb, "\n");
        String current = sb.toString();

        // Language-aware engine selection.
        assertTrue(current.contains("getEngines"));
        assertTrue(current.contains("queryIntentServices"));
        assertTrue(current.contains("isLanguageAvailable"));
        assertTrue(current.contains("LANG_COUNTRY_VAR_AVAILABLE"));
        assertTrue(current.contains("com.google.android.tts"));
        assertTrue(current.contains("_ttsEngineRejected"));
        assertTrue(current.contains("_ttsStartFinalFallback"));
        assertTrue(current.contains("_acceptLanguageFallback"));

        // Honest, one-shot user warning when the language has no voice data.
        assertTrue(current.contains("_ttsWarnNoVoice"));
        assertTrue(current.contains("_ttsNoVoiceWarned"));
        assertTrue(current.contains("android.widget.Toast.makeText"));
        assertTrue(current.contains("Instalar datos de voz"));

        // JS diagnostics: pull API + pushed events, guarded so a page without the hook is safe.
        assertTrue(current.contains("getDiagnostics"));
        assertTrue(current.contains("window.__ascodeTtsDiag"));
        assertTrue(current.contains("__ascodeTtsDiag&&window.__ascodeTtsDiag"));
        assertTrue(current.contains("evaluateJavascript"));
        assertTrue(current.contains("_ttsEmitDiag(\"onStart\")"));
        assertTrue(current.contains("_ttsEmitDiag(\"onDone\")"));
        assertTrue(current.contains("_ttsEmitDiag(\"onError\")"));
    }

    @Test
    public void versionTwoHelperIsLegacyAndUpgradedToCurrent() {
        StringBuilder sb = new StringBuilder();
        Jx.appendWebViewTtsHelpers(sb, "\n");
        String v3 = sb.toString();
        // Simulate a project patched by v1.0.42/43: identical helper body, v2 version marker.
        String v2 = v3.replace(Jx.WEBVIEW_TTS_HELPER_BEGIN_MARKER, "// <ascode-tts v2>");
        assertFalse("a v2 helper must not look current", Jx.hasCurrentWebViewTtsHelper(v2));
        assertTrue("a v2 helper must be flagged as legacy", Jx.hasLegacyWebViewTtsHelper(v2));

        String file = "package x;\npublic class MainActivity {\n" + v2 + "}\n";
        String migrated = Jx.replaceLegacyWebViewTtsHelper(file, "\n");
        assertNotNull(migrated);
        assertTrue(migrated.contains(Jx.WEBVIEW_TTS_HELPER_BEGIN_MARKER));
        assertTrue(Jx.hasCurrentWebViewTtsHelper(migrated));
        assertFalse(Jx.hasLegacyWebViewTtsHelper(migrated));
        assertEquals(1, countOccurrences(migrated, "private class _TtsBridge"));
    }

    @Test
    public void brandNewHelperIsCurrentAndNotTouched() {
        StringBuilder sb = new StringBuilder();
        Jx.appendWebViewTtsHelpers(sb, "\n");
        String current = sb.toString();

        assertTrue(Jx.hasCurrentWebViewTtsHelper(current));
        assertFalse(Jx.hasLegacyWebViewTtsHelper(current));
        // A current helper starts with the version marker (after the leading EOL).
        assertTrue(current.trim().startsWith(Jx.WEBVIEW_TTS_HELPER_BEGIN_MARKER));
        assertEquals(7, Jx.WEBVIEW_TTS_HELPER_VERSION);
    }

    @Test
    public void currentHelperCarriesAnOffByDefaultDiagnosticsFlag() {
        StringBuilder sb = new StringBuilder();
        Jx.appendWebViewTtsHelpers(sb, "\n");
        String current = sb.toString();

        // The flag exists, defaults to OFF, and records its own value for the migration check.
        assertTrue(current.contains("private boolean _ttsShowDiagToasts = false;"));
        assertTrue(current.contains(Jx.WEBVIEW_TTS_DIAG_MARKER_PREFIX + "false"));
        assertTrue(Jx.hasWebViewTtsDiagnosticsSetting(current, false));
        assertFalse(Jx.hasWebViewTtsDiagnosticsSetting(current, true));

        // The toast path reuses the diagnostics summary and is throttled on the main thread.
        assertTrue(current.contains("_ttsMaybeDiagToast"));
        assertTrue(current.contains("_ttsDiagnostics()"));
        assertTrue(current.contains("android.widget.Toast.LENGTH_SHORT"));
        assertTrue(current.contains("android.os.SystemClock.elapsedRealtime()"));
        assertTrue(current.contains("800L"));
        assertTrue(current.contains("Looper.getMainLooper()"));

        // The persistent, logcat-visible summary line (tag AscodeTTS).
        assertTrue(current.contains("DIAG event="));
        assertTrue(current.contains("\"AscodeTTS\""));

        // The honest "no voice installed" toast must still be emitted alongside the diagnostics.
        assertTrue(current.contains("_ttsWarnNoVoice"));
        assertTrue(current.contains("Instalar datos de voz"));
    }

    @Test
    public void diagnosticsFlagCanBeBakedOn() {
        StringBuilder sb = new StringBuilder();
        Jx.appendWebViewTtsHelpers(sb, "\n", "1.25", "pt-BR", true);
        String current = sb.toString();

        assertTrue(current.contains("private boolean _ttsShowDiagToasts = true;"));
        assertTrue(current.contains(Jx.WEBVIEW_TTS_DIAG_MARKER_PREFIX + "true"));
        assertTrue(Jx.hasWebViewTtsDiagnosticsSetting(current, true));
        assertFalse(Jx.hasWebViewTtsDiagnosticsSetting(current, false));
        assertTrue(current.contains("1.25f"));
        assertTrue(current.contains("new Locale(\"pt\", \"BR\")"));
        assertTrue(Jx.hasCurrentWebViewTtsHelper(current));
    }

    @Test
    public void overlayIsEmittedOnlyWhenDiagnosticsSwitchIsOn() {
        StringBuilder off = new StringBuilder();
        Jx.appendWebViewTtsHelpers(off, "\n", "0.95", "es-ES", false);
        String offCode = off.toString();
        // With the switch off the overlay is not emitted at all: behaviour matches the old helper.
        assertFalse(offCode.contains("_injectTtsDiagnosticsOverlay"));
        assertFalse(offCode.contains("__ascodeTtsOverlay"));
        assertFalse(offCode.contains("__ascodeTtsShimInstalled"));
        assertFalse(offCode.contains("Probar voz"));

        StringBuilder on = new StringBuilder();
        Jx.appendWebViewTtsHelpers(on, "\n", "0.95", "es-ES", true);
        String onCode = on.toString();
        // The overlay method, the JS UI and the shim-installed marker are all present.
        assertTrue(onCode.contains("_injectTtsDiagnosticsOverlay(WebView _webView)"));
        assertTrue(onCode.contains("__ascodeTtsOverlay"));
        assertTrue(onCode.contains("window.__ascodeTtsShimInstalled=true;"));
        assertTrue(onCode.contains("Probar voz"));
        assertTrue(onCode.contains("evaluateJavascript(_overlayJs, null)"));
        // It reports the bridge identity, the shim state and hooks the page error channels.
        assertTrue(onCode.contains("typeof _b.speak"));
        assertTrue(onCode.contains("window.onerror"));
        assertTrue(onCode.contains("console.error"));
        assertTrue(onCode.contains("getDiagnostics"));
        assertTrue(onCode.contains("_ttsEmitDiag(\"overlay\")"));
        // Its button drives the engine directly through the bridge probe.
        assertTrue(onCode.contains("window.AndroidBridge.probe()"));
        assertTrue(onCode.contains("public void probe()"));
        assertTrue(onCode.contains("Hola, prueba de voz"));
    }

    @Test
    public void bridgeExposesSpeakAndStopCountersThroughDiagnostics() {
        StringBuilder sb = new StringBuilder();
        Jx.appendWebViewTtsHelpers(sb, "\n", "0.95", "es-ES", true);
        String current = sb.toString();

        // Counters live on the Java side and are surfaced through getDiagnostics().
        assertTrue(current.contains("private int _ttsBridgeSpeechCalls = 0;"));
        assertTrue(current.contains("private int _ttsBridgeStopCalls = 0;"));
        assertTrue(current.contains("_ttsBridgeSpeechCalls++"));
        assertTrue(current.contains("_ttsBridgeStopCalls++"));
        assertTrue(current.contains("speakCalls="));
        assertTrue(current.contains("stopCalls="));
        assertTrue(current.contains("lastRate="));
        assertTrue(current.contains("lastText="));
        assertTrue(current.contains("return _ttsDiagnostics();"));
    }

    @Test
    public void diagnosticsOffHelperStaysBehaviourallyIdentical() {
        // The off path must keep the JavaScript API and every event the old helper exposed.
        StringBuilder off = new StringBuilder();
        Jx.appendWebViewTtsHelpers(off, "\n", "0.95", "es-ES", false);
        String code = off.toString();
        assertTrue(code.contains("private class _TtsBridge"));
        assertTrue(code.contains("window.AndroidBridge"));
        assertTrue(code.contains("public void speak(final String _text, final String _rate)"));
        assertTrue(code.contains("window.speechSynthesis"));
        assertTrue(code.contains("getDiagnostics"));
        assertFalse(code.contains("_injectTtsDiagnosticsOverlay"));
    }

    @Test
    public void replacementPreservesDiagnosticsSettingAndRate() {
        // A v3-shaped helper (current body with the version marker rewritten) must be upgraded to
        // the v4 helper while keeping the requested rate/language/diagnostics values.
        StringBuilder sb = new StringBuilder();
        Jx.appendWebViewTtsHelpers(sb, "\n", "1.1", "en-US", false);
        String v3 = sb.toString().replace(Jx.WEBVIEW_TTS_HELPER_BEGIN_MARKER, "// <ascode-tts v3>");
        String file = "package x;\npublic class MainActivity {\n" + v3 + "}\n";

        String migrated = Jx.replaceLegacyWebViewTtsHelper(file, "\n", "1.1", "en-US", true);
        assertNotNull(migrated);
        assertTrue(Jx.hasCurrentWebViewTtsHelper(migrated));
        assertTrue(Jx.hasWebViewTtsDiagnosticsSetting(migrated, true));
        assertTrue(migrated.contains("private boolean _ttsShowDiagToasts = true;"));
        assertTrue(migrated.contains("1.1f"));
        assertTrue(migrated.contains("new Locale(\"en\", \"US\")"));
    }

    @Test
    public void ttsDiagnosticsSettingDefaultsOff() {
        // The pure defaults (no project / never opened the screen) must reproduce the previous
        // behaviour: diagnostics toasts off.
        assertFalse(com.ascode.android.webview.ProjectWebViewSettings.defaults()
                .isTtsDiagnosticsEnabled());
    }

    @Test
    public void projectWithoutHelperIsNotFlaggedAsLegacy() {
        String plain = "public class MainActivity extends AppCompatActivity {\n"
                + "    private WebView webview1;\n"
                + "}\n";
        assertFalse(Jx.hasCurrentWebViewTtsHelper(plain));
        assertFalse(Jx.hasLegacyWebViewTtsHelper(plain));
    }

    /**
     * Writes a full activity carrying the current helper so the build/verification step can run
     * {@code javac} against {@code android.jar} and {@code javap} the resulting bytecode.
     */
    @Test
    public void emitsGeneratedActivityForBytecodeCheck() throws Exception {
        String activity = buildFullActivity();
        StaticJavaParser.parse(activity);
        File out = new File("build/tts-generated-check/com/ascode/check/MainActivity.java");
        assertTrue(out.getParentFile().mkdirs() || out.getParentFile().isDirectory());
        Files.write(out.toPath(), activity.getBytes(StandardCharsets.UTF_8));
        assertTrue(out.isFile());

        // Also emit the diagnostics-enabled variant so the javac check covers the new Toast path.
        StringBuilder diag = new StringBuilder();
        Jx.appendWebViewTtsHelpers(diag, "\n", "0.95", "es-ES", true);
        String diagActivity = "package com.ascode.check;\n"
                + "import android.app.Activity;\n"
                + "import android.webkit.*;\n"
                + "import android.os.*;\n"
                + "import android.media.*;\n"
                + "import android.speech.tts.TextToSpeech;\n"
                + "import java.util.*;\n"
                + "public class DiagActivity extends Activity {\n"
                + "private WebView webview1;\n"
                + diag
                + "}\n";
        StaticJavaParser.parse(diagActivity);
        File diagOut = new File("build/tts-generated-check-diag/com/ascode/check/DiagActivity.java");
        assertTrue(diagOut.getParentFile().mkdirs() || diagOut.getParentFile().isDirectory());
        Files.write(diagOut.toPath(), diagActivity.getBytes(StandardCharsets.UTF_8));
        assertTrue(diagOut.isFile());
    }

    // ---------------------------------------------------------------------------------------
    // Duplicated WebView bootstrap: idempotent de-duplication + redundant shim hook on
    // setWebViewClient.onPageFinished.
    // ---------------------------------------------------------------------------------------

    private static final String CHROME_WITH_SHIM =
            "webview1.setWebChromeClient(new WebChromeClient() {\n"
                    + "@Override\n"
                    + "public boolean onShowFileChooser(WebView _webView, ValueCallback<Uri[]> _filePathCallback, FileChooserParams _fileChooserParams) {\n"
                    + "return _openWebFileChooser(_filePathCallback, _fileChooserParams);\n"
                    + "}\n"
                    + "@Override\n"
                    + "public void onProgressChanged(WebView _webView, int _newProgress) {\n"
                    + "if (_newProgress >= 100) {\n"
                    + "_injectTtsShim(_webView);\n"
                    + "}\n"
                    + "}\n"
                    + "});\n";

    private static final String CHROME_EMPTY =
            "webview1.setWebChromeClient(new WebChromeClient() {\n"
                    + "@Override\n"
                    + "public boolean onShowFileChooser(WebView _webView, ValueCallback<Uri[]> _filePathCallback, FileChooserParams _fileChooserParams) {\n"
                    + "return _openWebFileChooser(_filePathCallback, _fileChooserParams);\n"
                    + "}\n"
                    + "@Override\n"
                    + "public void onProgressChanged(WebView _webView, int _newProgress) {\n"
                    + "}\n"
                    + "});\n";

    private static final String SAFE_DOWNLOAD =
            "webview1.setDownloadListener(new DownloadListener() {\n"
                    + "@Override\n"
                    + "public void onDownloadStart(String _url, String _userAgent, String _contentDisposition, String _mimeType, long _contentLength) {\n"
                    + "try {\n"
                    + "_downloadWebFile(webview1, _url, _userAgent, _contentDisposition, _mimeType);\n"
                    + "} catch (Throwable _e) {\n"
                    + "AscodeUtil.showMessage(getApplicationContext(), \"Download failed\");\n"
                    + "}\n"
                    + "}\n"
                    + "});\n";

    private static final String BLOB_IFACE =
            "webview1.addJavascriptInterface(new _BlobDownloadBridge(), \"_BlobDownloader\");\n";

    private static final String BRIDGE_IFACE =
            "webview1.addJavascriptInterface(new _TtsBridge(), \"AndroidBridge\");\n";

    private static final String PAGE_FINISHED =
            "webview1.setWebViewClient(new WebViewClient() {\n"
                    + "@Override\n"
                    + "public void onPageFinished(WebView _webView, String _url) {\n"
                    + "super.onPageFinished(_webView, _url);\n"
                    + "}\n"
                    + "});\n";

    /**
     * Mirrors the decompiled shape of a user project: the WebView bootstrap block was appended
     * three times by the old, non-idempotent migration, and the LAST WebChromeClient has an empty
     * onProgressChanged that used to shadow the one installing the TTS shim.
     */
    // Fully-qualified / decompiled variants (android.webkit.* names, `public final void` overrides,
    // `// from class:` comments) — the shape that defeated the unqualified-only patterns.
    private static final String REAL_CHROME_WITH_SHIM =
            "this.binding.webview1.setWebChromeClient(new android.webkit.WebChromeClient() { // from class: com.escuelita.MainActivity.1\n"
                    + "@Override // android.webkit.WebChromeClient\n"
                    + "public final void onProgressChanged(android.webkit.WebView webView, int i) {\n"
                    + "if (i >= 100) {\n"
                    + "com.escuelita.MainActivity.this._injectTtsShim(webView);\n"
                    + "}\n"
                    + "}\n"
                    + "@Override // android.webkit.WebChromeClient\n"
                    + "public final boolean onShowFileChooser(android.webkit.WebView webView, android.webkit.ValueCallback valueCallback, android.webkit.WebChromeClient.FileChooserParams fileChooserParams) {\n"
                    + "return com.escuelita.MainActivity.this._openWebFileChooser(valueCallback, fileChooserParams);\n"
                    + "}\n"
                    + "});\n";

    private static final String REAL_CHROME_EMPTY =
            "this.binding.webview1.setWebChromeClient(new android.webkit.WebChromeClient() { // from class: com.escuelita.MainActivity.3\n"
                    + "@Override // android.webkit.WebChromeClient\n"
                    + "public final void onProgressChanged(android.webkit.WebView webView, int i) {\n"
                    + "}\n"
                    + "@Override // android.webkit.WebChromeClient\n"
                    + "public final boolean onShowFileChooser(android.webkit.WebView webView, android.webkit.ValueCallback valueCallback, android.webkit.WebChromeClient.FileChooserParams fileChooserParams) {\n"
                    + "return com.escuelita.MainActivity.this._openWebFileChooser(valueCallback, fileChooserParams);\n"
                    + "}\n"
                    + "});\n";

    private static final String REAL_SAFE_DOWNLOAD =
            "this.binding.webview1.setDownloadListener(new android.webkit.DownloadListener() { // from class: com.escuelita.MainActivity.2\n"
                    + "@Override // android.webkit.DownloadListener\n"
                    + "public final void onDownloadStart(java.lang.String str, java.lang.String str2, java.lang.String str3, java.lang.String str4, long j) {\n"
                    + "try {\n"
                    + "com.escuelita.MainActivity mainActivity = com.escuelita.MainActivity.this;\n"
                    + "mainActivity._downloadWebFile(mainActivity.binding.webview1, str, str2, str3, str4);\n"
                    + "} catch (java.lang.Throwable unused) {\n"
                    + "com.escuelita.AscodeUtil.a(com.escuelita.MainActivity.this.getApplicationContext(), \"Download failed\");\n"
                    + "}\n"
                    + "}\n"
                    + "});\n";

    private static final String REAL_BLOB_IFACE =
            "this.binding.webview1.addJavascriptInterface(new com.escuelita.MainActivity._BlobDownloadBridge(), \"_BlobDownloader\");\n";

    private static final String REAL_BRIDGE_IFACE =
            "this.binding.webview1.addJavascriptInterface(new com.escuelita.MainActivity._TtsBridge(), \"AndroidBridge\");\n";

    private static final String REAL_EMPTY_WEBVIEW_CLIENT =
            "this.binding.webview1.setWebViewClient(new android.webkit.WebViewClient() { // from class: com.escuelita.MainActivity.5\n"
                    + "});\n";

    private String duplicatedBootstrapSource() {
        return "package com.escuelita;\n"
                + "import android.app.Activity;\n"
                + "import android.webkit.*;\n"
                + "import android.media.*;\n"
                + "import android.os.*;\n"
                + "import java.util.*;\n"
                + "import android.speech.tts.TextToSpeech;\n"
                + "public class MainActivity extends Activity {\n"
                + "private WebView webview1;\n"
                + "private void initialize() {\n"
                + CHROME_WITH_SHIM
                + BLOB_IFACE
                + SAFE_DOWNLOAD
                + BLOB_IFACE
                + CHROME_EMPTY
                + SAFE_DOWNLOAD
                + BLOB_IFACE
                + CHROME_EMPTY
                + SAFE_DOWNLOAD
                + BRIDGE_IFACE
                + PAGE_FINISHED
                + "}\n"
                + "private boolean _openWebFileChooser(ValueCallback<Uri[]> _cb, WebChromeClient.FileChooserParams _p) { return false; }\n"
                + "}\n";
    }

    @Test
    public void duplicatedBootstrapIsDetected() {
        String duplicated = duplicatedBootstrapSource();
        assertTrue("three chrome clients must be flagged as duplicates",
                Jx.hasDuplicateWebViewBootstrapCalls(duplicated));
        // A clean single bootstrap is not flagged.
        String clean = "webview1.setWebChromeClient(new WebChromeClient() {\n});\n"
                + BLOB_IFACE + BRIDGE_IFACE + SAFE_DOWNLOAD;
        assertFalse(Jx.hasDuplicateWebViewBootstrapCalls(clean));
    }

    @Test
    public void duplicatedBootstrapIsCollapsedAndShimSurvives() {
        String duplicated = duplicatedBootstrapSource();
        assertEquals(3, countOccurrences(duplicated, "setWebChromeClient(new WebChromeClient()"));
        assertEquals(3, countOccurrences(duplicated, "setDownloadListener(new DownloadListener()"));
        assertEquals(3, countOccurrences(duplicated, "\"_BlobDownloader\""));

        String migrated = Jx.deduplicateWebViewBootstrap(duplicated, "\n");

        assertEquals("exactly one WebChromeClient", 1,
                countOccurrences(migrated, "setWebChromeClient(new WebChromeClient()"));
        assertEquals("exactly one DownloadListener", 1,
                countOccurrences(migrated, "setDownloadListener(new DownloadListener()"));
        assertEquals("one _BlobDownloader registration", 1,
                countOccurrences(migrated, "\"_BlobDownloader\""));
        assertEquals("one AndroidBridge registration", 1,
                countOccurrences(migrated, "\"AndroidBridge\""));

        // The shim survives (the duplicate with the empty onProgressChanged is gone).
        assertEquals(1, countOccurrences(migrated, "_injectTtsShim(_webView)"));
        assertTrue("file chooser support survives", migrated.contains("onShowFileChooser("));
        // The user WebViewClient is preserved.
        assertEquals(1, countOccurrences(migrated, "setWebViewClient(new WebViewClient()"));
        assertTrue(migrated.contains("private boolean _openWebFileChooser("));
        // No duplicate methods left behind.
        assertEquals(1, countOccurrences(migrated, "onProgressChanged("));
    }

    @Test
    public void deduplicationIsIdempotent() {
        String duplicated = duplicatedBootstrapSource();
        String once = Jx.deduplicateWebViewBootstrap(duplicated, "\n");
        String twice = Jx.deduplicateWebViewBootstrap(once, "\n");
        assertEquals("applying the de-duplication twice must be byte-identical", once, twice);
        assertFalse(Jx.hasDuplicateWebViewBootstrapCalls(once));
    }

    @Test
    public void shimIsAlsoHookedIntoExistingWebViewClientOnPageFinished() {
        String migrated = Jx.injectTtsShimIntoWebViewClients(
                Jx.deduplicateWebViewBootstrap(duplicatedBootstrapSource(), "\n"), "\n", false);
        assertTrue(migrated.contains("public void onPageFinished(WebView _webView, String _url)"));
        assertTrue("onPageFinished must call the shim",
                migrated.contains("public void onPageFinished(WebView _webView, String _url) {\n_injectTtsShim(_webView);\nsuper.onPageFinished(_webView, _url);"));

        // Idempotent: a second pass changes nothing.
        String again = Jx.injectTtsShimIntoWebViewClients(migrated, "\n", false);
        assertEquals(migrated, again);
        // One shim call in the chrome client and one in onPageFinished.
        assertEquals(2, countOccurrences(again, "_injectTtsShim(_webView)"));
    }

    @Test
    public void emptyProgressOverrideGetsTheShimWithoutAddingASecondMethod() {
        // A chrome client with an EMPTY onProgressChanged (the shape that shadowed the shim).
        String source = "package x;\npublic class MainActivity {\n"
                + "private WebView webview1;\n"
                + "private void initialize() {\n"
                + CHROME_EMPTY
                + "}\n}\n";
        String patched = Jx.injectTtsShimIntoWebChromeClients(source, "\n", false);
        assertEquals("one onProgressChanged only", 1, countOccurrences(patched, "onProgressChanged("));
        assertEquals(1, countOccurrences(patched, "_injectTtsShim(_webView)"));
        // Idempotent.
        assertEquals(patched, Jx.injectTtsShimIntoWebChromeClients(patched, "\n", false));
    }

    // ---------------------------------------------------------------------------------------
    // Real-world shape: a decompiled / fully-qualified source (android.webkit.* type names,
    // `public final void` overrides, `// from class:` comments). This is the exact shape that
    // slipped through the old de-duplication: the patterns only matched unqualified type names,
    // so findAnonymousInvocations() returned nothing and the duplicate WebChromeClient survived.
    // ---------------------------------------------------------------------------------------

    private String realDecompiledDuplicatedBootstrapSource() {
        return "package com.escuelita;\n"
                + "import android.app.Activity;\n"
                + "public class MainActivity extends Activity {\n"
                + "private WebView webview1;\n"
                + "private static final class _BlobDownloadBridge {}\n"
                + "private static final class _TtsBridge {}\n"
                + "private void initialize(android.os.Bundle bundle) {\n"
                + REAL_CHROME_WITH_SHIM
                + REAL_BLOB_IFACE
                + REAL_BRIDGE_IFACE
                + REAL_SAFE_DOWNLOAD
                + REAL_BLOB_IFACE
                + REAL_CHROME_EMPTY
                + REAL_SAFE_DOWNLOAD
                + REAL_BLOB_IFACE
                + REAL_EMPTY_WEBVIEW_CLIENT
                + "}\n"
                + "private void _injectTtsShim(android.webkit.WebView _w) {}\n"
                + "private boolean _openWebFileChooser(android.webkit.ValueCallback<android.net.Uri[]> _cb, android.webkit.WebChromeClient.FileChooserParams _p) { return false; }\n"
                + "private void _downloadWebFile(android.webkit.WebView _w, java.lang.String _a, java.lang.String _b, java.lang.String _c, java.lang.String _d) {}\n"
                + "}\n";
    }

    @Test
    public void realDecompiledDuplicatedBootstrapIsDetectedAndCollapsed() {
        String duplicated = realDecompiledDuplicatedBootstrapSource();
        assertTrue("FQN / decompiled duplicates must be detected",
                Jx.hasDuplicateWebViewBootstrapCalls(duplicated));
        assertEquals(2, countOccurrences(duplicated, "setWebChromeClient("));
        assertEquals(2, countOccurrences(duplicated, "setDownloadListener("));
        assertEquals(3, countOccurrences(duplicated, "\"_BlobDownloader\""));

        String migrated = Jx.deduplicateWebViewBootstrap(duplicated, "\n");

        assertEquals("exactly one WebChromeClient", 1, countOccurrences(migrated, "setWebChromeClient("));
        assertEquals("exactly one DownloadListener", 1, countOccurrences(migrated, "setDownloadListener("));
        assertEquals("one _BlobDownloader registration", 1, countOccurrences(migrated, "\"_BlobDownloader\""));
        assertEquals("one AndroidBridge registration", 1, countOccurrences(migrated, "\"AndroidBridge\""));
        // The surviving chrome client is the one that installs the shim (not the empty one).
        assertTrue("the shim-carrying WebChromeClient must win",
                migrated.contains("_injectTtsShim(webView)"));
        assertEquals("only one shim call after de-duplication", 1,
                countOccurrences(migrated, "_injectTtsShim(webView)"));
        assertEquals("the empty duplicate must be gone", 1, countOccurrences(migrated, "onProgressChanged("));
        assertTrue("file chooser support survives", migrated.contains("onShowFileChooser("));
        assertEquals("the user WebViewClient is preserved", 1, countOccurrences(migrated, "setWebViewClient("));
        assertFalse("no duplicates left", Jx.hasDuplicateWebViewBootstrapCalls(migrated));
    }

    @Test
    public void realDecompiledMigrationIsIdempotent() {
        String duplicated = realDecompiledDuplicatedBootstrapSource();
        String once = Jx.migrateWebViewBootstrapTts(Jx.deduplicateWebViewBootstrap(duplicated, "\n"), "\n", false);
        String twice = Jx.migrateWebViewBootstrapTts(Jx.deduplicateWebViewBootstrap(once, "\n"), "\n", false);
        assertEquals("migrating twice must be byte-identical", once, twice);
    }

    @Test
    public void realDecompiledMigrationAddsShimToSurvivingEmptyProgressAndOnPageFinished() {
        // FQN chrome client with an EMPTY onProgressChanged + FQN (empty) WebViewClient: the
        // bullet-proof guarantee must add the shim call to the progress override (using its own
        // parameter name) and hook it into onPageFinished of the surviving WebViewClient.
        String source = "package com.escuelita;\n"
                + "import android.app.Activity;\n"
                + "public class MainActivity extends Activity {\n"
                + "private WebView webview1;\n"
                + "private void initialize(android.os.Bundle bundle) {\n"
                + REAL_CHROME_EMPTY
                + REAL_EMPTY_WEBVIEW_CLIENT
                + "}\n"
                + "private void _injectTtsShim(android.webkit.WebView _w) {}\n"
                + "private boolean _openWebFileChooser(android.webkit.ValueCallback<android.net.Uri[]> _cb, android.webkit.WebChromeClient.FileChooserParams _p) { return false; }\n"
                + "}\n";

        String migrated = Jx.migrateWebViewBootstrapTts(
                Jx.deduplicateWebViewBootstrap(source, "\n"), "\n", false);

        assertEquals("one onProgressChanged only", 1, countOccurrences(migrated, "onProgressChanged("));
        assertTrue("the empty progress override must now call the shim with its own parameter name",
                migrated.contains("_injectTtsShim(webView)"));
        assertTrue("the WebViewClient must hook the shim from onPageFinished",
                migrated.contains("public void onPageFinished(WebView _webView, String _url)")
                        && migrated.contains("_injectTtsShim(_webView)"));
        // Idempotent.
        assertEquals(migrated, Jx.migrateWebViewBootstrapTts(migrated, "\n", false));
    }

    @Test
    public void realDecompiledMigratedSourceParsesAsJava() {
        String migrated = Jx.migrateWebViewBootstrapTts(
                Jx.deduplicateWebViewBootstrap(realDecompiledDuplicatedBootstrapSource(), "\n"), "\n", false);
        StaticJavaParser.parse(migrated);
    }

    @Test
    public void migratedDuplicatedSourceStillParsesAsJava() {
        String migrated = Jx.injectTtsShimIntoWebViewClients(
                Jx.deduplicateWebViewBootstrap(duplicatedBootstrapSource(), "\n"), "\n", false);
        StaticJavaParser.parse(migrated);
    }

    // ---------------------------------------------------------------------------------------
    // Migration trigger: the exact shape decompiled from the user's real APK (two qualified
    // setWebChromeClient, three addJavascriptInterface("_BlobDownloader"), two setDownloadListener,
    // one setWebViewClient). needsWebViewSourceMigration() must return true so the build collapses
    // the duplicates instead of leaving the empty-onProgressChanged chrome client shadowing the shim.
    // ---------------------------------------------------------------------------------------

    @Test
    public void duplicateBootstrapForcesWebViewSourceMigrationTrigger() {
        String duplicated = realDecompiledDuplicatedBootstrapSource();
        assertTrue("the qualified duplicates must be detected",
                Jx.hasDuplicateWebViewBootstrapCalls(duplicated));
        assertTrue("with the TTS bridge enabled the duplicated source must force a migration",
                yq.needsWebViewSourceMigration(duplicated, true, false));
        assertTrue("duplicates force a migration regardless of the TTS switch",
                yq.needsWebViewSourceMigration(duplicated, false, false));
        assertTrue("with the diagnostics switch on the trigger still fires",
                yq.needsWebViewSourceMigration(duplicated, true, true));

        // A clean, single bootstrap does not trip the duplicate trigger.
        String clean = "webview1.setWebChromeClient(new WebChromeClient() {\n});\n"
                + BLOB_IFACE + BRIDGE_IFACE + SAFE_DOWNLOAD;
        assertFalse(Jx.hasDuplicateWebViewBootstrapCalls(clean));
    }

    @Test
    public void realDecompiledFullMigrationCollapsesAndHooksBothSurvivingClients() {
        String duplicated = realDecompiledDuplicatedBootstrapSource();
        String migrated = Jx.migrateWebViewBootstrapTts(duplicated, "\n", false);

        assertEquals("exactly one setWebChromeClient", 1,
                countOccurrences(migrated, "setWebChromeClient("));
        assertEquals("exactly one setDownloadListener", 1,
                countOccurrences(migrated, "setDownloadListener("));
        assertEquals("one _BlobDownloader registration", 1,
                countOccurrences(migrated, "\"_BlobDownloader\""));
        assertEquals("one AndroidBridge registration", 1,
                countOccurrences(migrated, "\"AndroidBridge\""));
        assertEquals("exactly one onProgressChanged", 1,
                countOccurrences(migrated, "onProgressChanged("));

        // The surviving chrome client is the one that installs the shim (not the empty duplicate).
        assertTrue("the shim-carrying WebChromeClient must win",
                migrated.contains("_injectTtsShim(webView)"));
        // The surviving WebViewClient hooks the shim from onPageFinished.
        assertTrue("onPageFinished must exist on the surviving WebViewClient",
                migrated.contains("public void onPageFinished(WebView _webView, String _url)"));
        assertTrue("onPageFinished must call the shim",
                migrated.contains("_injectTtsShim(_webView)"));
        // The user WebViewClient is preserved and file-chooser support survives.
        assertEquals("the user WebViewClient is preserved", 1,
                countOccurrences(migrated, "setWebViewClient("));
        assertTrue("file chooser support survives", migrated.contains("onShowFileChooser("));
        assertFalse("no duplicates left", Jx.hasDuplicateWebViewBootstrapCalls(migrated));

        // Idempotent. Migrating twice is byte-identical.
        assertEquals("migrating twice must be byte-identical",
                migrated, Jx.migrateWebViewBootstrapTts(migrated, "\n", false));
    }

    /**
     * Full, compilable-shape activity mirroring the user's real decompiled APK: the exact
     * {@code initialize()} bootstrap with qualified type names, plus the current helper methods so
     * the migrated result can be compiled with {@code javac} against {@code android.jar}.
     */
    private String realDecompiledCompilableActivity() {
        StringBuilder helpers = new StringBuilder();
        Jx.appendWebViewTtsHelpers(helpers, "\n");
        Jx.appendWebViewDownloadHelpers(helpers, "\n");
        StringBuilder imports = new StringBuilder();
        imports.append("import android.speech.tts.TextToSpeech;\n");
        for (String imp : Jx.WEBVIEW_DOWNLOAD_HELPER_REQUIRED_IMPORTS) {
            imports.append("import ").append(imp).append(";\n");
        }
        return "package com.escuelita;\n"
                + "import android.app.Activity;\n"
                + "import android.webkit.*;\n"
                + "import android.media.*;\n"
                + "import android.os.*;\n"
                + "import java.util.*;\n"
                + imports
                + "public class MainActivity extends Activity {\n"
                + "static class Binding { android.webkit.WebView webview1; }\n"
                + "Binding binding = new Binding();\n"
                + helpers
                + "private void initialize(android.os.Bundle bundle) {\n"
                + REAL_CHROME_WITH_SHIM
                + REAL_BLOB_IFACE
                + REAL_BRIDGE_IFACE
                + REAL_SAFE_DOWNLOAD
                + REAL_BLOB_IFACE
                + REAL_CHROME_EMPTY
                + REAL_SAFE_DOWNLOAD
                + REAL_BLOB_IFACE
                + REAL_EMPTY_WEBVIEW_CLIENT
                + "}\n"
                + "private boolean _openWebFileChooser(android.webkit.ValueCallback _cb, android.webkit.WebChromeClient.FileChooserParams _p) { return false; }\n"
                + "}\n"
                + "class AscodeUtil {"
                + " static void a(android.content.Context _c, String _m) {}"
                + " static void showMessage(android.content.Context _c, String _m) {}"
                + " }\n";
    }

    @Test
    public void dumpBootstrapEvidenceTEMP() throws Exception {
        String duplicated = realDecompiledDuplicatedBootstrapSource();
        String migrated = Jx.migrateWebViewBootstrapTts(duplicated, "\n", false);
        String twice = Jx.migrateWebViewBootstrapTts(migrated, "\n", false);
        StringBuilder sb = new StringBuilder();
        sb.append("[BEFORE] hasDuplicate=").append(Jx.hasDuplicateWebViewBootstrapCalls(duplicated));
        sb.append(" needsMigration(tts=true)=").append(yq.needsWebViewSourceMigration(duplicated, true, false));
        sb.append(" needsMigration(tts=false)=").append(yq.needsWebViewSourceMigration(duplicated, false, false)).append("\n");
        sb.append("[BEFORE] chrome=").append(countOccurrences(duplicated, "setWebChromeClient("));
        sb.append(" download=").append(countOccurrences(duplicated, "setDownloadListener("));
        sb.append(" blob=").append(countOccurrences(duplicated, "\"_BlobDownloader\""));
        sb.append(" bridge=").append(countOccurrences(duplicated, "\"AndroidBridge\""));
        sb.append(" webviewClient=").append(countOccurrences(duplicated, "setWebViewClient("));
        sb.append(" onProgress=").append(countOccurrences(duplicated, "onProgressChanged(")).append("\n");
        sb.append("[AFTER ] chrome=").append(countOccurrences(migrated, "setWebChromeClient("));
        sb.append(" download=").append(countOccurrences(migrated, "setDownloadListener("));
        sb.append(" blob=").append(countOccurrences(migrated, "\"_BlobDownloader\""));
        sb.append(" bridge=").append(countOccurrences(migrated, "\"AndroidBridge\""));
        sb.append(" webviewClient=").append(countOccurrences(migrated, "setWebViewClient("));
        sb.append(" onProgress=").append(countOccurrences(migrated, "onProgressChanged(")).append("\n");
        sb.append("[AFTER ] hasDuplicate=").append(Jx.hasDuplicateWebViewBootstrapCalls(migrated)).append("\n");
        sb.append("[AFTER ] chrome carries shim=").append(migrated.contains("_injectTtsShim(webView)")).append("\n");
        sb.append("[AFTER ] onPageFinished has shim=").append(migrated.contains("_injectTtsShim(_webView)")).append("\n");
        sb.append("[AFTER ] idempotent(second pass identical)=").append(migrated.equals(twice)).append("\n");
        Files.write(new File("build/webview-bootstrap-evidence.txt").toPath(),
                sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void realDecompiledMigratedActivityCompilesAgainstAndroidJar() throws Exception {
        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        org.junit.Assume.assumeTrue("a JDK with javac is required", compiler != null);

        File androidJar = extractBundledAndroidJar();
        org.junit.Assume.assumeTrue("bundled android.jar.zip is required", androidJar != null);

        String source = realDecompiledCompilableActivity();
        assertTrue("the real shape must be flagged as duplicated",
                Jx.hasDuplicateWebViewBootstrapCalls(source));

        String migrated = Jx.migrateWebViewBootstrapTts(source, "\n", false);
        StaticJavaParser.parse(migrated);

        File srcDir = new File("build/tts-real-javac/src/com/escuelita");
        assertTrue(srcDir.mkdirs() || srcDir.isDirectory());
        File javaFile = new File(srcDir, "MainActivity.java");
        Files.write(javaFile.toPath(), migrated.getBytes(StandardCharsets.UTF_8));

        File classesDir = new File("build/tts-real-javac/classes");
        assertTrue(classesDir.mkdirs() || classesDir.isDirectory());
        java.io.ByteArrayOutputStream diagnostics = new java.io.ByteArrayOutputStream();
        int result = compiler.run(null, null, diagnostics,
                "-classpath", androidJar.getAbsolutePath(),
                "-d", classesDir.getAbsolutePath(),
                "-proc:none",
                javaFile.getAbsolutePath());
        assertEquals("the migrated real-shaped source must compile:\n"
                + diagnostics.toString(StandardCharsets.UTF_8), 0, result);
    }

    /**
     * Emits the migrated duplicated activity (with the current helpers) and compiles it with
     * {@code javac} against {@code android.jar} extracted from the bundled asset, so the migration
     * is proved to produce compilable Java rather than just parseable text.
     */
    @Test
    public void migratedDuplicatedActivityCompilesAgainstAndroidJar() throws Exception {
        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        org.junit.Assume.assumeTrue("a JDK with javac is required", compiler != null);

        File androidJar = extractBundledAndroidJar();
        org.junit.Assume.assumeTrue("bundled android.jar.zip is required", androidJar != null);

        StringBuilder helpers = new StringBuilder();
        Jx.appendWebViewTtsHelpers(helpers, "\n");
        Jx.appendWebViewDownloadHelpers(helpers, "\n");
        StringBuilder imports = new StringBuilder();
        imports.append("import android.speech.tts.TextToSpeech;\n");
        for (String imp : Jx.WEBVIEW_DOWNLOAD_HELPER_REQUIRED_IMPORTS) {
            imports.append("import ").append(imp).append(";\n");
        }
        String activity = "package com.ascode.dedupcheck;\n"
                + "import android.app.Activity;\n"
                + "import android.webkit.*;\n"
                + "import android.media.*;\n"
                + "import android.os.*;\n"
                + "import java.util.*;\n"
                + imports
                + "public class MainActivity extends Activity {\n"
                + "private WebView webview1;\n"
                + helpers
                + "private void initialize() {\n"
                + CHROME_WITH_SHIM
                + BLOB_IFACE
                + SAFE_DOWNLOAD
                + BLOB_IFACE
                + CHROME_EMPTY
                + SAFE_DOWNLOAD
                + BLOB_IFACE
                + CHROME_EMPTY
                + SAFE_DOWNLOAD
                + BRIDGE_IFACE
                + PAGE_FINISHED
                + "}\n"
                + "private boolean _openWebFileChooser(ValueCallback<Uri[]> _cb, WebChromeClient.FileChooserParams _p) { return false; }\n"
                + "}\n"
                + "class AscodeUtil { static void showMessage(android.content.Context _c, String _m) {} }\n";

        String migrated = Jx.injectTtsShimIntoWebViewClients(
                Jx.deduplicateWebViewBootstrap(activity, "\n"), "\n", false);
        StaticJavaParser.parse(migrated);

        File srcDir = new File("build/tts-dedup-javac/src/com/ascode/dedupcheck");
        assertTrue(srcDir.mkdirs() || srcDir.isDirectory());
        File javaFile = new File(srcDir, "MainActivity.java");
        Files.write(javaFile.toPath(), migrated.getBytes(StandardCharsets.UTF_8));

        File classesDir = new File("build/tts-dedup-javac/classes");
        assertTrue(classesDir.mkdirs() || classesDir.isDirectory());
        java.io.ByteArrayOutputStream diagnostics = new java.io.ByteArrayOutputStream();
        int result = compiler.run(null, null, diagnostics,
                "-classpath", androidJar.getAbsolutePath(),
                "-d", classesDir.getAbsolutePath(),
                "-proc:none",
                javaFile.getAbsolutePath());
        assertEquals("migrated source must compile:\n" + diagnostics.toString(StandardCharsets.UTF_8),
                0, result);
    }

    private File extractBundledAndroidJar() throws Exception {
        File cached = new File("build/android-jar-cache/android.jar");
        if (cached.isFile()) {
            return cached;
        }
        File archive = new File("src/main/assets/libs/android.jar.zip");
        if (!archive.isFile()) {
            return null;
        }
        assertTrue(cached.getParentFile().mkdirs() || cached.getParentFile().isDirectory());
        try (java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(
                new java.io.FileInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if ("android.jar".equals(entry.getName())) {
                    Files.copy(in, cached.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    return cached.isFile() ? cached : null;
                }
            }
        }
        return null;
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}

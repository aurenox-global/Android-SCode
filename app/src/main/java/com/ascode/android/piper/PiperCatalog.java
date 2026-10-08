package com.ascode.android.piper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Curated, offline-first catalog for the built-in Piper TTS engine.
 *
 * <p>Everything here is data only (no Android APIs) so it can be unit-tested on the JVM and reused
 * from both the installer and the settings UI. Two kinds of entries live here:</p>
 *
 * <ul>
 *   <li><b>Runtime</b> — the sherpa-onnx Android runtime that is <em>not</em> bundled in the IDE's
 *       APK (it would add ~40&nbsp;MB). The IDE downloads the official static-link AAR from the
 *       {@code k2-fsa/sherpa-onnx} GitHub release plus {@code kotlin-stdlib} from Maven Central on
 *       demand, then pre-dexes it for the generated app.</li>
 *   <li><b>Voices</b> — a small, Spanish/English-only list of Piper voices published as
 *       {@code vits-piper-<voice>-int8.tar.bz2} under the {@code tts-models} release.</li>
 * </ul>
 *
 * <p>Sizes and SHA-256 digests were computed once against the immutable GitHub/Maven artifacts
 * (neither host publishes a digest) and are pinned here; the installer refuses a download whose
 * digest does not match.</p>
 */
public final class PiperCatalog {

    /** sherpa-onnx release the runtime is taken from. */
    public static final String RUNTIME_VERSION = "1.13.8";

    public static final String RUNTIME_AAR_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/v" + RUNTIME_VERSION
                    + "/sherpa-onnx-static-link-onnxruntime-" + RUNTIME_VERSION + ".aar";
    public static final long RUNTIME_AAR_SIZE = 38_691_998L;
    public static final String RUNTIME_AAR_SHA256 =
            "b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471";

    /** Kotlin runtime the sherpa classes are compiled against; version proven end-to-end in etapa 1. */
    public static final String KOTLIN_STDLIB_VERSION = "1.9.0";
    public static final String KOTLIN_STDLIB_URL =
            "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/"
                    + KOTLIN_STDLIB_VERSION + "/kotlin-stdlib-" + KOTLIN_STDLIB_VERSION + ".jar";
    public static final long KOTLIN_STDLIB_SIZE = 1_708_006L;
    public static final String KOTLIN_STDLIB_SHA256 =
            "35aeffbe2db5aa446072cee50fcee48b7fa9e2fc51ca37c0cc7d7d0bc39d952e";

    public static final String VOICE_ASSET_BASE =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-";

    /** A downloadable, installable Piper voice. */
    public static final class Voice {
        public final String id;
        /** BCP-47-ish tag used for display and language matching, e.g. {@code es-MX}. */
        public final String lang;
        public final String url;
        public final long sizeBytes;
        public final String license;
        public final String sha256;

        Voice(String id, String lang, long sizeBytes, String license, String sha256) {
            this.id = id;
            this.lang = lang;
            this.sizeBytes = sizeBytes;
            this.license = license;
            this.sha256 = sha256;
            this.url = VOICE_ASSET_BASE + id + ".tar.bz2";
        }

        /** e.g. {@code vits-piper-es_MX-ald-medium-int8.tar.bz2}. */
        public String fileName() {
            return "vits-piper-" + id + ".tar.bz2";
        }
    }

    /** Default voice: Mexican Spanish, best size/quality compromise for the target audience. */
    public static final String DEFAULT_VOICE_ID = "es_MX-ald-medium-int8";

    /**
     * Spanish + English voices only, es_MX first. Digests computed by downloading each
     * {@code .tar.bz2} once from the immutable {@code tts-models} release.
     */
    private static final List<Voice> VOICES = Collections.unmodifiableList(Arrays.asList(
            new Voice("es_MX-ald-medium-int8", "es-MX", 21_283_187L, "Unlicense",
                    "447e82d080719409db08e54a4b6eec2e4b6ba850b98dcaf09d3fc67bf30ff692"),
            new Voice("es_MX-claude-high-int8", "es-MX", 21_216_685L, "Apache-2.0",
                    "0f9fc9c07d2e17bdc0f5f33a657addae92085da85704cb86a861e59d32f3bbfa"),
            new Voice("es_ES-carlfm-x_low-int8", "es-ES", 13_356_095L, "Public domain",
                    "fe5b74e55254e2a568a4e4d73fcfcc02580c7957fc337777adf46e5fdfe10218"),
            new Voice("en_US-arctic-medium-int8", "en-US", 23_320_524L, "CC BY 4.0",
                    "d2bfd1f39ed7a16930e3331010608269049522d42bf671c7485ff6814561d3fc"),
            new Voice("en_GB-cori-medium-int8", "en-GB", 20_768_736L, "Public domain",
                    "169ca8aff3adb271f009a4924c99928a811dbf2b52eaca2dbb460e8c34478c93")
    ));

    private PiperCatalog() {
    }

    public static List<Voice> voices() {
        return VOICES;
    }

    public static Voice defaultVoice() {
        Voice v = findById(DEFAULT_VOICE_ID);
        return v != null ? v : VOICES.get(0);
    }

    public static Voice findById(String id) {
        if (id == null) {
            return null;
        }
        for (Voice voice : VOICES) {
            if (voice.id.equals(id)) {
                return voice;
            }
        }
        return null;
    }

    public static Voice findByFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        for (Voice voice : VOICES) {
            if (voice.fileName().equals(fileName)) {
                return voice;
            }
        }
        return null;
    }

    /** Human-readable size, e.g. {@code 20.3 MB}. */
    public static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(java.util.Locale.US, "%.1f KB", kb);
        }
        return String.format(java.util.Locale.US, "%.1f MB", kb / 1024.0);
    }
}

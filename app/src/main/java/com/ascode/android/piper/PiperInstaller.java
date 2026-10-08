package com.ascode.android.piper;

import android.os.Environment;
import android.util.Log;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import mod.jbk.build.BuiltInLibraries;

/**
 * Downloads, verifies, extracts and prepares everything the offline Piper engine needs, fully
 * on-device and without bundling any of it in the IDE's own APK.
 *
 * <p>Two independent pieces are managed here, both rooted at
 * {@code <external>/.AndroidSCode/data/<sc_id>/tts/}:</p>
 *
 * <ul>
 *   <li><b>Runtime</b> ({@code tts/runtime/}) — the official
 *       {@code sherpa-onnx-static-link-onnxruntime-*.aar} plus {@code kotlin-stdlib}: per-ABI
 *       {@code libsherpa-onnx-jni.so}, {@code sherpa-classes.jar}, {@code kotlin-stdlib.jar} and a
 *       single pre-dexed {@code piper-runtime.dex} (produced with the D8 that already ships in the
 *       IDE). Once installed, {@link PiperTtsPackaging} copies these straight into the generated
 *       project's build inputs.</li>
 *   <li><b>Voice</b> ({@code tts/}) — one {@code vits-piper-<id>-int8.tar.bz2} unpacked into
 *       {@code model.onnx}, {@code tokens.txt} and a pruned {@code espeak-ng-data/} (Spanish and
 *       English dictionaries only, saving ~17&nbsp;MB per project).</li>
 * </ul>
 *
 * <p>Downloads are resumable (HTTP {@code Range} into a {@code .part} file) with retries, and every
 * download is rejected unless its SHA-256 matches the value pinned in {@link PiperCatalog}.
 * Progress is reported through {@link Progress} from the calling (background) thread.</p>
 */
public final class PiperInstaller {

    private static final String TAG = "PiperInstaller";

    /** espeak-ng dictionaries kept when pruning: Spanish and English only. */
    private static final String[] KEPT_DICTS = {"es", "en"};
    /** espeak-ng language families kept when pruning. */
    private static final String[] KEPT_LANG_FAMILIES = {"roa", "gmw"};

    private PiperInstaller() {
    }

    /** Progress callback; {@code total <= 0} means "indeterminate". */
    public interface Progress {
        void onStage(String stage, long done, long total);
    }

    // ---------------------------------------------------------------------
    // Paths
    // ---------------------------------------------------------------------

    private static File dataDir(String scId) {
        return new File(Environment.getExternalStorageDirectory(), ".AndroidSCode/data/" + scId);
    }

    /** Folder holding the installed voice ({@code model.onnx}, {@code tokens.txt}, ...). */
    public static File voiceDir(String scId) {
        return new File(dataDir(scId), "tts");
    }

    /** Folder holding the prepared sherpa-onnx runtime. */
    public static File runtimeDir(String scId) {
        return new File(voiceDir(scId), "runtime");
    }

    // ---------------------------------------------------------------------
    // Installed-state queries
    // ---------------------------------------------------------------------

    public static boolean isRuntimeInstalled(String scId) {
        return isRuntimeInstalled(runtimeDir(scId));
    }

    public static boolean isRuntimeInstalled(File runtimeDir) {
        if (runtimeDir == null || !new File(runtimeDir, ".installed").isFile()) {
            return false;
        }
        if (PiperTtsRuntime.runtimeJars(runtimeDir).isEmpty()) {
            return false;
        }
        if (PiperTtsRuntime.runtimeDex(runtimeDir) == null) {
            return false;
        }
        for (String abi : PiperTtsRuntime.SUPPORTED_ABIS) {
            if (new File(new File(runtimeDir, abi), PiperTtsRuntime.SO_NAME).isFile()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isVoiceInstalled(String scId) {
        return PiperTtsRuntime.isVoiceInstalled(voiceDir(scId));
    }

    /** @return the id of the installed voice, or {@code null} when none is installed. */
    public static String installedVoiceId(String scId) {
        File marker = new File(voiceDir(scId), "voice.id");
        if (!marker.isFile()) {
            return null;
        }
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            try (InputStream in = new FileInputStream(marker)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    bos.write(buffer, 0, read);
                }
            }
            String id = new String(bos.toByteArray(), "UTF-8").trim();
            return id.isEmpty() ? null : id;
        } catch (IOException e) {
            return null;
        }
    }

    /** @return the size in bytes of the currently installed voice folder, or 0. */
    public static long installedVoiceSize(String scId) {
        return dirSize(voiceDir(scId));
    }

    // ---------------------------------------------------------------------
    // Runtime installation
    // ---------------------------------------------------------------------

    /**
     * Downloads and prepares the sherpa-onnx runtime for {@code scId}. Safe to re-run: an already
     * installed runtime is kept unless {@code force} is set.
     */
    public static void installRuntime(String scId, Progress progress, boolean force) throws Exception {
        File rt = runtimeDir(scId);
        if (!force && isRuntimeInstalled(rt)) {
            report(progress, "runtime-done", 1, 1);
            return;
        }
        if (!rt.isDirectory() && !rt.mkdirs()) {
            throw new IOException("Cannot create " + rt);
        }

        File aar = new File(rt, "runtime.aar");
        File stdlib = new File(rt, "kotlin-stdlib.jar");
        download(PiperCatalog.RUNTIME_AAR_URL, aar, PiperCatalog.RUNTIME_AAR_SIZE,
                PiperCatalog.RUNTIME_AAR_SHA256, progress, "runtime-aar");
        download(PiperCatalog.KOTLIN_STDLIB_URL, stdlib, PiperCatalog.KOTLIN_STDLIB_SIZE,
                PiperCatalog.KOTLIN_STDLIB_SHA256, progress, "runtime-stdlib");

        extractAar(aar, rt);
        // The AAR is only needed to extract from; drop it so the runtime folder stays small.
        //noinspection ResultOfMethodCallIgnored
        aar.delete();

        dex(rt, stdlib, progress);

        writeText(new File(rt, ".installed"), PiperCatalog.RUNTIME_VERSION);
        report(progress, "runtime-done", 1, 1);
        Log.i(TAG, "Runtime Piper instalado en " + rt);
    }

    private static void extractAar(File aar, File rt) throws IOException {
        int abis = 0;
        try (ZipFile zip = new ZipFile(aar)) {
            for (String abi : PiperTtsRuntime.SUPPORTED_ABIS) {
                ZipEntry so = zip.getEntry("jni/" + abi + "/" + PiperTtsRuntime.SO_NAME);
                if (so == null) {
                    continue;
                }
                File out = new File(new File(rt, abi), PiperTtsRuntime.SO_NAME);
                ensureParent(out);
                try (InputStream in = zip.getInputStream(so)) {
                    copyStream(in, out);
                }
                abis++;
            }
            if (abis == 0) {
                throw new IOException("El AAR no trae " + PiperTtsRuntime.SO_NAME);
            }
            ZipEntry classes = zip.getEntry("classes.jar");
            if (classes == null) {
                throw new IOException("El AAR no trae classes.jar");
            }
            try (InputStream in = zip.getInputStream(classes)) {
                copyStream(in, new File(rt, "sherpa-classes.jar"));
            }
        }
    }

    private static void dex(File rt, File stdlib, Progress progress) throws Exception {
        report(progress, "runtime-dex", 0, 1);
        File sherpa = new File(rt, "sherpa-classes.jar");
        File outDir = new File(rt, "dex");
        deleteRecursively(outDir);
        if (!outDir.mkdirs()) {
            throw new IOException("Cannot create " + outDir);
        }

        D8Command.Builder builder = D8Command.builder()
                .setMode(CompilationMode.RELEASE)
                .setMinApiLevel(21)
                .setOutput(outDir.toPath(), OutputMode.DexIndexed)
                .addProgramFiles(sherpa.toPath(), stdlib.toPath());
        File androidJar = new File(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "android.jar");
        if (androidJar.isFile()) {
            builder.addLibraryFiles(androidJar.toPath());
        }
        D8.run(builder.build());

        // Publish the produced dex file(s) at the runtime root under a collision-free name.
        File[] dexes = outDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".dex"));
        if (dexes == null || dexes.length == 0) {
            throw new IOException("D8 no produjo ningún .dex");
        }
        java.util.Arrays.sort(dexes, (a, b) -> a.getName().compareTo(b.getName()));
        for (int i = 0; i < dexes.length; i++) {
            File target = new File(rt, i == 0 ? "piper-runtime.dex" : "piper-runtime" + (i + 1) + ".dex");
            if (target.exists() && !target.delete()) {
                throw new IOException("Cannot replace " + target);
            }
            if (!dexes[i].renameTo(target)) {
                copyStream(new FileInputStream(dexes[i]), target);
            }
        }
        deleteRecursively(outDir);
        report(progress, "runtime-dex", 1, 1);
    }

    // ---------------------------------------------------------------------
    // Voice installation
    // ---------------------------------------------------------------------

    /**
     * Installs {@code voice}: downloads its {@code .tar.bz2} (unless {@code localArchive} is given,
     * e.g. from a SAF import), verifies the digest, unpacks it and prunes {@code espeak-ng-data}.
     */
    public static void installVoice(String scId, PiperCatalog.Voice voice, File localArchive,
                                    Progress progress) throws Exception {
        if (voice == null) {
            throw new IllegalArgumentException("voice == null");
        }
        File tts = voiceDir(scId);
        if (!tts.isDirectory() && !tts.mkdirs()) {
            throw new IOException("Cannot create " + tts);
        }

        File archive = localArchive;
        if (archive == null) {
            archive = new File(tts, voice.fileName() + ".part");
            downloadToFile(voice.url, archive, voice.sizeBytes, voice.sha256, progress, "voice-download", false);
        } else {
            report(progress, "voice-verify", 0, 1);
            verifySha256(localArchive, voice.sha256);
            report(progress, "voice-verify", 1, 1);
        }

        // Unpack into a scratch dir, then move the pieces into place so a failure leaves the
        // previous install untouched.
        File scratch = new File(tts, "unpack");
        deleteRecursively(scratch);
        if (!scratch.mkdirs()) {
            throw new IOException("Cannot create " + scratch);
        }
        report(progress, "voice-extract", 0, 1);
        extractTarBz2(archive, scratch);
        report(progress, "voice-extract", 1, 1);

        File root = findVoiceRoot(scratch);
        File model = PiperTtsRuntime.findVoiceModel(root);
        File tokens = new File(root, "tokens.txt");
        File espeak = new File(root, "espeak-ng-data");
        if (model == null || !tokens.isFile() || !espeak.isDirectory()) {
            deleteRecursively(scratch);
            throw new IOException("El paquete no contiene model.onnx/tokens.txt/espeak-ng-data");
        }

        report(progress, "voice-prune", 0, 1);
        long saved = pruneEspeakData(espeak);
        report(progress, "voice-prune", 1, 1);
        Log.i(TAG, "espeak-ng-data podado: ahorro " + saved + " bytes");

        // Clear the previous voice (keeping the runtime) and move the new pieces in.
        for (File child : listOrEmpty(tts)) {
            String name = child.getName();
            if (child.isFile() && (name.equals("model.onnx") || name.equals("tokens.txt")
                    || name.endsWith(".onnx.json") || name.equals("voice.id"))) {
                //noinspection ResultOfMethodCallIgnored
                child.delete();
            }
        }
        deleteRecursively(new File(tts, "espeak-ng-data"));

        copyStream(new FileInputStream(model), new File(tts, "model.onnx"));
        File modelJson = findModelJson(root);
        if (modelJson != null) {
            copyStream(new FileInputStream(modelJson), new File(tts, modelJson.getName()));
        }
        copyStream(new FileInputStream(tokens), new File(tts, "tokens.txt"));
        PiperTtsRuntime.copyDir(espeak, new File(tts, "espeak-ng-data"));
        writeText(new File(tts, "voice.id"), voice.id);
        deleteRecursively(scratch);
        if (localArchive == null) {
            //noinspection ResultOfMethodCallIgnored
            archive.delete();
        }
        report(progress, "voice-done", 1, 1);
        Log.i(TAG, "Voz Piper instalada: " + voice.id + " (" + installedVoiceSize(scId) + " bytes)");
    }

    /** @return the directory containing {@code tokens.txt} inside {@code scratch}, or {@code scratch}. */
    private static File findVoiceRoot(File scratch) {
        File[] children = listOrEmpty(scratch);
        for (File child : children) {
            if (child.isDirectory() && new File(child, "tokens.txt").isFile()) {
                return child;
            }
        }
        return scratch;
    }

    private static File findModelJson(File root) {
        for (File child : listOrEmpty(root)) {
            if (child.isFile() && child.getName().toLowerCase().endsWith(".onnx.json")) {
                return child;
            }
        }
        return null;
    }

    /**
     * Removes espeak-ng dictionaries and language definitions that are neither Spanish nor English.
     * The bulk of {@code espeak-ng-data} is its dictionary set ({@code ru_dict} alone is ~8&nbsp;MB),
     * so this cuts the folder from ~18&nbsp;MB to ~1.8&nbsp;MB without affecting es/en
     * phonemization.
     *
     * @return bytes reclaimed
     */
    public static long pruneEspeakData(File espeakData) {
        if (espeakData == null || !espeakData.isDirectory()) {
            return 0;
        }
        long[] saved = {0};

        // 1. Dictionaries: keep <lang>_dict only for the kept languages (+ base "en-*" variants).
        for (File child : listOrEmpty(espeakData)) {
            String name = child.getName();
            if (!child.isFile() || !name.endsWith("_dict")) {
                continue;
            }
            String lang = name.substring(0, name.length() - "_dict".length());
            if (!isKeptDict(lang)) {
                saved[0] += child.length();
                //noinspection ResultOfMethodCallIgnored
                child.delete();
            }
        }

        // 2. Language definitions: keep only the kept families (roa: Spanish/Catalan..., gmw: English...).
        File lang = new File(espeakData, "lang");
        for (File family : listOrEmpty(lang)) {
            if (!family.isDirectory()) {
                continue;
            }
            if (!isKeptFamily(family.getName())) {
                saved[0] += dirSize(family);
                deleteRecursively(family);
            }
        }
        return saved[0];
    }

    private static boolean isKeptDict(String lang) {
        String base = lang.toLowerCase();
        for (String kept : KEPT_DICTS) {
            if (base.equals(kept) || base.startsWith(kept + "-")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKeptFamily(String family) {
        for (String kept : KEPT_LANG_FAMILIES) {
            if (kept.equalsIgnoreCase(family)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // Download / digest helpers
    // ---------------------------------------------------------------------

    /**
     * Resumable download to {@code dest}. A partial download lives in {@code dest + ".part"}; on
     * retry the existing bytes are reused through a {@code Range} request.
     */
    public static void download(String url, File dest, long expectedSize, String expectedSha256,
                                Progress progress, String stage) throws IOException {
        downloadToFile(url, dest, expectedSize, expectedSha256, progress, stage, true);
    }

    private static void downloadToFile(String url, File dest, long expectedSize, String expectedSha256,
                                       Progress progress, String stage, boolean inPlace) throws IOException {
        File part = inPlace ? new File(dest.getAbsolutePath() + ".part") : dest;
        ensureParent(part);
        if (part.isFile() && expectedSize > 0 && part.length() == expectedSize) {
            // Already fully downloaded previously; just verify.
            verifySha256(part, expectedSha256);
            finishDownload(part, dest, inPlace);
            return;
        }
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            long existing = part.isFile() ? part.length() : 0;
            if (expectedSize > 0 && existing > expectedSize) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                existing = 0;
            }
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(20_000);
                conn.setReadTimeout(60_000);
                conn.setRequestProperty("User-Agent", "Android-SCode-Piper");
                if (existing > 0) {
                    conn.setRequestProperty("Range", "bytes=" + existing + "-");
                }
                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                    throw new IOException("HTTP " + code + " for " + url);
                }
                boolean append = code == HttpURLConnection.HTTP_PARTIAL;
                try (InputStream in = new BufferedInputStream(conn.getInputStream());
                     RandomAccessFile raf = new RandomAccessFile(part, "rw")) {
                    if (!append) {
                        raf.setLength(0);
                        existing = 0;
                    }
                    raf.seek(existing);
                    byte[] buffer = new byte[64 * 1024];
                    long done = existing;
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        raf.write(buffer, 0, read);
                        done += read;
                        report(progress, stage, done, expectedSize);
                    }
                }
                last = null;
                break;
            } catch (IOException e) {
                last = e;
                Log.w(TAG, "Descarga fallida (intento " + (attempt + 1) + ") " + url + ": " + e);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
        if (last != null) {
            throw last;
        }
        verifySha256(part, expectedSha256);
        finishDownload(part, dest, inPlace);
    }

    private static void finishDownload(File part, File dest, boolean inPlace) throws IOException {
        if (inPlace) {
            if (dest.exists() && !dest.delete()) {
                throw new IOException("Cannot replace " + dest);
            }
            if (!part.renameTo(dest)) {
                copyStream(new FileInputStream(part), dest);
                //noinspection ResultOfMethodCallIgnored
                part.delete();
            }
        }
    }

    private static void verifySha256(File file, String expected) throws IOException {
        if (expected == null || expected.isEmpty()) {
            return;
        }
        String actual = sha256(file);
        if (!expected.equalsIgnoreCase(actual)) {
            if (file.getName().endsWith(".part")) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            throw new IOException("SHA-256 no coincide para " + file.getName()
                    + ": esperado " + expected + ", obtenido " + actual);
        }
    }

    public static String sha256(File file) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return toHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------------
    // Archive helpers
    // ---------------------------------------------------------------------

    /** Extracts a {@code .tar.bz2} archive into {@code destDir}. */
    public static void extractTarBz2(File archive, File destDir) throws IOException {
        try (InputStream raw = new BufferedInputStream(new FileInputStream(archive));
             BZip2CompressorInputStream bz = new BZip2CompressorInputStream(raw, true);
             TarArchiveInputStream tar = new TarArchiveInputStream(bz)) {
            TarArchiveEntry entry;
            byte[] buffer = new byte[64 * 1024];
            while ((entry = tar.getNextEntry()) != null) {
                File out = new File(destDir, entry.getName());
                if (!out.getCanonicalPath().startsWith(destDir.getCanonicalPath())) {
                    throw new IOException("Entrada insegura en el tar: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    if (!out.isDirectory() && !out.mkdirs()) {
                        throw new IOException("Cannot create " + out);
                    }
                    continue;
                }
                ensureParent(out);
                try (OutputStream os = new FileOutputStream(out)) {
                    int read;
                    while ((read = tar.read(buffer)) > 0) {
                        os.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Small file helpers
    // ---------------------------------------------------------------------

    static void ensureParent(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
    }

    static void copyStream(InputStream in, File dest) throws IOException {
        ensureParent(dest);
        try (OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        }
    }

    static void writeText(File file, String value) throws IOException {
        ensureParent(file);
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(value.getBytes("UTF-8"));
        }
    }

    static File[] listOrEmpty(File dir) {
        File[] children = dir == null ? null : dir.listFiles();
        return children == null ? new File[0] : children;
    }

    static long dirSize(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return 0;
        }
        long total = 0;
        for (File child : listOrEmpty(dir)) {
            total += child.isDirectory() ? dirSize(child) : child.length();
        }
        return total;
    }

    static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            for (File child : listOrEmpty(file)) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static void report(Progress progress, String stage, long done, long total) {
        if (progress != null) {
            try {
                progress.onStage(stage, done, total);
            } catch (Throwable t) {
                Log.w(TAG, "Progress callback falló: " + t);
            }
        }
    }

    /** Unused import guard for {@link List} (kept for API symmetry / future multi-part support). */
    @SuppressWarnings("unused")
    private static List<String> supportedAbis() {
        return new ArrayList<>(java.util.Arrays.asList(PiperTtsRuntime.SUPPORTED_ABIS));
    }
}

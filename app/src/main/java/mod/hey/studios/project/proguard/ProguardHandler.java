package mod.hey.studios.project.proguard;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import a.a.a.ProjectBuilder;
import mod.hey.studios.util.Helper;
import mod.jbk.build.BuildProgressReceiver;
import com.ascode.android.utility.FileUtil;

public class ProguardHandler {
    private static final String TAG = "ProguardHandler";

    /**
     * Marks a config whose shrink settings come from the "new project" defaults instead of an
     * explicit user choice (see {@link #writeNewProjectDefaultConfig(String)}). Such defaults are
     * release-only: debug/debuggable builds never shrink, while an explicit user choice is always
     * honoured.
     */
    private static final String KEY_SHRINK_DEFAULT = "shrink_default";

    /**
     * Default rules for the classic ProGuard path. Keeps {@code -dontoptimize} on purpose: that
     * path is legacy and its behaviour must not change for existing projects.
     */
    public static String ANDROID_PROGUARD_RULES_PATH = createAndroidRules();

    /**
     * Default rules used by the R8 path. Same keeps as {@link #ANDROID_PROGUARD_RULES_PATH} but
     * <b>without</b> {@code -dontoptimize}, so R8 is allowed to optimize while it shrinks. The file
     * lives under a name of its own, so installations upgraded from an older version (whose
     * {@code android-proguard-rules.pro} already exists and is therefore never rewritten) still
     * receive the rule set without {@code -dontoptimize}.
     */
    public static String R8_BASE_PROGUARD_RULES_PATH = createR8BaseRules();

    /**
     * Conservative keep rules used only when R8 is active, so that libraries' classes which are
     * reached through reflection, the manifest or JSON deserialization survive shrinking.
     * <p>
     * The file is migrated in place when it still holds a pristine older default, and left
     * untouched when the user edited it (unlike {@link #createAndroidRules()} it never forces
     * {@code -dontoptimize}).
     */
    public static String SAFE_PROGUARD_RULES_PATH = createSafeRules();

    public static String DEFAULT_PROGUARD_RULES_PATH = "";
    private final String config_path;
    private final String fm_config_path;

    public ProguardHandler(String sc_id) {
        DEFAULT_PROGUARD_RULES_PATH = createDefaultRules(sc_id);
        config_path = getConfigPath(sc_id);
        fm_config_path = FileUtil.getExternalStorageDir() + "/.AndroidSCode/data/" + sc_id + "/proguard_fm";

        if (!FileUtil.isExistFile(config_path)) {
            FileUtil.writeFile(config_path, getDefaultConfig());
        }
    }

    /**
     * @param sc_id ID of the project
     * @return Absolute path of the project's shrinker config file
     */
    public static String getConfigPath(String sc_id) {
        return FileUtil.getExternalStorageDir() + "/.AndroidSCode/data/" + sc_id + "/proguard";
    }

    /**
     * Writes the config a brand-new project is born with: R8 shrinker enabled by default. It is
     * <b>release-only</b> (see {@link #KEY_SHRINK_DEFAULT}); opening the Code Shrinking Manager and
     * toggling anything there clears the marker and makes the choice apply to every build type.
     * <p>
     * Only called when a project is created; already existing projects (including projects restored
     * from a backup that carries their {@code proguard} file) keep their current config untouched.
     *
     * @param sc_id ID of the newly created project
     */
    public static void writeNewProjectDefaultConfig(String sc_id) {
        HashMap<String, String> config = new HashMap<>();
        config.put("enabled", "true");
        config.put("r8", "true");
        config.put("debug", "false");
        config.put(KEY_SHRINK_DEFAULT, "true");

        FileUtil.writeFile(getConfigPath(sc_id), new Gson().toJson(config));
    }

    /**
     * Writes {@code newContent} to {@code path} when the file does not exist yet or when it still
     * holds one of the {@code legacyDefaults} verbatim (whitespace aside). A file the user edited
     * is left completely untouched and reported in the log.
     *
     * @param path           Absolute path of the generated rules file
     * @param legacyDefaults Pristine older defaults that may be safely upgraded
     * @param newContent     Current default content
     * @param label          Human readable name used for logging
     */
    private static void ensureGeneratedRulesFile(String path, List<String> legacyDefaults, String newContent, String label) {
        if (!FileUtil.isExistFile(path)) {
            FileUtil.writeFile(path, newContent);
            return;
        }

        String current = normalizeRules(FileUtil.readFile(path));
        if (current.equals(normalizeRules(newContent))) {
            return;
        }

        for (String legacyDefault : legacyDefaults) {
            if (current.equals(normalizeRules(legacyDefault))) {
                FileUtil.writeFile(path, newContent);
                android.util.Log.d("Ascode", TAG + ": migrated default rules file to the current default: " + path);
                return;
            }
        }

        android.util.Log.d("Ascode", TAG + ": rules file of " + label + " was edited by the user; left untouched: " + path);
    }

    /**
     * Normalizes a rules file for comparison: line endings, per-line indentation and blank lines
     * are irrelevant to the shrinker, so they must not make an untouched default look edited.
     */
    private static String normalizeRules(String content) {
        StringBuilder normalized = new StringBuilder();

        for (String line : content.replace("\r", "").split("\n")) {
            String trimmed = line.trim();

            if (!trimmed.isEmpty()) {
                normalized.append(trimmed).append('\n');
            }
        }

        return normalized.toString();
    }

    private static String createAndroidRules() {
        String rulePath = FileUtil.getExternalStorageDir() + "/.AndroidSCode/libs/android-proguard-rules.pro";

        if (!FileUtil.isExistFile(rulePath)) {
            FileUtil.writeFile(rulePath, """
                    -dontusemixedcaseclassnames
                    -dontskipnonpubliclibraryclasses
                    -verbose

                    -dontoptimize
                    -dontpreverify

                    -keepattributes *Annotation*
                    -keep public class com.google.vending.licensing.ILicensingService
                    -keep public class com.android.vending.licensing.ILicensingService

                    -keepclasseswithmembernames class * {
                        native <methods>;
                    }

                    -keepclassmembers public class * extends android.view.View {
                       void set*(***);
                       *** get*();
                    }

                    -keepclassmembers class * extends android.app.Activity {
                       public void *(android.view.View);
                    }

                    -keepclassmembers enum * {
                        public static **[] values();
                        public static ** valueOf(java.lang.String);
                    }

                    -keepclassmembers class * implements android.os.Parcelable {
                      public static final android.os.Parcelable$Creator CREATOR;
                    }

                    -keepclassmembers class **.R$* {
                        public static <fields>;
                    }

                    -dontwarn android.support.**

                    -keep class android.support.annotation.Keep

                    -keep @android.support.annotation.Keep class * {*;}

                    -keepclasseswithmembers class * {
                        @android.support.annotation.Keep <methods>;
                    }

                    -keepclasseswithmembers class * {
                        @android.support.annotation.Keep <fields>;
                    }

                    -keepclasseswithmembers class * {
                        @android.support.annotation.Keep <init>(...);
                    }

                    -keepclassmembers class * {
                        @android.webkit.JavascriptInterface <methods>;
                    }

                    -dontwarn android.arch.**
                    -dontwarn android.lifecycle.**
                    -keep class android.arch.** { *; }
                    -keep class android.lifecycle.** { *; }

                    -dontwarn androidx.arch.**
                    -dontwarn androidx.lifecycle.**
                    -keep class androidx.arch.** { *; }
                    -keep class androidx.lifecycle.** { *; }
                    """);
        }

        return rulePath;
    }

    /**
     * Rules used by the R8 path: the {@link #createAndroidRules()} keeps minus
     * {@code -dontoptimize} (R8 is allowed to optimize). Created for every installation, including
     * those upgraded from a version that predates this file.
     *
     * @return Absolute path of the file containing the rules
     */
    private static String createR8BaseRules() {
        String rulePath = FileUtil.getExternalStorageDir() + "/.AndroidSCode/libs/ascode-r8-rules.pro";

        ensureGeneratedRulesFile(rulePath, List.of(), """
                # R8 base rules (Android SCode). Same keeps as android-proguard-rules.pro, but
                # WITHOUT -dontoptimize: R8 is allowed to optimize while it shrinks the app and its
                # built-in libraries.
                # Automatically generated; user edits are preserved on updates.
                # ascode-rules-version: 2

                -dontusemixedcaseclassnames
                -dontskipnonpubliclibraryclasses

                -dontpreverify

                -keepattributes *Annotation*
                -keep public class com.google.vending.licensing.ILicensingService
                -keep public class com.android.vending.licensing.ILicensingService

                -keepclasseswithmembernames class * {
                    native <methods>;
                }

                -keepclassmembers public class * extends android.view.View {
                   void set*(***);
                   *** get*();
                }

                -keepclassmembers class * extends android.app.Activity {
                   public void *(android.view.View);
                }

                -keepclassmembers enum * {
                    public static **[] values();
                    public static ** valueOf(java.lang.String);
                }

                -keepclassmembers class * implements android.os.Parcelable {
                  public static final android.os.Parcelable$Creator CREATOR;
                }

                -keepclassmembers class **.R$* {
                    public static <fields>;
                }

                -dontwarn android.support.**

                -keep class android.support.annotation.Keep

                -keep @android.support.annotation.Keep class * {*;}

                -keepclasseswithmembers class * {
                    @android.support.annotation.Keep <methods>;
                }

                -keepclasseswithmembers class * {
                    @android.support.annotation.Keep <fields>;
                }

                -keepclasseswithmembers class * {
                    @android.support.annotation.Keep <init>(...);
                }

                -keepclassmembers class * {
                    @android.webkit.JavascriptInterface <methods>;
                }

                -dontwarn android.arch.**
                -dontwarn android.lifecycle.**
                -keep class android.arch.** { *; }
                -keep class android.lifecycle.** { *; }

                -dontwarn androidx.arch.**
                -dontwarn androidx.lifecycle.**
                -keep class androidx.arch.** { *; }
                -keep class androidx.lifecycle.** { *; }
                """, "ascode-r8-rules.pro");

        return rulePath;
    }

    /**
     * Conservative keep rules used only when R8 is active (see the field's javadoc).
     * <p>
     * Pristine copies written by an older version are upgraded in place; a user-edited copy is
     * never overwritten.
     *
     * @return Absolute path of the file containing the rules
     */
    private static String createSafeRules() {
        String rulePath = FileUtil.getExternalStorageDir() + "/.AndroidSCode/libs/ascode-safe-rules.pro";

        ensureGeneratedRulesFile(rulePath, List.of(SAFE_RULES_LEGACY_DEFAULT), """
                # Conservative keep rules for shrinking built-in libraries with R8.
                # Automatically generated; user edits are preserved on updates.
                # It never forces -dontoptimize.
                # ascode-rules-version: 2

                # --- Android components instantiated from the generated manifest ---
                -keep class * extends android.app.Activity { *; }
                -keep class * extends android.app.Service { *; }
                -keep class * extends android.content.BroadcastReceiver { *; }
                -keep class * extends android.content.ContentProvider { *; }
                -keep class * extends android.app.Application { *; }
                -keep class * extends android.app.backup.BackupAgentHelper { *; }

                # --- Attributes needed by reflection / JSON / Kotlin / annotations ---
                -keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exceptions
                -keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
                -keepattributes RuntimeInvisibleAnnotations, RuntimeInvisibleParameterAnnotations
                -keepattributes AnnotationDefault, MethodParameters, KotlinMetadata

                # --- Parcelable, native methods, enums, JS interfaces, resources, views ---
                -keep class * implements android.os.Parcelable {
                    public static final android.os.Parcelable$Creator *;
                }
                -keepclasseswithmembernames class * {
                    native <methods>;
                }
                -keepclassmembers enum * {
                    public static **[] values();
                    public static ** valueOf(java.lang.String);
                }
                -keepclassmembers class * {
                    @android.webkit.JavascriptInterface <methods>;
                }
                -keepclassmembers class **.R$* {
                    public static <fields>;
                }
                -keepclassmembers class * extends android.view.View {
                    void set*(***);
                    *** get*();
                }

                # --- Reflection-heavy libraries (proguard.txt of each library is also applied) ---
                -keep class com.google.gson.** { *; }
                -keepclassmembers,allowobfuscation class * {
                    @com.google.gson.annotations.SerializedName <fields>;
                }
                -keep class com.airbnb.lottie.** { *; }
                -keep class com.bumptech.glide.** { *; }

                # --- Kotlin / KMP ---
                -keep class kotlin.Metadata { *; }
                -keepclassmembers class **$Companion { *; }
                -dontwarn kotlin.**

                # --- Firebase / Play Services (complements each library's proguard.txt) ---
                -keep class com.google.firebase.** { *; }
                -dontwarn com.google.android.gms.**
                """, "ascode-safe-rules.pro");

        return rulePath;
    }

    private static String createDefaultRules(String sc_id) {
        String path = FileUtil.getExternalStorageDir() + "/.AndroidSCode/data/" + sc_id + "/proguard-rules.pro";

        if (!FileUtil.isExistFile(path)) {
            FileUtil.writeFile(path, """
                    -repackageclasses
                    -ignorewarnings
                    -dontwarn
                    -dontnote
                    """);
        }

        return path;
    }

    /**
     * Exact content written by versions before the rule migration: kept only so that a pristine
     * copy on an existing installation can be recognised and safely upgraded.
     */
    private static final String SAFE_RULES_LEGACY_DEFAULT = """
            # Conservative keep rules for shrinking built-in libraries with R8.
            # This file is created once and can be overwritten; it never forces -dontoptimize.

            # --- Android components instantiated from the generated manifest ---
            -keep class * extends android.app.Activity { *; }
            -keep class * extends android.app.Service { *; }
            -keep class * extends android.content.BroadcastReceiver { *; }
            -keep class * extends android.content.ContentProvider { *; }
            -keep class * extends android.app.Application { *; }
            -keep class * extends android.app.backup.BackupAgentHelper { *; }

            # --- Attributes needed by reflection / JSON / Kotlin / annotations ---
            -keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exceptions
            -keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
            -keepattributes RuntimeInvisibleAnnotations, RuntimeInvisibleParameterAnnotations
            -keepattributes AnnotationDefault, MethodParameters, KotlinMetadata

            # --- Parcelable, native methods, enums, JS interfaces, resources, views ---
            -keep class * implements android.os.Parcelable {
                public static final android.os.Parcelable$Creator *;
            }
            -keepclasseswithmembernames class * {
                native <methods>;
            }
            -keepclassmembers enum * {
                public static **[] values();
                public static ** valueOf(java.lang.String);
            }
            -keepclassmembers class * {
                @android.webkit.JavascriptInterface <methods>;
            }
            -keepclassmembers class **.R$* {
                public static <fields>;
            }
            -keepclassmembers class * extends android.view.View {
                void set*(***);
                *** get*();
            }

            # --- Reflection-heavy libraries ({@code proguard.txt} of each library is also applied) ---
            -keep class com.google.gson.** { *; }
            -keepclassmembers,allowobfuscation class * {
                @com.google.gson.annotations.SerializedName <fields>;
            }
            -keep class com.airbnb.lottie.** { *; }
            -keep class com.bumptech.glide.** { *; }

            # --- Kotlin / KMP ---
            -keep class kotlin.Metadata { *; }
            -keepclassmembers class **$Companion { *; }
            -dontwarn kotlin.**

            # --- Firebase / Play Services (complements each library's proguard.txt) ---
            -keep class com.google.firebase.** { *; }
            -dontwarn com.google.android.gms.**
            """;

    private String getDefaultConfig() {
        HashMap<String, String> defaultConfig = new HashMap<>();

        defaultConfig.put("enabled", "false");
        defaultConfig.put("debug", "false");

        return new Gson().toJson(defaultConfig);
    }

    public String getCustomProguardRules() {
        return DEFAULT_PROGUARD_RULES_PATH;
    }

    public boolean isDebugFilesEnabled() {
        boolean debugFiles = true;
        if (FileUtil.isExistFile(config_path)) {
            try {
                HashMap<String, String> config = new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);

                if (!config.containsKey("debug")) return false;

                String debug = config.get("debug");
                if (debug != null) {
                    debugFiles = debug.equals("true");
                }

            } catch (Exception e) {
                debugFiles = false;
            }
        }

        return debugFiles;
    }

    public boolean isShrinkingEnabled() {
        boolean proguardEnabled = true;
        if (FileUtil.isExistFile(config_path)) {
            try {
                HashMap<String, String> config = new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);

                String enabled = config.get("enabled");
                if (enabled == null) {
                    proguardEnabled = false;
                } else {
                    proguardEnabled = enabled.equals("true");
                }

            } catch (Exception e) {
                proguardEnabled = false;
            }
        }

        return proguardEnabled;
    }

    /**
     * @return Whether the shrink settings still come from the new-project default and were never
     * touched by the user. In that case shrinking is release-only.
     */
    public boolean isShrinkFromDefault() {
        if (FileUtil.isExistFile(config_path)) {
            try {
                HashMap<String, String> config = new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);

                return "true".equals(config.get(KEY_SHRINK_DEFAULT));
            } catch (Exception e) {
                return false;
            }
        }

        return false;
    }

    /**
     * Removes the "came from the new-project default" marker, so that from now on the user's
     * explicit choice is honoured on every build type (including debug ones).
     */
    private void markConfigAsUserChosen(HashMap<String, String> config) {
        if (config.remove(KEY_SHRINK_DEFAULT) != null) {
            android.util.Log.d("Ascode", TAG + ": shrink settings changed by the user; the release-only default no longer applies");
        }
    }

    public void setProguardEnabled(boolean proguardEnabled) {
        HashMap<String, String> config = new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);
        config.put("enabled", String.valueOf(proguardEnabled));
        markConfigAsUserChosen(config);

        FileUtil.writeFile(config_path, new Gson().toJson(config));
    }

    public boolean isR8Enabled() {
        boolean r8Enabled = true;
        if (FileUtil.isExistFile(config_path)) {
            try {
                var config = new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);

                String enabled = config.get("r8");
                if (enabled == null) {
                    r8Enabled = false;
                } else {
                    r8Enabled = enabled.equals("true");
                }

            } catch (Exception e) {
                r8Enabled = false;
            }
        }

        return r8Enabled;
    }

    public void setR8Enabled(boolean r8Enabled) {
        var config = new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);
        config.put("r8", String.valueOf(r8Enabled));
        markConfigAsUserChosen(config);

        FileUtil.writeFile(config_path, new Gson().toJson(config));
    }

    public boolean libIsProguardFMEnabled(String library) {
        boolean enabled;
        if (isShrinkingEnabled() && FileUtil.isExistFile(fm_config_path)) {
            String configContent = FileUtil.readFile(fm_config_path);

            if (configContent.isEmpty()) {
                return false;
            }

            try {
                ArrayList<String> config = new Gson().fromJson(configContent, Helper.TYPE_STRING);
                enabled = config.contains(library);
                return enabled;
            } catch (Exception ignored) {
                android.util.Log.d("Ascode", "ProguardHandler: Exception ignored", ignored);
            }
        }

        return false;
    }

    public void setDebugEnabled(boolean debugEnabled) {
        HashMap<String, String> config = new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);
        config.put("debug", String.valueOf(debugEnabled));
        markConfigAsUserChosen(config);

        FileUtil.writeFile(config_path, new Gson().toJson(config));
    }

    public void setProguardFMLibs(ArrayList<String> fullModeLibs) {
        FileUtil.writeFile(fm_config_path, new Gson().toJson(fullModeLibs));
    }

    public void start(BuildProgressReceiver progressReceiver, ProjectBuilder builder) throws IOException {
        /*
         * Full gate (shared with ProjectBuilder): shrinking off, or the new-project default on a
         * debug/debuggable build, which must not shrink.
         */
        if (builder.isShrinkBuildEnabled()) {
            if (isR8Enabled()) {
                progressReceiver.onProgress("Running R8 on classes...", 15);
                builder.runR8();
            } else {
                progressReceiver.onProgress("ProGuarding classes...", 16);
                builder.runProguard();
            }
        }
    }
}

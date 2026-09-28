package io.ascode.android;

import com.ascode.android.utility.FileUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads/writes the project's AndroidManifest permission injections, the same file the
 * "Permissions" screen of {@code AndroidManifestInjectionDetails} edits:
 * {@code .AndroidSCode/data/<scId>/Injection/androidmanifest/attributes.json}.
 *
 * <p>Each permission is one entry {@code {"name":"_application_permissions",
 * "value":"android:name=\"android.permission.CAMERA\""}} and the manifest builder turns
 * every entry into a {@code <uses-permission .../>} tag.</p>
 *
 * <p>Additive-only helpers: they never drop unrelated attributes (application attrs,
 * activity attrs, app components...) already present in the file.</p>
 */
public final class AgentProjectPermissions {

    public static final String CONSTANT = "_application_permissions";

    private AgentProjectPermissions() {
    }

    /** The attributes.json file that stores the custom manifest injections. */
    public static File attributesFile(String scId) {
        return new File(FileUtil.getExternalStorageDir(),
                ".AndroidSCode/data/" + scId + "/Injection/androidmanifest/attributes.json");
    }

    /**
     * Accepts "CAMERA", "android.permission.CAMERA" or "Manifest.permission.CAMERA"
     * and always returns a fully-qualified permission name (or "" if empty).
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String permission = raw.trim().replace("\"", "").replace("'", "");
        if (permission.isEmpty()) {
            return "";
        }
        permission = permission.replace("Manifest.permission.", "android.permission.");
        if (permission.contains(".")) {
            return permission;
        }
        return "android.permission." + permission;
    }

    /** Permission names currently added to the project manifest (order preserved). */
    public static List<String> list(String scId) {
        List<String> permissions = new ArrayList<>();
        JSONArray attributes = readAttributes(scId);
        for (int i = 0; i < attributes.length(); i++) {
            JSONObject entry = attributes.optJSONObject(i);
            if (entry == null || !CONSTANT.equals(entry.optString("name", ""))) {
                continue;
            }
            String permission = parsePermissionName(entry.optString("value", ""));
            if (!permission.isEmpty() && !permissions.contains(permission)) {
                permissions.add(permission);
            }
        }
        return permissions;
    }

    /** @return true if the (already normalized) permission is present. */
    public static boolean contains(String scId, String normalizedPermission) {
        if (normalizedPermission == null || normalizedPermission.isEmpty()) {
            return false;
        }
        return list(scId).contains(normalizedPermission);
    }

    /**
     * Adds a permission to the project manifest.
     *
     * @param rawPermission "CAMERA" or "android.permission.CAMERA"
     * @return true if it was added, false if it was empty or already present.
     */
    public static boolean add(String scId, String rawPermission) {
        String permission = normalize(rawPermission);
        if (permission.isEmpty() || contains(scId, permission)) {
            return false;
        }
        JSONArray attributes = readAttributes(scId);
        JSONObject entry = new JSONObject();
        try {
            entry.put("name", CONSTANT);
            entry.put("value", "android:name=\"" + permission + "\"");
        } catch (Exception e) {
            return false;
        }
        attributes.put(entry);
        return writeAttributes(scId, attributes);
    }

    /**
     * Removes a permission from the project manifest.
     *
     * @return true if a matching entry was removed.
     */
    public static boolean remove(String scId, String rawPermission) {
        String permission = normalize(rawPermission);
        if (permission.isEmpty()) {
            return false;
        }
        JSONArray attributes = readAttributes(scId);
        JSONArray kept = new JSONArray();
        boolean removed = false;
        for (int i = 0; i < attributes.length(); i++) {
            JSONObject entry = attributes.optJSONObject(i);
            if (entry != null
                    && CONSTANT.equals(entry.optString("name", ""))
                    && permission.equals(parsePermissionName(entry.optString("value", "")))) {
                removed = true;
                continue;
            }
            if (entry != null) {
                kept.put(entry);
            }
        }
        if (!removed) {
            return false;
        }
        return writeAttributes(scId, kept);
    }

    /** Raw file content (or null when the file does not exist) — used for the rollback safety net. */
    static String readRaw(String scId) {
        try {
            File file = attributesFile(scId);
            if (!file.exists()) {
                return null;
            }
            return FileUtil.readFile(file.getAbsolutePath());
        } catch (Throwable throwable) {
            return null;
        }
    }

    /** Restores the raw content captured before the actions ran (null removes the file). */
    static void restoreRaw(String scId, String raw) {
        try {
            File file = attributesFile(scId);
            if (raw == null) {
                if (file.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
                return;
            }
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            FileUtil.writeFile(file.getAbsolutePath(), raw);
        } catch (Throwable ignored) {
            android.util.Log.w("Ascode", "AgentProjectPermissions: no se pudo restaurar attributes.json", ignored);
        }
    }

    private static JSONArray readAttributes(String scId) {
        try {
            File file = attributesFile(scId);
            if (!file.exists()) {
                return new JSONArray();
            }
            String content = FileUtil.readFile(file.getAbsolutePath());
            if (content == null || content.trim().isEmpty()) {
                return new JSONArray();
            }
            return new JSONArray(content);
        } catch (Throwable throwable) {
            return new JSONArray();
        }
    }

    private static boolean writeAttributes(String scId, JSONArray attributes) {
        try {
            File file = attributesFile(scId);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return false;
            }
            FileUtil.writeFile(file.getAbsolutePath(), attributes.toString());
            return true;
        } catch (Throwable throwable) {
            android.util.Log.w("Ascode", "AgentProjectPermissions: no se pudo escribir attributes.json", throwable);
            return false;
        }
    }

    /** Extracts {@code android.permission.X} from {@code android:name="android.permission.X"}. */
    private static String parsePermissionName(String value) {
        if (value == null) {
            return "";
        }
        int start = value.indexOf('"');
        int end = value.lastIndexOf('"');
        if (start >= 0 && end > start) {
            return value.substring(start + 1, end).trim();
        }
        int eq = value.indexOf('=');
        if (eq >= 0) {
            return value.substring(eq + 1).trim().replace("\"", "");
        }
        return value.trim();
    }
}

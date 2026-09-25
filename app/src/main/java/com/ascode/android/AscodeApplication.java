package com.ascode.android;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;

import com.besome.sketch.tools.CollectErrorActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import dev.aldi.sayuti.block.MyBlockDefaultsInstaller;
import io.ascode.android.rag.LocalAiRagRegistry;
import com.ascode.android.featureflags.FeatureFlags;
import com.ascode.android.metrics.StartupPerformanceTracker;
import com.ascode.android.utility.theme.ThemeManager;

public class AscodeApplication extends Application {
    private static Context mApplicationContext;

    public static Context getContext() {
        return mApplicationContext;
    }

    @Override
    public void onCreate() {
        mApplicationContext = getApplicationContext();
        Thread.UncaughtExceptionHandler existingHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(@NonNull Thread thread, @NonNull Throwable throwable) {
                Intent intent = new Intent(getApplicationContext(), CollectErrorActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intent.putExtra("error", Log.getStackTraceString(throwable));
                startActivity(intent);
                if (existingHandler != null) {
                    existingHandler.uncaughtException(thread, throwable);
                } else {
                    Process.killProcess(Process.myPid());
                    System.exit(1);
                }
            }
        });
        super.onCreate();
        FeatureFlags.applyDefaultsIfMissing(this);
        MyBlockDefaultsInstaller.installIfNeeded(this);
        ThemeManager.applyTheme(this, ThemeManager.getCurrentTheme(this));
        StartupPerformanceTracker.markApplicationCreated(this);
        LocalAiRagRegistry.getInstance().initialize(this);
        importLegacyStorageIfNeeded();
    }

    /**
     * La app guarda sus datos en /.AndroidSCode. Las instalaciones anteriores usaban /.sketchware,
     * asi que la primera vez copiamos esos datos para que los proyectos ya creados aparezcan.
     */
    private void importLegacyStorageIfNeeded() {
        try {
            File legacyDir = new File(Environment.getExternalStorageDirectory(), ".sketchware");
            if (!legacyDir.isDirectory()) return;
            File legacyData = new File(legacyDir, "data");
            if (!legacyData.isDirectory()) return;

            var prefs = getSharedPreferences("ascode_prefs", MODE_PRIVATE);
            if (prefs.getBoolean("legacy_import_done", false)) return;

            File currentData = new File(new File(Environment.getExternalStorageDirectory(), ".AndroidSCode"), "data");
            if (hasProjects(currentData)) return;

            new Thread(() -> {
                boolean ok = copyDirectory(legacyDir, new File(Environment.getExternalStorageDirectory(), ".AndroidSCode"));
                Log.i("AscodeApplication", "Importacion de .sketchware: " + (ok ? "completada" : "fallida"));
                if (ok) prefs.edit().putBoolean("legacy_import_done", true).apply();
            }, "ascode-legacy-import").start();
        } catch (Throwable t) {
            Log.w("AscodeApplication", "No se pudieron importar los datos de .sketchware", t);
        }
    }

    private static boolean hasProjects(File dataDir) {
        String[] children = dataDir.isDirectory() ? dataDir.list() : null;
        if (children == null) return false;
        for (String child : children) {
            if (child.matches("\\d+")) return true;
        }
        return false;
    }

    private static boolean copyDirectory(File src, File dst) {
        try {
            if (src.isDirectory()) {
                if (!dst.isDirectory() && !dst.mkdirs()) return false;
                File[] children = src.listFiles();
                if (children == null) return false;
                boolean ok = true;
                for (File child : children) {
                    ok &= copyDirectory(child, new File(dst, child.getName()));
                }
                return ok;
            }
            if (dst.exists()) return true;
            File parent = dst.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
            try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}

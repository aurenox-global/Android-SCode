package com.ascode.android.tools;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.ascode.android.utility.FileUtil;

public class ImageFactory {

    /**
     * Saves a bitmap of a view to storage.
     *
     * @param view      The view to save as bitmap.
     * @param imageName The image's name inside /Internal storage/ascode/saved_block/.
     * @return A File object of the saved bitmap.
     */
    public static File saveBitmap(View view, String imageName) {
        File saveToDirectory = new File(FileUtil.getExternalStorageDir(), "ascode/saved_block");
        if (!saveToDirectory.exists()) {
            FileUtil.makeDir(saveToDirectory.getAbsolutePath());
        }
        File savedBitmap = new File(saveToDirectory.getPath() + File.separator + imageName + ".png");
        Bitmap bitmap = getBitmapFromView(view);
        try {
            savedBitmap.createNewFile();
            FileOutputStream fileOutputStream = new FileOutputStream(savedBitmap);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);
            fileOutputStream.flush();
            fileOutputStream.close();
        } catch (IOException e) {
            Log.e(ImageFactory.class.getSimpleName(), "Couldn't save bitmap with name " + imageName + ": " + e.getMessage(), e);
        }
        return savedBitmap;
    }

    public static Bitmap getBitmapFromView(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Drawable background = view.getBackground();
        if (background != null) {
            background.draw(canvas);
        } else {
            canvas.drawColor(-1);
            view.draw(canvas);
        }
        return bitmap;
    }
}
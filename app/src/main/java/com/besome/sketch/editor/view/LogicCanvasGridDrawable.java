package com.besome.sketch.editor.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ascode.android.R;
import com.ascode.android.utility.ThemeUtils;

/**
 * Fondo del lienzo del editor de lógica: color de superficie + rejilla de puntos sutil.
 *
 * <p>Pinta una tesela repetida ({@link BitmapShader} en modo {@link Shader.TileMode#REPEAT})
 * con un punto al ~6% de opacidad sobre el color {@code ?attr/colorSurface}. De este modo la
 * rejilla se adapta automáticamente a tema claro y oscuro sin recursos adicionales.</p>
 */
public class LogicCanvasGridDrawable extends Drawable {

    /** Separación entre puntos de la rejilla (dp). */
    private static final float DOT_SPACING_DP = 24.0f;
    /** Radio de cada punto (dp). */
    private static final float DOT_RADIUS_DP = 1.2f;
    /** Opacidad de los puntos (~6%). */
    private static final int DOT_ALPHA = 0x0F;

    private final Paint backgroundColorPaint = new Paint();
    private final Paint gridPaint = new Paint();
    private final int backgroundColor;

    public LogicCanvasGridDrawable(@NonNull Context context) {
        backgroundColor = ThemeUtils.getColor(context, R.attr.colorSurface);
        int onSurface = ThemeUtils.getColor(context, R.attr.colorOnSurface);

        float density = context.getResources().getDisplayMetrics().density;
        int tileSize = Math.max(1, Math.round(DOT_SPACING_DP * density));
        float radius = DOT_RADIUS_DP * density;

        backgroundColorPaint.setColor(backgroundColor);

        // La tesela se dibuja una sola vez; el shader la repite al pintar el fondo.
        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(onSurface);
        dotPaint.setAlpha(DOT_ALPHA);
        Bitmap tile = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888);
        Canvas tileCanvas = new Canvas(tile);
        tileCanvas.drawCircle(tileSize / 2f, tileSize / 2f, radius, dotPaint);

        gridPaint.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        canvas.drawRect(bounds, backgroundColorPaint);
        canvas.drawRect(bounds, gridPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        // No-op: la opacidad de los puntos es fija.
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        // No-op: los colores se resuelven del tema.
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}

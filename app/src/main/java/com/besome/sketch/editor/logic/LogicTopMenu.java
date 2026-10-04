package com.besome.sketch.editor.logic;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;

import com.ascode.android.R;
import com.ascode.android.databinding.LogicEditorTopMenuBinding;
import com.ascode.android.utility.ThemeUtils;

public class LogicTopMenu extends LinearLayout {

    /**
     * How much accent is blended into the neutral card surface for the active
     * background. Keeps the tint subtle (tonal) in both light and dark themes.
     */
    private static final float ACTIVE_TONAL_RATIO = 0.18f;

    private final Context context;
    public boolean isDeleteActive;
    public boolean isCopyActive;
    public boolean isFavoriteActive;
    public boolean isDetailActive;
    private int colorSurfaceContainerHigh;
    private int colorOnSurfaceVariant;
    private int accentDelete;
    private int accentCopy;
    private int accentFavorite;
    private int accentDetail;
    private LogicEditorTopMenuBinding binding;

    public LogicTopMenu(Context context) {
        super(context);
        this.context = context;
        initialize();
    }

    public LogicTopMenu(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        initialize();
    }

    private void initialize() {
        binding = LogicEditorTopMenuBinding.inflate(LayoutInflater.from(context), this, true);

        colorSurfaceContainerHigh = ThemeUtils.getColor(this, R.attr.colorSurfaceContainerHigh);
        colorOnSurfaceVariant = ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant);
        accentDelete = ContextCompat.getColor(context, R.color.scolor_red_02);
        accentCopy = ContextCompat.getColor(context, R.color.scolor_green_normal);
        accentDetail = ContextCompat.getColor(context, R.color.scolor_green_violet);
        // The collection/bookmark action is the only accented card by default,
        // matching the red bookmark of the mockup.
        accentFavorite = ThemeUtils.getColor(this, R.attr.colorError);
    }

    public void setCopyActive(boolean active) {
        isCopyActive = active;
        updateLayoutAppearance(binding.layoutCopy, active, accentCopy, binding.ivCopy, colorOnSurfaceVariant);
    }

    public void setDeleteActive(boolean active) {
        isDeleteActive = active;
        updateLayoutAppearance(binding.layoutDelete, active, accentDelete, binding.ivTrash, colorOnSurfaceVariant);
    }

    public void setDetailActive(boolean active) {
        isDetailActive = active;
        updateLayoutAppearance(binding.layoutDetail, active, accentDetail, binding.ivDetail, colorOnSurfaceVariant);
    }

    public void setFavoriteActive(boolean active) {
        isFavoriteActive = active;
        updateLayoutAppearance(binding.layoutFavorite, active, accentFavorite, binding.ivBookmark, accentFavorite);
    }

    public boolean isInsideCopyArea(float x, float y) {
        return isInsideArea(binding.layoutCopy, x, y);
    }

    public boolean isInsideDeleteArea(float x, float y) {
        return isInsideArea(binding.layoutDelete, x, y);
    }

    public boolean isInsideDetailArea(float x, float y) {
        return isInsideArea(binding.layoutDetail, x, y);
    }

    public boolean isInsideFavoriteArea(float x, float y) {
        return isInsideArea(binding.layoutFavorite, x, y);
    }

    public void toggleLayoutVisibility(boolean isBlockCollection) {
        binding.layoutFavorite.setVisibility(isBlockCollection ? VISIBLE : GONE);
        binding.layoutCopy.setVisibility(isBlockCollection ? VISIBLE : GONE);
        binding.layoutDetail.setVisibility(isBlockCollection ? GONE : VISIBLE);
    }

    /**
     * Active: tonal background derived from the accent + accent-tinted icon.
     * Inactive: neutral surface container background + neutral icon (or the accent
     * itself for the bookmark card, which stays colored by default).
     */
    private void updateLayoutAppearance(
            MaterialCardView layout, boolean active, int accentColor, ImageView icon, int defaultIconColor) {
        if (active) {
            layout.setCardBackgroundColor(tonal(accentColor));
            icon.setColorFilter(accentColor);
        } else {
            layout.setCardBackgroundColor(colorSurfaceContainerHigh);
            icon.setColorFilter(defaultIconColor);
        }
    }

    /** Blends the accent over the neutral surface to obtain a subtle tonal fill. */
    private int tonal(int accentColor) {
        return blend(accentColor, colorSurfaceContainerHigh, ACTIVE_TONAL_RATIO);
    }

    private static int blend(int foreground, int background, float ratio) {
        int r = Math.round(Color.red(foreground) * ratio + Color.red(background) * (1f - ratio));
        int g = Math.round(Color.green(foreground) * ratio + Color.green(background) * (1f - ratio));
        int b = Math.round(Color.blue(foreground) * ratio + Color.blue(background) * (1f - ratio));
        return Color.argb(255, r, g, b);
    }

    private boolean isInsideArea(View layout, float x, float y) {
        if (layout.getVisibility() == GONE) {
            return false;
        }

        int[] location = new int[2];
        layout.getLocationOnScreen(location);

        return x > location[0] && x < location[0] + layout.getWidth()
                && y > location[1] && y < location[1] + layout.getHeight();
    }
}

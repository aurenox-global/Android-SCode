package com.besome.sketch.editor.logic;

import static com.ascode.android.utility.ThemeUtils.getColor;
import static com.ascode.android.utility.ThemeUtils.isDarkThemeEnabled;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import java.util.Locale;

import a.a.a.Rs;
import a.a.a.Ts;
import a.a.a.wB;
import com.ascode.android.R;
import com.ascode.android.databinding.PaletteBlockBinding;

public class PaletteBlock extends LinearLayout {

    public float f = 0.0F;
    private PaletteBlockBinding binding;
    private Context context;
    private EditText searchInput;
    private String searchQuery = "";

    public PaletteBlock(Context context) {
        super(context);
        initialize(context);
    }

    public PaletteBlock(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    private void initialize(Context context) {
        this.context = context;
        binding = PaletteBlockBinding.inflate(LayoutInflater.from(context), this, true);
        f = wB.a(context, 1.0F);

        searchInput = binding.paletteSearchInput;
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    /**
     * Hides every palette entry whose label does not match the current query.
     * Works on the already-built nodes of both the actions and blocks containers.
     */
    private void applyFilter(String query) {
        searchQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filterContainer(binding.actionsContainer);
        filterContainer(binding.blockBuilder);
    }

    private void filterContainer(ViewGroup container) {
        if (container == null) {
            return;
        }
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (isSpacer(child)) {
                continue;
            }
            child.setVisibility(matches(child) ? View.VISIBLE : View.GONE);
        }
    }

    private void applyFilterToView(View view) {
        if (view == null || isSpacer(view)) {
            return;
        }
        view.setVisibility(matches(view) ? View.VISIBLE : View.GONE);
    }

    private boolean isSpacer(View view) {
        return view.getClass() == View.class;
    }

    private boolean matches(View view) {
        if (searchQuery.isEmpty()) {
            return true;
        }
        return collectText(view).contains(searchQuery);
    }

    private String collectText(View view) {
        StringBuilder text = new StringBuilder();
        if (view.getContentDescription() != null) {
            text.append(view.getContentDescription()).append(' ');
        }
        Object tag = view.getTag();
        if (tag instanceof CharSequence) {
            text.append(tag).append(' ');
        }
        appendChildText(view, text);
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private void appendChildText(View view, StringBuilder text) {
        if (view instanceof TextView) {
            CharSequence label = ((TextView) view).getText();
            if (label != null) {
                text.append(label).append(' ');
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                appendChildText(group.getChildAt(i), text);
            }
        }
    }

    public Ts a(String var1, String var2, String var3) {
        View view = new View(context);
        view.setLayoutParams(getLayoutParams(8.0F));
        binding.blockBuilder.addView(view);
        Rs blockView = new Rs(context, -1, var1, var2, var3);
        blockView.setContentDescription(generateContentDescription(var3));
        blockView.setBlockType(1);
        binding.blockBuilder.addView(blockView);
        applyFilterToView(blockView);
        return blockView;
    }

    public Ts a(String var1, String var2, String var3, String var4) {
        View view = new View(context);
        view.setLayoutParams(getLayoutParams(8.0F));
        binding.blockBuilder.addView(view);
        Rs blockView = new Rs(context, -1, var1, var2, var3, var4);
        blockView.setContentDescription(generateContentDescription(var4));
        blockView.setBlockType(1);
        binding.blockBuilder.addView(blockView);
        applyFilterToView(blockView);
        return blockView;
    }

    public TextView a(String title) {
        var textView = new TextView(context);
        textView.setText(title);
        textView.setTextSize(10.0F);
        textView.setTypeface(null, Typeface.BOLD);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding((int) (f * 8.0F), 0, (int) (f * 8.0F), 0);

        var cardView = new MaterialCardView(context);
        var params = getLayoutParams(30.0F);
        params.setMargins(0, 0, (int) (f * 4), (int) (f * 6));
        cardView.setLayoutParams(params);
        cardView.setCardBackgroundColor(getColor(context, isDarkThemeEnabled(context) ? R.attr.colorSurfaceContainerHigh : R.attr.colorSurfaceContainerHighest));
        cardView.addView(textView);

        binding.actionsContainer.addView(cardView);
        applyFilterToView(cardView);
        return textView;
    }

    public void a() {
        binding.blockBuilder.removeAllViews();
        binding.actionsContainer.removeAllViews();
    }

    public void a(String title, int color) {
        var cardView = new MaterialCardView(context);
        var params = getLayoutParams(18.0F);
        params.topMargin = (int) (f * 16.0F);
        cardView.setLayoutParams(params);
        cardView.setCardBackgroundColor(color);
        cardView.setRadius(f * 8f);

        TextView textView = new TextView(context);
        textView.setText(title);
        textView.setTextColor(getColor(context, isDarkThemeEnabled(context) ? R.attr.colorOnSurface : R.attr.colorOnSurfaceInverse));
        textView.setTextSize(10.0F);
        textView.setGravity(Gravity.CENTER | Gravity.LEFT);
        textView.setPadding((int) (f * 12.0F), 0, (int) (f * 12.0F), 0);
        cardView.addView(textView);

        binding.blockBuilder.addView(cardView);
        applyFilterToView(cardView);
    }

    public void addDeprecatedBlock(String message, String type, String opCode) {
        if (message != null && !message.isEmpty()) {
            a(message, getColor(context, isDarkThemeEnabled(context) ? R.attr.colorSurfaceContainerHigh : R.attr.colorSurfaceInverse));
        }
        Ts blockView = a("", type, opCode);
        blockView.e = 0xFFBDBDBD;
        blockView.setTag(opCode);
    }

    private String generateContentDescription(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        result.append(name.charAt(0));
        for (int i = 1; i < name.length(); i++) {
            char currentChar = name.charAt(i);
            if (Character.isUpperCase(currentChar)) {
                // Check if previous char is not already a space (for acronyms like "HTTPExample")
                // and if the current char is not part of an acronym (e.g. the TTP in HTTP)
                // For simplicity here, just add a space before any uppercase unless it's followed by lowercase.
                if (i + 1 < name.length() && Character.isLowerCase(name.charAt(i + 1)) || Character.isLowerCase(name.charAt(i - 1))) {
                    result.append(' ');
                }
            }
            result.append(currentChar);
        }
        return result.toString();
    }

    private LinearLayout.LayoutParams getLayoutParams(float heightMultiplier) {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (f * heightMultiplier));
    }

    public void setDragEnabled(boolean dragEnabled) {
        if (dragEnabled) {
            binding.scroll.b();
            binding.scrollHorizontal.b();
        } else {
            binding.scroll.a();
            binding.scrollHorizontal.a();
        }
    }

    public void setMinWidth(int minWidth) {
        binding.scroll.setMinimumWidth(minWidth - (int) (f * 5.0F));
        binding.scrollHorizontal.setMinimumWidth(minWidth - (int) (f * 5.0F));
        getLayoutParams().width = minWidth;
    }

    public void setUseScroll(boolean useScroll) {
        binding.scroll.setUseScroll(useScroll);
        binding.scrollHorizontal.setUseScroll(useScroll);
    }
}

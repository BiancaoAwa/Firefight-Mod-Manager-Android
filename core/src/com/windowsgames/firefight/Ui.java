package com.windowsgames.firefight;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

/**
 * Widget factory used to build every manager view in code; no XML layout is added to the game.
 */

final class Ui {

    private Ui() {
    }

    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    static int color(Context c, int attr, int fallback) {
        try {
            TypedValue tv = new TypedValue();
            if (c.getTheme().resolveAttribute(attr, tv, true)
                    && tv.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return tv.data;
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    static int bg(Context c) {
        return color(c, android.R.attr.colorBackground, 0xFF101214);
    }

    static int cardColor(Context c) {
        return color(c, android.R.attr.colorBackgroundFloating, 0xFF1C2024);
    }

    static int primary(Context c) {
        return color(c, android.R.attr.textColorPrimary, 0xFFECEFF1);
    }

    static int secondary(Context c) {
        return color(c, android.R.attr.textColorSecondary, 0xFF9AA0A6);
    }

    static int accent(Context c) {
        return color(c, android.R.attr.colorAccent, 0xFF4C9BE8);
    }

    private static int faded(Context c, int alpha) {
        return (secondary(c) & 0x00FFFFFF) | (alpha << 24);
    }

    static GradientDrawable cardBg(Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(c, 10));
        g.setColor(cardColor(c));
        g.setStroke(Math.max(1, dp(c, 1)), faded(c, 0x33));
        return g;
    }

    /** Card background with an accent border, used to mark the row a jump landed on. */
    static GradientDrawable cardBgMarked(Context c) {
        GradientDrawable g = cardBg(c);
        g.setStroke(Math.max(1, dp(c, 2)), accent(c));
        return g;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        l.setBackground(cardBg(c));
        int p = dp(c, 10);
        l.setPadding(p, p, p, p);
        return l;
    }

    static void pad(View v, Context c, float l, float t, float r, float b) {
        v.setPadding(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
    }

    static LinearLayout.LayoutParams fill() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(s == null ? "" : s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        if (bold) {
            v.setTypeface(Typeface.DEFAULT_BOLD);
        }
        v.setLineSpacing(dp(c, 2), 1f);
        return v;
    }

    static TextView title(Context c, String s) {
        return text(c, s, 20f, primary(c), true);
    }

    static TextView subtitle(Context c, String s) {
        return text(c, s, 13f, secondary(c), false);
    }

    static TextView body(Context c, String s) {
        return text(c, s, 15f, primary(c), false);
    }

    static TextView strong(Context c, String s) {
        return text(c, s, 16f, primary(c), true);
    }

    static TextView small(Context c, String s) {
        return text(c, s, 12f, secondary(c), false);
    }

    static Button button(Context c, String s) {
        Button b = new Button(c);
        b.setText(s == null ? "" : s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        return b;
    }

    static Button tabButton(Context c, String s) {
        Button b = button(c, s);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(c, 2), dp(c, 4), dp(c, 2), dp(c, 4));
        return b;
    }

    static Button action(Context c, String s) {
        Button b = button(c, s);
        b.setTextColor(accent(c));
        return b;
    }

    static Button headerAction(Context c, String s) {
        Button b = action(c, s);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(c, 8), dp(c, 2), dp(c, 8), dp(c, 2));
        return b;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(faded(c, 0x33));
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1))));
        return v;
    }

    static View spacer(Context c, float h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, h)));
        return v;
    }

    static EditText searchBox(Context c, String hint) {
        EditText e = new EditText(c);
        e.setSingleLine(true);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        e.setHint(hint == null ? "" : hint);
        e.setHintTextColor(secondary(c));
        e.setTextColor(primary(c));
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setPadding(dp(c, 8), dp(c, 6), dp(c, 8), dp(c, 6));
        e.setBackground(cardBg(c));
        return e;
    }

    /** Compact button for row arrows and numbering. Tall enough for a comfortable tap. */
    static Button mini(Context c, String s) {
        Button b = action(c, s);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        b.setMinWidth(dp(c, 44));
        b.setMinimumWidth(dp(c, 44));
        b.setMinHeight(dp(c, 38));
        b.setMinimumHeight(dp(c, 38));
        b.setPadding(dp(c, 8), dp(c, 6), dp(c, 8), dp(c, 6));
        return b;
    }

    /** Square preview of an image file; an empty frame when the file cannot be decoded. */
    static ImageView thumb(Context c, File f, float sizeDp) {
        ImageView v = new ImageView(c);
        int px = dp(c, sizeDp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px, px);
        lp.rightMargin = dp(c, 8);
        v.setLayoutParams(lp);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setBackground(cardBg(c));
        if (f != null) {
            Bitmap bm = decode(f, px);
            if (bm != null) {
                v.setImageBitmap(bm);
            }
        }
        return v;
    }

    private static Bitmap decode(File f, int want) {
        try {
            BitmapFactory.Options probe = new BitmapFactory.Options();
            probe.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), probe);
            int sample = 1;
            int longest = Math.max(probe.outWidth, probe.outHeight);
            while (longest > 0 && longest / sample > want * 2) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
        } catch (Throwable t) {
            return null;
        }
    }
}

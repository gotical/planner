package ru.rybinsklab.planner;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class Ui {

    static int BG, CARD, CARD2, TEXT, SUB, FAINT, ACCENT, ACCENT_SOFT, DIVIDER, RED, GREEN, BLUE, ORANGE, BORDER;
    static boolean dark;

    static void init(Context c) {
        SharedPreferences p = c.getSharedPreferences("planner", 0);
        dark = p.getBoolean("dark", false);
        ACCENT = p.getInt("accent", 0xFF4772FA);
        ACCENT_SOFT = (ACCENT & 0x00FFFFFF) | 0x1A000000;
        if (dark) {
            BG = 0xFF141414; CARD = 0xFF1F1F1F; CARD2 = 0xFF262626;
            TEXT = 0xFFECECEC; SUB = 0xFF9A9A9A; FAINT = 0xFF6A6A6A;
            DIVIDER = 0xFF2A2A2A; BORDER = 0xFF333333;
            RED = 0xFFEF5350; GREEN = 0xFF66BB6A; BLUE = 0xFF42A5F5; ORANGE = 0xFFFFA726;
        } else {
            BG = 0xFFF7F7F9; CARD = 0xFFFFFFFF; CARD2 = 0xFFF0F1F4;
            TEXT = 0xFF202020; SUB = 0xFF787878; FAINT = 0xFFB0B0B0;
            DIVIDER = 0xFFEDEDEF; BORDER = 0xFFE4E4E8;
            RED = 0xFFE53935; GREEN = 0xFF43A047; BLUE = 0xFF1E88E5; ORANGE = 0xFFF57C00;
        }
    }

    static int dp(Context c, float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()); }

    static TextView tv(Context c, String s, float sp, int color) { return tv(c, s, sp, color, false); }
    static TextView tv(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static LinearLayout row(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c) {
            @Override
            protected LinearLayout.LayoutParams generateDefaultLayoutParams() {
                return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        };
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static GradientDrawable bg(Context c, int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radius));
        return g;
    }
    static ImageView icon(Context c, int resId, int sizeDp, int color) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(resId);
        iv.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        int s = dp(c, sizeDp);
        iv.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return iv;
    }
    static ImageView iconTouch(Context c, int resId, int sizeDp, int color) {
        ImageView iv = icon(c, resId, sizeDp, color);
        iv.setScaleType(ImageView.ScaleType.CENTER);
        return iv;
    }
    static GradientDrawable oval(int color) { GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(color); return g; }
    static GradientDrawable ring(Context c, int color, float width) { GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setStroke(dp(c, width), color); return g; }
    static GradientDrawable stroke(Context c, int color, float width, float radius) { GradientDrawable g = new GradientDrawable(); g.setStroke(dp(c, width), color); g.setCornerRadius(dp(c, radius)); return g; }

    static LinearLayout.LayoutParams weight(float w) { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w); }
    static LinearLayout.LayoutParams fullWeight(float w) { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, w); }

    static View spacer(Context c, int h) { View v = new View(c); v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)); return v; }

    static EditText et(Context c, String hint, float sp) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(FAINT);
        e.setTextSize(sp);
        e.setTextColor(TEXT);
        e.setBackgroundColor(0x00000000);
        e.setPadding(0, dp(c, 12), 0, dp(c, 12));
        e.setSingleLine(true);
        return e;
    }

    static ScrollView scroll(Context c) { ScrollView s = new ScrollView(c); s.setFillViewport(true); s.setClipToPadding(false); return s; }

    static void divider(LinearLayout parent, Context c, int padding) {
        View v = new View(c);
        v.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(padding, 0, padding, 0);
        parent.addView(v, lp);
    }
}

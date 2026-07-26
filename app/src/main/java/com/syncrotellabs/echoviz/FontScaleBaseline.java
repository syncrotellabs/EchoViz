package com.example.echoviz;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

final class FontScaleBaseline {
    private static final String PREFS_NAME = "echoviz_font_scale";
    private static final String KEY_BASELINE_SCALE = "baseline_scale";
    private static final String KEY_REQUESTED_RELATIVE_SCALE = "requested_relative_scale";
    private static final float DEFAULT_SCALE = 1.0f;
    private static final float MIN_SCALE = 0.85f;
    private static final float MAX_SCALE = 2.0f;

    private FontScaleBaseline() {
    }

    static float ensure(Context context) {
        SharedPreferences prefs = prefs(context);
        if (!prefs.contains(KEY_BASELINE_SCALE)) {
            float current = current(context);
            prefs.edit()
                    .putFloat(KEY_BASELINE_SCALE, current)
                    .putFloat(KEY_REQUESTED_RELATIVE_SCALE, DEFAULT_SCALE)
                    .apply();
            return current;
        }
        return prefs.getFloat(KEY_BASELINE_SCALE, DEFAULT_SCALE);
    }

    static float captureCurrent(Context context) {
        float current = current(context);
        prefs(context).edit()
                .putFloat(KEY_BASELINE_SCALE, current)
                .putFloat(KEY_REQUESTED_RELATIVE_SCALE, DEFAULT_SCALE)
                .apply();
        return current;
    }

    static float current(Context context) {
        try {
            return Settings.System.getFloat(context.getContentResolver(), Settings.System.FONT_SCALE);
        } catch (Settings.SettingNotFoundException ignored) {
            return DEFAULT_SCALE;
        }
    }

    static float relative(Context context) {
        return current(context) / ensure(context);
    }

    static float requestedRelative(Context context) {
        SharedPreferences prefs = prefs(context);
        if (!prefs.contains(KEY_REQUESTED_RELATIVE_SCALE)) {
            return relative(context);
        }
        return prefs.getFloat(KEY_REQUESTED_RELATIVE_SCALE, DEFAULT_SCALE);
    }

    static float saveRequestedRelativeForAbsolute(Context context, float absoluteScale) {
        float relativeScale = absoluteScale / ensure(context);
        prefs(context).edit().putFloat(KEY_REQUESTED_RELATIVE_SCALE, relativeScale).apply();
        return relativeScale;
    }

    static float scaleForRelative(Context context, float relativeScale) {
        return clamp(ensure(context) * relativeScale);
    }

    static String percentLabel(float scale) {
        return Math.round(scale * 100) + "%";
    }

    private static float clamp(float scale) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}

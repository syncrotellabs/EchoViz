package com.example.echoviz;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

final class EchoVizRuntime {
    static final String ACTION_RESTORE_OVERLAY = "com.example.echoviz.action.RESTORE_OVERLAY";
    static final String EXTRA_SHOW_ON_HOME = "com.example.echoviz.extra.SHOW_ON_HOME";

    private static final String PREFS_NAME = "echoviz_runtime";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_SHOW_ON_HOME_RESTORE = "show_on_home_restore";

    private EchoVizRuntime() {
    }

    static boolean isActive(Context context) {
        return prefs(context).getBoolean(KEY_ACTIVE, true);
    }

    static void setActive(Context context, boolean active) {
        prefs(context).edit().putBoolean(KEY_ACTIVE, active).apply();
    }

    static boolean consumeShowOnHomeRestore(Context context) {
        SharedPreferences prefs = prefs(context);
        boolean showOnHome = prefs.getBoolean(KEY_SHOW_ON_HOME_RESTORE, false);
        if (showOnHome) {
            prefs.edit().putBoolean(KEY_SHOW_ON_HOME_RESTORE, false).apply();
        }
        return showOnHome;
    }

    static void requestRestore(Context context) {
        requestRestore(context, false);
    }

    static void requestShortcutRestore(Context context) {
        requestRestore(context, true);
    }

    private static void requestRestore(Context context, boolean showOnHome) {
        prefs(context).edit()
                .putBoolean(KEY_ACTIVE, true)
                .putBoolean(KEY_SHOW_ON_HOME_RESTORE, showOnHome)
                .apply();
        Intent intent = new Intent(ACTION_RESTORE_OVERLAY);
        intent.setPackage(context.getPackageName());
        intent.putExtra(EXTRA_SHOW_ON_HOME, showOnHome);
        context.sendBroadcast(intent);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}

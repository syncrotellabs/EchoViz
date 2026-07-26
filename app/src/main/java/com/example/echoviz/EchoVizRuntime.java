package com.example.echoviz;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

final class EchoVizRuntime {
    static final String ACTION_RESTORE_OVERLAY = "com.example.echoviz.action.RESTORE_OVERLAY";

    private static final String PREFS_NAME = "echoviz_runtime";
    private static final String KEY_ACTIVE = "active";

    private EchoVizRuntime() {
    }

    static boolean isActive(Context context) {
        return prefs(context).getBoolean(KEY_ACTIVE, true);
    }

    static void setActive(Context context, boolean active) {
        prefs(context).edit().putBoolean(KEY_ACTIVE, active).apply();
    }

    static void requestRestore(Context context) {
        setActive(context, true);
        Intent intent = new Intent(ACTION_RESTORE_OVERLAY);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}

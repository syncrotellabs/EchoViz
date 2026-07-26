package com.syncrotellabs.echoviz;

import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

final class SetupStatus {
    private SetupStatus() {
    }

    static boolean isComplete(Context context) {
        return isAccessibilityServiceEnabled(context) && Settings.System.canWrite(context);
    }

    static boolean isAccessibilityServiceEnabled(Context context) {
        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (TextUtils.isEmpty(enabledServices)) {
            return false;
        }

        String expected = context.getPackageName() + "/" + EchoVizAccessibilityService.class.getName();
        return enabledServices.toLowerCase().contains(expected.toLowerCase());
    }
}

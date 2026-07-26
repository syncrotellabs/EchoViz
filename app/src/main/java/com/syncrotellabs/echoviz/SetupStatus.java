package com.syncrotellabs.echoviz;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;

import java.util.List;

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

    static boolean hasTextToSpeechEngine(Context context) {
        Intent intent = new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE);
        List<ResolveInfo> services = context.getPackageManager().queryIntentServices(intent, 0);
        return services != null && !services.isEmpty();
    }
}

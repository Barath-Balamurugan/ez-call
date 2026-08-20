package com.ezcall.onetoone;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

final class FullScreenCallAccess {
    private FullScreenCallAccess() {
    }

    static boolean isRequired() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    }

    static boolean isGranted(Context context) {
        if (!isRequired()) {
            return true;
        }
        NotificationManager notificationManager =
                context.getSystemService(NotificationManager.class);
        return notificationManager != null && notificationManager.canUseFullScreenIntent();
    }

    static void openSettings(Context context) {
        if (!isRequired()) {
            return;
        }
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:" + context.getPackageName())
        );
        addNewTaskFlagIfNeeded(context, intent);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Intent fallback = new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + context.getPackageName())
            );
            addNewTaskFlagIfNeeded(context, fallback);
            context.startActivity(fallback);
        }
    }

    private static void addNewTaskFlagIfNeeded(Context context, Intent intent) {
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
    }
}

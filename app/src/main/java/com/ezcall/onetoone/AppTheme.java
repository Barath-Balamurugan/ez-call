package com.ezcall.onetoone;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.view.View;

final class AppTheme {
    private AppTheme() {
    }

    static boolean isLight(Context context) {
        return AppSettings.THEME_LIGHT.equals(AppSettings.themePreference(context));
    }

    static int color(Context context, String darkToken) {
        String accentToken = AppThemePalette.accentToken(
                AppSettings.accentPreference(context),
                darkToken
        );
        String resolved = isLight(context)
                ? AppThemePalette.lightToken(accentToken)
                : accentToken;
        return Color.parseColor(resolved);
    }

    static int primaryText(Context context) {
        return color(context, "#FFFFFF");
    }

    static void applyWindow(Activity activity) {
        boolean light = isLight(activity);
        activity.getWindow().setStatusBarColor(color(activity, "#070812"));
        activity.getWindow().setNavigationBarColor(color(activity, "#05060C"));

        int flags = 0;
        if (light) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        activity.getWindow().getDecorView().setSystemUiVisibility(flags);
    }
}

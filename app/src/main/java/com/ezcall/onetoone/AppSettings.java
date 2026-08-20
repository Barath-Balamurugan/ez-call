package com.ezcall.onetoone;

import android.Manifest;
import android.app.LocaleManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class AppSettings {
    static final String LANGUAGE_SYSTEM = "system";
    static final String LANGUAGE_ENGLISH = "en";
    static final String THEME_DARK = "dark";
    static final String THEME_LIGHT = "light";

    private static final String PREFS_NAME = "ez_call_settings";
    private static final String KEY_LANGUAGE = "language_preference";
    private static final String KEY_THEME = "theme_preference";
    private static final String KEY_ACCENT = "accent_preference";
    private static final String KEY_SECURITY_MODE = "security_mode_";
    private static final String KEY_PRIORITY_CONTACTS = "priority_contacts_";

    private AppSettings() {
    }

    static boolean isSecurityModeEnabled(Context context) {
        return preferences(context).getBoolean(KEY_SECURITY_MODE + accountScope(context), false);
    }

    static void setSecurityModeEnabled(Context context, boolean enabled) {
        preferences(context).edit()
                .putBoolean(KEY_SECURITY_MODE + accountScope(context), enabled)
                .apply();
    }

    static Set<String> priorityContacts(Context context) {
        Set<String> stored = preferences(context).getStringSet(
                KEY_PRIORITY_CONTACTS + accountScope(context),
                Collections.emptySet()
        );
        return stored == null ? Collections.emptySet() : new HashSet<>(stored);
    }

    static boolean isPriorityContact(Context context, String phoneNumber) {
        return priorityContacts(context).contains(FirebaseCallRepository.normalizePhoneNumber(phoneNumber));
    }

    static void setPriorityContact(Context context, String phoneNumber, boolean priority) {
        String normalizedPhone = FirebaseCallRepository.normalizePhoneNumber(phoneNumber);
        if (normalizedPhone.isEmpty()) {
            return;
        }
        Set<String> contacts = priorityContacts(context);
        if (priority) {
            contacts.add(normalizedPhone);
        } else {
            contacts.remove(normalizedPhone);
        }
        preferences(context).edit()
                .putStringSet(KEY_PRIORITY_CONTACTS + accountScope(context), contacts)
                .apply();
    }

    static boolean shouldAllowIncomingCall(Context context, String callerPhoneNumber) {
        if (!isSecurityModeEnabled(context)) {
            return true;
        }
        boolean hasContactsPermission = context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
        if (!hasContactsPermission) {
            return false;
        }
        boolean callerIsInContacts = DeviceContactNumbers.contains(
                DeviceContactNumbers.read(context.getContentResolver()),
                callerPhoneNumber
        );
        return SecurityModePolicy.isCallerAllowed(true, callerIsInContacts);
    }

    static boolean shouldAllowIncomingCall(
            Context context,
            String callerPhoneNumber,
            Set<String> knownDeviceContacts
    ) {
        boolean callerIsInContacts = DeviceContactNumbers.contains(
                knownDeviceContacts == null ? Collections.emptySet() : knownDeviceContacts,
                callerPhoneNumber
        );
        return SecurityModePolicy.isCallerAllowed(
                isSecurityModeEnabled(context),
                callerIsInContacts
        );
    }

    static String languagePreference(Context context) {
        return preferences(context).getString(KEY_LANGUAGE, LANGUAGE_SYSTEM);
    }

    static String languageLabel(Context context) {
        return LANGUAGE_ENGLISH.equals(languagePreference(context))
                ? "English"
                : "System default";
    }

    static void setLanguagePreference(Context context, String languageTag) {
        String resolved = LANGUAGE_ENGLISH.equals(languageTag)
                ? LANGUAGE_ENGLISH
                : LANGUAGE_SYSTEM;
        preferences(context).edit().putString(KEY_LANGUAGE, resolved).apply();
        applyLanguage(context, resolved);
    }

    static String themePreference(Context context) {
        return preferences(context).getString(KEY_THEME, THEME_DARK);
    }

    static String themeLabel(Context context) {
        return THEME_LIGHT.equals(themePreference(context)) ? "Light" : "Dark";
    }

    static void setThemePreference(Context context, String theme) {
        String resolved = THEME_LIGHT.equals(theme) ? THEME_LIGHT : THEME_DARK;
        preferences(context).edit().putString(KEY_THEME, resolved).apply();
    }

    static String accentPreference(Context context) {
        return AppThemePalette.ACCENT_BRAND;
    }

    static String accentLabel(Context context) {
        return AppThemePalette.accentLabel(accentPreference(context));
    }

    static void setAccentPreference(Context context, String accent) {
        preferences(context).edit()
                .putString(KEY_ACCENT, AppThemePalette.ACCENT_BRAND)
                .apply();
    }

    static void applyLanguage(Context context) {
        applyLanguage(context, languagePreference(context));
    }

    private static void applyLanguage(Context context, String languageTag) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocaleManager localeManager = context.getSystemService(LocaleManager.class);
            if (localeManager != null) {
                localeManager.setApplicationLocales(
                        LANGUAGE_ENGLISH.equals(languageTag)
                                ? LocaleList.forLanguageTags(LANGUAGE_ENGLISH)
                                : LocaleList.getEmptyLocaleList()
                );
            }
            return;
        }

        Locale locale = LANGUAGE_ENGLISH.equals(languageTag)
                ? Locale.ENGLISH
                : systemLocale();
        Locale.setDefault(locale);
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(locale);
        context.getResources().updateConfiguration(
                configuration,
                context.getResources().getDisplayMetrics()
        );
    }

    private static Locale systemLocale() {
        LocaleList locales = android.content.res.Resources.getSystem()
                .getConfiguration()
                .getLocales();
        return locales.isEmpty() ? Locale.ENGLISH : locales.get(0);
    }

    private static String accountScope(Context context) {
        FirebaseUser user = FirebaseCallRepository.isConfigured(context)
                ? FirebaseAuth.getInstance().getCurrentUser()
                : null;
        if (user != null && !user.getUid().trim().isEmpty()) {
            return user.getUid();
        }
        String localPhone = FirebaseCallRepository.normalizePhoneNumber(
                FirebaseCallRepository.currentPhoneNumberOrFallback(context)
        );
        return localPhone.isEmpty() ? "device" : localPhone;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}

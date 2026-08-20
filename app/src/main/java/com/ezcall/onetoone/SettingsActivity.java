package com.ezcall.onetoone;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends Activity {
    private static final int REQUEST_CONTACTS = 301;

    private final ExecutorService contactsExecutor = Executors.newSingleThreadExecutor();
    private final List<FirebaseCallRepository.UserProfile> registeredProfiles = new ArrayList<>();
    private ListenerRegistration usersRegistration;
    private TextView prioritySummary;
    private TextView languageValue;
    private TextView appearanceValue;
    private TextView fullScreenCallAccessValue;
    private Set<String> deviceContactNumbers = Collections.emptySet();
    private Map<String, String> deviceContactNames = Collections.emptyMap();
    private boolean usersLoaded;
    private boolean deviceContactsLoaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppTheme.applyWindow(this);
        buildUi();
        loadPriorityContactChoices();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateFullScreenCallAccessValue();
    }

    @Override
    protected void onDestroy() {
        if (usersRegistration != null) {
            usersRegistration.remove();
            usersRegistration = null;
        }
        contactsExecutor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackground(screenBackground());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(38), dp(24), dp(36));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("Settings", 36, "#FFFFFF", Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        TextView done = text("Done", 15, "#FFFFFF", Typeface.BOLD);
        done.setGravity(Gravity.CENTER);
        done.setPadding(dp(15), dp(8), dp(15), dp(8));
        done.setBackground(rounded("#267C5CFC", dp(10), "#667C5CFC", 1));
        done.setOnClickListener(view -> finish());
        header.addView(done);

        TextView subtitle = text(
                "Control who can reach you and how EZ Call behaves on this device.",
                16,
                "#8E8B99",
                Typeface.NORMAL
        );
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.setMargins(0, dp(10), 0, dp(28));
        root.addView(subtitle, subtitleParams);

        root.addView(sectionLabel("APPEARANCE"));
        LinearLayout appearanceCard = settingCard();
        appearanceCard.setClickable(true);
        appearanceCard.setFocusable(true);
        appearanceCard.setContentDescription("Choose light or dark appearance");
        appearanceCard.setOnClickListener(view -> showAppearancePicker());

        LinearLayout appearanceText = new LinearLayout(this);
        appearanceText.setOrientation(LinearLayout.VERTICAL);
        appearanceCard.addView(appearanceText, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));
        appearanceText.addView(text("App appearance", 17, "#FFFFFF", Typeface.BOLD));
        TextView appearanceDescription = text(
                "Choose a bright light theme or the original dark theme.",
                14,
                "#8E8B99",
                Typeface.NORMAL
        );
        LinearLayout.LayoutParams appearanceDescriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        appearanceDescriptionParams.setMargins(0, dp(5), dp(12), 0);
        appearanceText.addView(appearanceDescription, appearanceDescriptionParams);

        appearanceValue = text(AppSettings.themeLabel(this), 15, "#9C86FF", Typeface.BOLD);
        appearanceValue.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        appearanceCard.addView(appearanceValue, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        LinearLayout.LayoutParams appearanceCardParams = cardParams();
        appearanceCardParams.setMargins(0, 0, 0, dp(12));
        root.addView(appearanceCard, appearanceCardParams);

        root.addView(sectionLabel("CALLS"));
        LinearLayout lockScreenCard = settingCard();
        lockScreenCard.setClickable(FullScreenCallAccess.isRequired());
        lockScreenCard.setFocusable(FullScreenCallAccess.isRequired());
        lockScreenCard.setContentDescription("Configure lock-screen incoming calls");
        lockScreenCard.setOnClickListener(view -> FullScreenCallAccess.openSettings(this));

        LinearLayout lockScreenText = new LinearLayout(this);
        lockScreenText.setOrientation(LinearLayout.VERTICAL);
        lockScreenCard.addView(lockScreenText, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));
        lockScreenText.addView(text("Lock-screen calls", 17, "#FFFFFF", Typeface.BOLD));
        TextView lockScreenDescription = text(
                "Show incoming calls and answer them without unlocking this phone.",
                14,
                "#8E8B99",
                Typeface.NORMAL
        );
        LinearLayout.LayoutParams lockScreenDescriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lockScreenDescriptionParams.setMargins(0, dp(5), dp(12), 0);
        lockScreenText.addView(lockScreenDescription, lockScreenDescriptionParams);

        fullScreenCallAccessValue = text("", 14, "#9C86FF", Typeface.BOLD);
        fullScreenCallAccessValue.setGravity(Gravity.CENTER);
        fullScreenCallAccessValue.setPadding(dp(10), dp(7), dp(10), dp(7));
        lockScreenCard.addView(fullScreenCallAccessValue, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(lockScreenCard, cardParams());
        updateFullScreenCallAccessValue();

        root.addView(sectionLabel("PRIVACY"));
        LinearLayout securityCard = settingCard();
        LinearLayout securityText = new LinearLayout(this);
        securityText.setOrientation(LinearLayout.VERTICAL);
        securityCard.addView(securityText, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));
        securityText.addView(text("Security Mode", 17, "#FFFFFF", Typeface.BOLD));
        TextView securityDescription = text(
                "Only callers saved in your phone contacts can ring this device. Other calls are declined automatically.",
                14,
                "#8E8B99",
                Typeface.NORMAL
        );
        LinearLayout.LayoutParams securityDescriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        securityDescriptionParams.setMargins(0, dp(5), dp(12), 0);
        securityText.addView(securityDescription, securityDescriptionParams);

        Switch securitySwitch = new Switch(this);
        securitySwitch.setChecked(AppSettings.isSecurityModeEnabled(this));
        securitySwitch.setContentDescription("Security Mode");
        securitySwitch.setThumbTintList(switchThumbColors());
        securitySwitch.setTrackTintList(switchTrackColors());
        securitySwitch.setOnCheckedChangeListener((button, checked) -> {
            AppSettings.setSecurityModeEnabled(this, checked);
            updatePrioritySummary();
        });
        securityCard.addView(securitySwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(securityCard, cardParams());

        root.addView(sectionLabel("PRIORITY CONTACTS"));
        LinearLayout priorityCard = settingCard();
        priorityCard.setClickable(true);
        priorityCard.setFocusable(true);
        priorityCard.setContentDescription("Choose priority contacts");
        priorityCard.setOnClickListener(view -> showPriorityContactsPicker());

        LinearLayout priorityText = new LinearLayout(this);
        priorityText.setOrientation(LinearLayout.VERTICAL);
        priorityCard.addView(priorityText, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));
        priorityText.addView(text("Choose priority contacts", 17, "#FFFFFF", Typeface.BOLD));
        TextView priorityDescription = text(
                "Selected people stay at the top of the People tab for easy calling.",
                14,
                "#8E8B99",
                Typeface.NORMAL
        );
        LinearLayout.LayoutParams priorityDescriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        priorityDescriptionParams.setMargins(0, dp(5), dp(12), 0);
        priorityText.addView(priorityDescription, priorityDescriptionParams);

        prioritySummary = text("Loading...", 14, "#9C86FF", Typeface.BOLD);
        prioritySummary.setGravity(Gravity.CENTER);
        prioritySummary.setPadding(dp(10), dp(7), dp(10), dp(7));
        prioritySummary.setBackground(rounded("#267C5CFC", dp(8), "#667C5CFC", 1));
        priorityCard.addView(prioritySummary, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(priorityCard, cardParams());

        TextView languageSection = sectionLabel("LANGUAGE");
        LinearLayout.LayoutParams languageSectionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        languageSectionParams.setMargins(0, dp(28), 0, dp(10));
        root.addView(languageSection, languageSectionParams);

        LinearLayout languageCard = settingCard();
        languageCard.setClickable(true);
        languageCard.setFocusable(true);
        languageCard.setContentDescription("Choose language preference");
        languageCard.setOnClickListener(view -> showLanguagePicker());

        LinearLayout languageText = new LinearLayout(this);
        languageText.setOrientation(LinearLayout.VERTICAL);
        languageCard.addView(languageText, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));
        languageText.addView(text("Language preference", 17, "#FFFFFF", Typeface.BOLD));
        TextView languageDescription = text(
                "Use the phone language or keep EZ Call in English.",
                14,
                "#8E8B99",
                Typeface.NORMAL
        );
        LinearLayout.LayoutParams languageDescriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        languageDescriptionParams.setMargins(0, dp(5), dp(12), 0);
        languageText.addView(languageDescription, languageDescriptionParams);

        languageValue = text(AppSettings.languageLabel(this), 15, "#9C86FF", Typeface.BOLD);
        languageValue.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        languageCard.addView(languageValue, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.addView(languageCard, cardParams());

        setContentView(scrollView);
    }

    private void updateFullScreenCallAccessValue() {
        if (fullScreenCallAccessValue == null) {
            return;
        }
        boolean granted = FullScreenCallAccess.isGranted(this);
        fullScreenCallAccessValue.setText(granted ? "Allowed" : "Allow");
        fullScreenCallAccessValue.setTextColor(color(granted ? "#4ADE80" : "#9C86FF"));
        fullScreenCallAccessValue.setBackground(rounded(
                granted ? "#2034D399" : "#267C5CFC",
                dp(8),
                granted ? "#6634D399" : "#667C5CFC",
                1
        ));
    }

    private void loadPriorityContactChoices() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            renderContactsPermissionRequired();
            return;
        }

        prioritySummary.setText("Loading...");
        contactsExecutor.execute(() -> {
            DeviceContactNumbers.Snapshot snapshot =
                    DeviceContactNumbers.readSnapshot(getContentResolver());
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                deviceContactNumbers = new HashSet<>(snapshot.phoneNumbers);
                deviceContactNames = new HashMap<>(snapshot.displayNamesByNumber);
                deviceContactsLoaded = true;
                updatePriorityContactAvailability();
            });
        });

        usersRegistration = FirebaseCallRepository.listenForRegisteredUsers(
                this,
                (profiles, error) -> {
                    usersLoaded = true;
                    registeredProfiles.clear();
                    if (error == null) {
                        registeredProfiles.addAll(profiles);
                    }
                    updatePriorityContactAvailability();
                }
        );
        if (usersRegistration == null) {
            usersLoaded = true;
            updatePriorityContactAvailability();
        }
    }

    private void updatePriorityContactAvailability() {
        if (!usersLoaded || !deviceContactsLoaded) {
            prioritySummary.setText("Loading...");
            return;
        }
        if (availablePriorityContacts().isEmpty()) {
            prioritySummary.setText("None available");
            return;
        }
        updatePrioritySummary();
    }

    private List<FirebaseCallRepository.UserProfile> availablePriorityContacts() {
        List<FirebaseCallRepository.UserProfile> available = new ArrayList<>();
        for (FirebaseCallRepository.UserProfile profile : registeredProfiles) {
            if (DeviceContactNumbers.contains(deviceContactNumbers, profile.normalizedPhoneNumber)) {
                available.add(profile);
            }
        }
        available.sort((left, right) -> localDisplayName(left)
                .compareToIgnoreCase(localDisplayName(right)));
        return available;
    }

    private void showPriorityContactsPicker() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            requestContactsPermission();
            return;
        }
        if (!usersLoaded || !deviceContactsLoaded) {
            new AlertDialog.Builder(this)
                    .setTitle("Priority contacts")
                    .setMessage("Your registered phone contacts are still loading. Try again shortly.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        List<FirebaseCallRepository.UserProfile> available = availablePriorityContacts();
        if (available.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Priority contacts")
                    .setMessage("No registered EZ Call users were found in your phone contacts.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        String[] names = new String[available.size()];
        boolean[] selected = new boolean[available.size()];
        for (int index = 0; index < available.size(); index++) {
            FirebaseCallRepository.UserProfile profile = available.get(index);
            names[index] = localDisplayName(profile);
            selected[index] = AppSettings.isPriorityContact(
                    this,
                    profile.normalizedPhoneNumber
            );
        }

        new AlertDialog.Builder(this)
                .setTitle("Choose priority contacts")
                .setMultiChoiceItems(names, selected, (dialog, which, checked) ->
                        selected[which] = checked)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    for (int index = 0; index < available.size(); index++) {
                        AppSettings.setPriorityContact(
                                this,
                                available.get(index).normalizedPhoneNumber,
                                selected[index]
                        );
                    }
                    updatePrioritySummary();
                })
                .show();
    }

    private String localDisplayName(FirebaseCallRepository.UserProfile profile) {
        return DeviceContactNumbers.displayName(
                deviceContactNames,
                profile.normalizedPhoneNumber,
                profile.displayName
        );
    }

    private void renderContactsPermissionRequired() {
        prioritySummary.setText("Allow access");
    }

    private void requestContactsPermission() {
        requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_CONTACTS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_CONTACTS) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            loadPriorityContactChoices();
        } else {
            renderContactsPermissionRequired();
        }
    }

    private void updatePrioritySummary() {
        int count = AppSettings.priorityContacts(this).size();
        prioritySummary.setText(count == 0
                ? "None selected"
                : count == 1 ? "1 selected" : count + " selected");
        prioritySummary.setTextColor(color("#9C86FF"));
    }

    private void showLanguagePicker() {
        String current = AppSettings.languagePreference(this);
        int checked = AppSettings.LANGUAGE_ENGLISH.equals(current) ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle("Language preference")
                .setSingleChoiceItems(
                        new String[]{"System default", "English"},
                        checked,
                        (dialog, which) -> {
                            AppSettings.setLanguagePreference(
                                    this,
                                    which == 1
                                            ? AppSettings.LANGUAGE_ENGLISH
                                            : AppSettings.LANGUAGE_SYSTEM
                            );
                            languageValue.setText(AppSettings.languageLabel(this));
                            dialog.dismiss();
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                recreate();
                            }
                        }
                )
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAppearancePicker() {
        int checked = AppSettings.THEME_LIGHT.equals(AppSettings.themePreference(this)) ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle("App appearance")
                .setSingleChoiceItems(
                        new String[]{"Dark", "Light"},
                        checked,
                        (dialog, which) -> {
                            AppSettings.setThemePreference(
                                    this,
                                    which == 1 ? AppSettings.THEME_LIGHT : AppSettings.THEME_DARK
                            );
                            dialog.dismiss();
                            recreate();
                        }
                )
                .setNegativeButton("Cancel", null)
                .show();
    }

    private LinearLayout settingCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(16), dp(12), dp(16));
        card.setBackground(rounded("#12FFFFFF", dp(12), "#22FFFFFF", 1));
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(28));
        return params;
    }

    private TextView sectionLabel(String label) {
        TextView text = text(label, 12, "#7C5CFC", Typeface.BOLD);
        text.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        text.setLayoutParams(params);
        return text;
    }

    private ColorStateList switchThumbColors() {
        return new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Color.WHITE, color("#B5B3BE")}
        );
    }

    private ColorStateList switchTrackColors() {
        return new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{color("#7C5CFC"), color("#454451")}
        );
    }

    private TextView text(String value, int sp, String color, int style) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color(color));
        text.setTypeface(Typeface.DEFAULT, style);
        text.setIncludeFontPadding(true);
        return text;
    }

    private GradientDrawable rounded(String fill, int radius, String stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(fill));
        drawable.setCornerRadius(radius);
        if (strokeDp > 0) {
            drawable.setStroke(dp(strokeDp), color(stroke));
        }
        return drawable;
    }

    private GradientDrawable oval(String fill, String stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color(fill));
        if (strokeDp > 0) {
            drawable.setStroke(dp(strokeDp), color(stroke));
        }
        return drawable;
    }

    private GradientDrawable screenBackground() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{color("#0C0D1E"), color("#080910"), color("#07080F")}
        );
    }

    private int color(String hex) {
        return AppTheme.color(this, hex);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

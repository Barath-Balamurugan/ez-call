package com.ezcall.onetoone;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_APP_PERMISSIONS = 100;
    private static final String PERMISSION_PREFERENCES = "ez_call_permissions";
    private static final String FULL_SCREEN_ACCESS_PROMPTED =
            "full_screen_call_access_prompted";
    static final String PATIENT_PHONE_NUMBER = "+1 555 010 0000";
    static final String EXTRA_NAME = "contact_name";
    static final String EXTRA_UID = "contact_uid";
    static final String EXTRA_PHONE_NUMBER = "contact_phone_number";
    static final String EXTRA_PHOTO_BASE64 = "contact_photo_base64";
    static final String EXTRA_CALL_ID = "contact_call_id";
    static final String EXTRA_INCOMING_CALL = "incoming_call";
    static final String EXTRA_AUTO_ACCEPT_INCOMING_CALL = "auto_accept_incoming_call";
    static final String EXTRA_END_CURRENT_AND_ACCEPT = "end_current_and_accept";
    static final String EXTRA_INCOMING_NOTIFICATION_ID = "incoming_notification_id";

    private ListenerRegistration usersRegistration;
    private ListenerRegistration callHistoryRegistration;
    private LinearLayout contactsContainer;
    private LinearLayout navigationBar;
    private ScrollView mainScrollView;
    private EditText searchInput;
    private TextView signedInText;
    private String activePhoneNumber = "";
    private final List<ContactLog> callLogs = new ArrayList<>();
    private final List<FirebaseCallRepository.CallHistoryEntry> callHistoryEntries =
            new ArrayList<>();
    private final List<FirebaseCallRepository.UserProfile> registeredProfiles = new ArrayList<>();
    private final Map<String, FirebaseCallRepository.CallHistoryEntry> latestCallByUid =
            new HashMap<>();
    private final Map<String, FirebaseCallRepository.CallHistoryEntry> latestCallByPhone =
            new HashMap<>();
    private final ExecutorService contactsExecutor = Executors.newSingleThreadExecutor();
    private Set<String> deviceContactNumbers = Collections.emptySet();
    private Map<String, String> deviceContactNames = Collections.emptyMap();
    private boolean firebaseProfilesLoaded;
    private boolean deviceContactsLoaded;
    private boolean loadingContacts = true;
    private boolean callHistoryLoaded;
    private int contactsLoadGeneration;
    private String currentQuery = "";
    private String contactStatusMessage = "Loading registered contacts...";
    private String callHistoryStatusMessage = "Loading call history...";
    private ContentTab activeTab = ContentTab.PEOPLE;
    private boolean fullScreenAccessDialogVisible;
    private String createdThemePreference;
    private String createdAccentPreference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createdThemePreference = AppSettings.themePreference(this);
        createdAccentPreference = AppSettings.accentPreference(this);
        if (FirebaseCallRepository.isConfigured(this) && !FirebaseCallRepository.isSignedIn(this)) {
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }

        AppTheme.applyWindow(this);

        FrameLayout page = new FrameLayout(this);
        page.setBackground(mainBackground());
        addAmbientGlows(page);

        mainScrollView = new ScrollView(this);
        mainScrollView.setBackgroundColor(Color.TRANSPARENT);
        mainScrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        mainScrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        content.addView(header());

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(8), dp(20), dp(118));
        content.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        contactsContainer = new LinearLayout(this);
        contactsContainer.setOrientation(LinearLayout.VERTICAL);
        body.addView(contactsContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        renderContacts("");

        page.addView(mainScrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        page.addView(bottomNavigation(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(88),
                Gravity.BOTTOM
        ));

        setContentView(page);
        requestAppPermissionsIfNeeded();
        startOrRefreshFirebaseListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!AppSettings.themePreference(this).equals(createdThemePreference)
                || !AppSettings.accentPreference(this).equals(createdAccentPreference)) {
            recreate();
            return;
        }
        updateSignedInText();
        maybePromptForFullScreenCallAccess();
        if (contactsContainer != null) {
            startOrRefreshFirebaseListeners();
            if (hasContactsPermission()) {
                loadDeviceContactNumbers();
            } else {
                applyContactFilter();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (usersRegistration != null) {
            usersRegistration.remove();
            usersRegistration = null;
        }
        if (callHistoryRegistration != null) {
            callHistoryRegistration.remove();
            callHistoryRegistration = null;
        }
        contactsLoadGeneration++;
        contactsExecutor.shutdownNow();
        super.onDestroy();
    }

    private View header() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(24), dp(42), dp(24), dp(22));
        header.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(topRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                0
        ));

        TextView title = text("EZ Calls", 42, "#FFFFFF", Typeface.BOLD);
        topRow.addView(title, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT
                , 1
        ));

        ImageButton profile = new ImageButton(this);
        profile.setImageResource(R.drawable.ic_person);
        profile.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        profile.setColorFilter(color("#7C5CFC"));
        profile.setPadding(dp(9), dp(9), dp(9), dp(9));
        profile.setBackgroundColor(Color.TRANSPARENT);
        profile.setClickable(true);
        profile.setFocusable(true);
        profile.setContentDescription("Open your profile");
        profile.setOnClickListener(view -> startActivity(new Intent(this, ProfileActivity.class)));

        LinearLayout.LayoutParams profileParams = new LinearLayout.LayoutParams(dp(56), dp(56));
        profileParams.setMargins(dp(14), 0, 0, 0);
        topRow.addView(profile, profileParams);

        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(dp(13), 0, dp(12), 0);
        searchBar.setBackground(rounded("#10FFFFFF", dp(8), "#18FFFFFF", 1));

        ImageView searchIcon = new ImageView(this);
        searchIcon.setImageResource(R.drawable.ic_search);
        searchIcon.setColorFilter(color("#7C5CFC"));
        searchBar.addView(searchIcon, new LinearLayout.LayoutParams(dp(20), dp(20)));

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(15);
        searchInput.setTextColor(AppTheme.primaryText(this));
        searchInput.setHintTextColor(color("#85838E"));
        searchInput.setHint("Search contacts");
        searchInput.setBackgroundColor(Color.TRANSPARENT);
        searchInput.setPadding(dp(10), 0, 0, 0);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                currentQuery = value == null ? "" : value.toString();
                renderContacts(currentQuery);
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });
        searchBar.addView(searchInput, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1
        ));

        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
        );
        searchParams.setMargins(0, dp(16), 0, 0);
        header.addView(searchBar, searchParams);
        return header;
    }

    private void updateSignedInText() {
        if (signedInText == null) {
            return;
        }
        signedInText.setText(
                FirebaseCallRepository.currentDisplayNameOrFallback(this)
                        + "  •  "
                        + FirebaseCallRepository.currentPhoneNumberOrFallback(this)
        );
    }

    private void renderContacts(String query) {
        if (activeTab == ContentTab.CALLS) {
            renderCallHistory(query);
        } else {
            renderPeople(query);
        }
    }

    private void renderPeople(String query) {
        contactsContainer.removeAllViews();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<ContactLog> priorityContacts = new ArrayList<>();
        List<ContactLog> recentContacts = new ArrayList<>();
        List<ContactLog> availableContacts = new ArrayList<>();
        for (ContactLog log : callLogs) {
            if (!matchesQuery(log, normalizedQuery)) {
                continue;
            }
            if (AppSettings.isPriorityContact(this, log.phoneNumber)) {
                priorityContacts.add(log);
            } else if (log.lastCallAtMillis > 0) {
                recentContacts.add(log);
            } else {
                availableContacts.add(log);
            }
        }
        recentContacts.sort((left, right) ->
                Long.compare(right.lastCallAtMillis, left.lastCallAtMillis));

        if (priorityContacts.isEmpty()
                && recentContacts.isEmpty()
                && availableContacts.isEmpty()) {
            String message = loadingContacts ? "Loading contacts..." : contactStatusMessage;
            if (!loadingContacts && !normalizedQuery.isEmpty()) {
                message = "No contacts found";
            }
            TextView empty = text(message, 19, "#A5A2B0", Typeface.BOLD);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(60), 0, dp(60));
            if (!hasContactsPermission()) {
                empty.setText("Allow contacts access\nto see registered people you know");
                empty.setTextColor(color("#835EFF"));
                empty.setClickable(true);
                empty.setFocusable(true);
                empty.setOnClickListener(view -> requestContactsPermissionOrOpenSettings());
            }
            contactsContainer.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            return;
        }

        renderPeopleSection("Priority contacts", priorityContacts, 0);
        renderPeopleSection("Recent calls", recentContacts, priorityContacts.size());
        renderPeopleSection(
                "Available people",
                availableContacts,
                priorityContacts.size() + recentContacts.size()
        );
    }

    private void renderPeopleSection(
            String label,
            List<ContactLog> contacts,
            int accentOffset
    ) {
        if (contacts.isEmpty()) {
            return;
        }

        TextView sectionLabel = callSectionLabel(label);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.setMargins(
                0,
                contactsContainer.getChildCount() == 0 ? 0 : dp(16),
                0,
                dp(10)
        );
        contactsContainer.addView(sectionLabel, labelParams);

        int cardHeight = contactCardHeight();
        for (int index = 0; index < contacts.size(); index += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);

            LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, cardHeight, 1);
            leftParams.setMargins(0, 0, dp(6), 0);
            row.addView(contactCard(contacts.get(index), accentOffset + index), leftParams);

            LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, cardHeight, 1);
            rightParams.setMargins(dp(6), 0, 0, 0);
            if (index + 1 < contacts.size()) {
                row.addView(
                        contactCard(contacts.get(index + 1), accentOffset + index + 1),
                        rightParams
                );
            } else {
                row.addView(new View(this), rightParams);
            }

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    cardHeight
            );
            rowParams.setMargins(0, 0, 0, dp(12));
            contactsContainer.addView(row, rowParams);
        }
    }

    private void renderCallHistory(String query) {
        contactsContainer.removeAllViews();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<FirebaseCallRepository.CallHistoryEntry> matches = new ArrayList<>();
        for (FirebaseCallRepository.CallHistoryEntry entry : callHistoryEntries) {
            if (matchesCallHistoryQuery(entry, normalizedQuery)) {
                matches.add(entry);
            }
        }

        if (matches.isEmpty()) {
            String message = callHistoryLoaded ? callHistoryStatusMessage : "Loading call history...";
            if (callHistoryLoaded && !normalizedQuery.isEmpty()) {
                message = "No calls found";
            }
            TextView empty = text(message, 19, "#A5A2B0", Typeface.BOLD);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(60), 0, dp(60));
            contactsContainer.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            return;
        }

        LinearLayout.LayoutParams recentLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        recentLabelParams.setMargins(0, 0, 0, dp(10));
        TextView recentLabel = callSectionLabel("Recent calls");
        recentLabel.setLayoutParams(recentLabelParams);
        contactsContainer.addView(recentLabel);

        for (int index = 0; index < matches.size(); index++) {
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rowParams.setMargins(0, 0, 0, dp(10));
            contactsContainer.addView(callHistoryRow(matches.get(index), index), rowParams);
        }
    }

    private TextView callSectionLabel(String label) {
        TextView section = text(label, 14, "#A99AFF", Typeface.BOLD);
        section.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        section.setLayoutParams(params);
        return section;
    }

    private boolean matchesCallHistoryQuery(
            FirebaseCallRepository.CallHistoryEntry entry,
            String query
    ) {
        if (query.isEmpty()) {
            return true;
        }
        FirebaseCallRepository.UserProfile profile = profileForHistoryEntry(entry);
        String phoneNumber = historyPhoneNumber(entry, profile);
        String name = DeviceContactNumbers.displayName(
                deviceContactNames,
                phoneNumber,
                historyDisplayName(entry, profile)
        ).toLowerCase(Locale.ROOT);
        String status = historyStatusLabel(entry).toLowerCase(Locale.ROOT);
        return name.contains(query) || status.contains(query);
    }

    private View callHistoryRow(FirebaseCallRepository.CallHistoryEntry entry, int index) {
        FirebaseCallRepository.UserProfile profile = profileForHistoryEntry(entry);
        String phoneNumber = historyPhoneNumber(entry, profile);
        String name = DeviceContactNumbers.displayName(
                deviceContactNames,
                phoneNumber,
                historyDisplayName(entry, profile)
        );
        String photoBase64 = historyPhoto(entry, profile);
        String status = historyStatusLabel(entry);
        boolean unsuccessful = "declined".equals(entry.status)
                || "missed".equals(entry.status)
                || "no_answer".equals(entry.status)
                || "expired".equals(entry.status);
        String accent = unsuccessful ? "#FC5C7D" : contactAccent(index);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(13), dp(12), dp(13));
        row.setBackground(contactGlass(accent, dp(14)));
        row.setElevation(dp(2));

        ImageView photo = new ImageView(this);
        setProfileImage(photo, photoBase64);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setBackground(oval("#20202A", accent, 1));
        photo.setClipToOutline(true);
        photo.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        row.addView(photo, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        detailsParams.setMargins(dp(13), 0, dp(8), 0);
        row.addView(details, detailsParams);

        TextView nameView = text(name, 17, "#FFFFFF", Typeface.BOLD);
        nameView.setMaxLines(1);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        details.addView(nameView);

        String direction = entry.incoming ? "Incoming" : "Outgoing";
        TextView statusView = text(
                direction + " - " + status,
                14,
                unsuccessful ? "#FC5C7D" : "#A5A2B0",
                Typeface.BOLD
        );
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusParams.setMargins(0, dp(3), 0, 0);
        details.addView(statusView, statusParams);

        TextView time = text(lastCallLabel(entry.createdAtMillis), 13, "#777582", Typeface.NORMAL);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        timeParams.setMargins(0, dp(2), 0, 0);
        details.addView(time, timeParams);

        ImageView call = new ImageView(this);
        call.setImageResource(R.drawable.ic_videocam);
        call.setColorFilter(color(accent));
        call.setPadding(dp(11), dp(11), dp(11), dp(11));
        call.setBackground(callButtonGlass(accent, dp(10)));
        call.setContentDescription("Call " + name);
        row.addView(call, new LinearLayout.LayoutParams(dp(46), dp(46)));

        if (!entry.otherUid.trim().isEmpty() && !phoneNumber.trim().isEmpty()) {
            View.OnClickListener listener = view -> startCall(new ContactLog(
                    entry.otherUid,
                    name,
                    phoneNumber,
                    photoBase64,
                    entry.createdAtMillis,
                    entry.status,
                    entry.incoming
            ));
            row.setClickable(true);
            row.setFocusable(true);
            row.setContentDescription("Call " + name + " from call history");
            row.setOnClickListener(listener);
            call.setOnClickListener(listener);
        } else {
            call.setAlpha(0.35f);
        }
        return row;
    }

    private FirebaseCallRepository.UserProfile profileForHistoryEntry(
            FirebaseCallRepository.CallHistoryEntry entry
    ) {
        for (FirebaseCallRepository.UserProfile profile : registeredProfiles) {
            if (!entry.otherUid.isEmpty() && entry.otherUid.equals(profile.uid)) {
                return profile;
            }
            if (!entry.otherNormalizedPhoneNumber.isEmpty()
                    && entry.otherNormalizedPhoneNumber.equals(profile.normalizedPhoneNumber)) {
                return profile;
            }
        }
        return null;
    }

    private String historyDisplayName(
            FirebaseCallRepository.CallHistoryEntry entry,
            FirebaseCallRepository.UserProfile profile
    ) {
        if (profile != null && !profile.displayName.trim().isEmpty()) {
            return profile.displayName;
        }
        if (!entry.otherName.trim().isEmpty()) {
            return entry.otherName;
        }
        if (!entry.otherPhoneNumber.trim().isEmpty()) {
            return entry.otherPhoneNumber;
        }
        return "Unknown caller";
    }

    private String historyPhoneNumber(
            FirebaseCallRepository.CallHistoryEntry entry,
            FirebaseCallRepository.UserProfile profile
    ) {
        if (profile != null && !profile.phoneNumber.trim().isEmpty()) {
            return profile.phoneNumber;
        }
        if (!entry.otherPhoneNumber.trim().isEmpty()) {
            return entry.otherPhoneNumber;
        }
        return entry.otherNormalizedPhoneNumber.isEmpty()
                ? ""
                : "+" + entry.otherNormalizedPhoneNumber;
    }

    private String historyPhoto(
            FirebaseCallRepository.CallHistoryEntry entry,
            FirebaseCallRepository.UserProfile profile
    ) {
        if (profile != null && !profile.photoBase64.trim().isEmpty()) {
            return profile.photoBase64;
        }
        return entry.otherPhotoBase64;
    }

    private String historyStatusLabel(FirebaseCallRepository.CallHistoryEntry entry) {
        switch (entry.status) {
            case "declined":
                return "Declined";
            case "missed":
            case "expired":
                return entry.incoming ? "Missed" : "No answer";
            case "no_answer":
                return "No answer";
            case "answered":
            case "connected":
            case "ended":
                return "Completed";
            case "ringing":
            case "sent":
                return "Ringing";
            default:
                return entry.status.trim().isEmpty() ? "Video call" : entry.status;
        }
    }

    private int contactCardHeight() {
        return Math.min(dp(350), contactPhotoHeight() + dp(150));
    }

    private int contactPhotoHeight() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int availableWidth = screenWidth - dp(40) - dp(12);
        int cardWidth = Math.max(dp(132), availableWidth / 2);
        return Math.min(dp(190), Math.max(dp(138), Math.round(cardWidth * 0.92f)));
    }

    private boolean matchesQuery(ContactLog log, String query) {
        return query.isEmpty() || log.name.toLowerCase(Locale.ROOT).contains(query);
    }

    private View contactCard(ContactLog log, int index) {
        String accent = contactAccent(index);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(contactGlass(accent, dp(18)));
        card.setElevation(dp(3));
        card.setClipToOutline(true);
        card.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription("Start video call with " + log.name);
        card.setOnClickListener(view -> startCall(log));

        ImageView photo = new ImageView(this);
        setProfileImage(photo, log.photoBase64);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setBackgroundColor(withAlpha(accent, 0x16));
        photo.setContentDescription(log.name);
        card.addView(photo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                contactPhotoHeight()
        ));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setGravity(Gravity.CENTER_HORIZONTAL);
        details.setPadding(dp(12), dp(9), dp(12), dp(12));

        TextView name = text(log.name, 18, "#FFFFFF", Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setMinHeight(dp(48));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        nameParams.setMargins(0, 0, 0, dp(2));
        details.addView(name, nameParams);

        boolean missedIncomingCall = log.lastCallIncoming
                && "missed".equals(log.lastCallStatus);
        TextView lastCall = text(
                missedIncomingCall ? "Missed" : lastCallLabel(log.lastCallAtMillis),
                13,
                missedIncomingCall ? "#FC5C7D" : "#777582",
                Typeface.BOLD
        );
        lastCall.setGravity(Gravity.CENTER);
        if (missedIncomingCall) {
            lastCall.setPadding(dp(8), dp(2), dp(8), dp(2));
            lastCall.setBackground(rounded("#26FC5C7D", dp(8), "#00FC5C7D", 0));
        }
        LinearLayout.LayoutParams lastCallParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lastCallParams.setMargins(0, 0, 0, dp(8));
        details.addView(lastCall, lastCallParams);

        details.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        LinearLayout callButton = new LinearLayout(this);
        callButton.setOrientation(LinearLayout.HORIZONTAL);
        callButton.setGravity(Gravity.CENTER);
        callButton.setBackground(callButtonGlass(accent, dp(8)));
        callButton.setOnClickListener(view -> startCall(log));
        callButton.setContentDescription("Call " + log.name);

        ImageView callIcon = new ImageView(this);
        callIcon.setImageResource(R.drawable.ic_videocam);
        callIcon.setColorFilter(color(accent));
        callButton.addView(callIcon, new LinearLayout.LayoutParams(dp(18), dp(18)));

        TextView callLabel = text("Call", 15, accent, Typeface.BOLD);
        LinearLayout.LayoutParams callLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        callLabelParams.setMargins(dp(6), 0, 0, 0);
        callButton.addView(callLabel, callLabelParams);
        details.addView(callButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40)
        ));
        card.addView(details, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        return card;
    }

    private View bottomNavigation() {
        navigationBar = new LinearLayout(this);
        navigationBar.setOrientation(LinearLayout.HORIZONTAL);
        navigationBar.setGravity(Gravity.CENTER);
        navigationBar.setPadding(dp(14), dp(8), dp(14), dp(10));
        navigationBar.setBackground(darkGlass(dp(0)));
        renderBottomNavigation();
        return navigationBar;
    }

    private void renderBottomNavigation() {
        navigationBar.removeAllViews();
        navigationBar.addView(navigationItem(
                R.drawable.ic_phone,
                "Calls",
                activeTab == ContentTab.CALLS,
                view -> selectTab(ContentTab.CALLS)
        ), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        navigationBar.addView(navigationItem(
                R.drawable.ic_people,
                "People",
                activeTab == ContentTab.PEOPLE,
                view -> selectTab(ContentTab.PEOPLE)
        ), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        navigationBar.addView(navigationItem(
                R.drawable.ic_settings,
                "Settings",
                false,
                view -> startActivity(new Intent(this, SettingsActivity.class))
        ), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    private void selectTab(ContentTab tab) {
        if (activeTab == tab) {
            mainScrollView.smoothScrollTo(0, 0);
            return;
        }
        activeTab = tab;
        currentQuery = "";
        searchInput.setHint(tab == ContentTab.CALLS ? "Search call history" : "Search contacts");
        searchInput.setText("");
        renderContacts("");
        renderBottomNavigation();
        mainScrollView.scrollTo(0, 0);
    }

    private View navigationItem(
            int iconResource,
            String label,
            boolean selected,
            View.OnClickListener listener
    ) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(label);
        item.setOnClickListener(listener);

        int tint = color(selected ? "#7C5CFC" : "#666570");
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setColorFilter(tint);
        item.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));

        TextView text = text(label, 13, selected ? "#7C5CFC" : "#777681", Typeface.BOLD);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        textParams.setMargins(0, dp(3), 0, 0);
        item.addView(text, textParams);
        return item;
    }

    private String contactAccent(int index) {
        String[] accents = {"#7C5CFC", "#FC5C7D", "#5CF0FC", "#5CFC9A", "#FCC05C", "#FC7C5C"};
        return accents[Math.floorMod(index, accents.length)];
    }

    private void startCall(ContactLog log) {
        Intent intent = new Intent(this, VideoCallActivity.class);
        intent.putExtra(EXTRA_NAME, log.name);
        intent.putExtra(EXTRA_UID, log.uid);
        intent.putExtra(EXTRA_PHONE_NUMBER, log.phoneNumber);
        intent.putExtra(EXTRA_PHOTO_BASE64, log.photoBase64);
        intent.putExtra(EXTRA_CALL_ID, callIdForPhoneNumber(log.phoneNumber));
        startActivity(intent);
    }

    private void startOrRefreshFirebaseListeners() {
        String currentPhoneNumber = FirebaseCallRepository.currentPhoneNumberOrFallback(this);
        if (currentPhoneNumber.equals(activePhoneNumber)) {
            return;
        }

        activePhoneNumber = currentPhoneNumber;
        if (usersRegistration != null) {
            usersRegistration.remove();
            usersRegistration = null;
        }
        if (callHistoryRegistration != null) {
            callHistoryRegistration.remove();
            callHistoryRegistration = null;
        }
        latestCallByUid.clear();
        latestCallByPhone.clear();
        callHistoryEntries.clear();
        callHistoryLoaded = false;
        callHistoryStatusMessage = "Loading call history...";

        FirebaseCallRepository.registerDeviceForPhoneNumber(this, currentPhoneNumber);
        listenForRegisteredUsers();
        listenForCallHistory();
    }

    private void listenForRegisteredUsers() {
        usersRegistration = FirebaseCallRepository.listenForRegisteredUsers(
                this,
                (profiles, error) -> {
                    firebaseProfilesLoaded = true;
                    registeredProfiles.clear();
                    if (error != null) {
                        contactStatusMessage = "Could not load contacts. Check Firestore setup.";
                        loadingContacts = false;
                        renderContacts(currentQuery);
                        return;
                    }

                    registeredProfiles.addAll(profiles);
                    applyContactFilter();
                }
        );

        if (usersRegistration == null) {
            loadingContacts = false;
            contactStatusMessage = "Firebase is not configured, so contacts cannot load.";
            renderContacts(currentQuery);
        }
    }

    private void listenForCallHistory() {
        callHistoryRegistration = FirebaseCallRepository.listenForCallHistory(
                this,
                (history, error) -> {
                    callHistoryLoaded = true;
                    callHistoryEntries.clear();
                    latestCallByUid.clear();
                    latestCallByPhone.clear();
                    if (error != null) {
                        callHistoryStatusMessage = "Could not load call history.";
                        renderContacts(currentQuery);
                        return;
                    }
                    callHistoryEntries.addAll(history);
                    callHistoryStatusMessage = callHistoryEntries.isEmpty()
                            ? "No call history yet."
                            : "";
                    for (FirebaseCallRepository.CallHistoryEntry entry : history) {
                        keepLatestCall(latestCallByUid, entry.otherUid, entry);
                        keepLatestCall(
                                latestCallByPhone,
                                entry.otherNormalizedPhoneNumber,
                                entry
                        );
                    }
                    applyContactFilter();
                }
        );
    }

    private void keepLatestCall(
            Map<String, FirebaseCallRepository.CallHistoryEntry> target,
            String key,
            FirebaseCallRepository.CallHistoryEntry candidate
    ) {
        if (key == null || key.trim().isEmpty()) {
            return;
        }
        FirebaseCallRepository.CallHistoryEntry current = target.get(key);
        if (current == null || candidate.createdAtMillis > current.createdAtMillis) {
            target.put(key, candidate);
        }
    }

    private void requestAppPermissionsIfNeeded() {
        List<String> permissions = new ArrayList<>();
        if (!hasContactsPermission()) {
            permissions.add(Manifest.permission.READ_CONTACTS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), REQUEST_APP_PERMISSIONS);
        } else {
            loadDeviceContactNumbers();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_APP_PERMISSIONS) {
            return;
        }
        if (hasContactsPermission()) {
            loadDeviceContactNumbers();
        } else {
            deviceContactsLoaded = false;
            deviceContactNumbers = Collections.emptySet();
            deviceContactNames = Collections.emptyMap();
            applyContactFilter();
        }
        maybePromptForFullScreenCallAccess();
    }

    private void maybePromptForFullScreenCallAccess() {
        if (!FullScreenCallAccess.isRequired()
                || FullScreenCallAccess.isGranted(this)
                || fullScreenAccessDialogVisible) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (getSharedPreferences(PERMISSION_PREFERENCES, MODE_PRIVATE)
                .getBoolean(FULL_SCREEN_ACCESS_PROMPTED, false)) {
            return;
        }

        fullScreenAccessDialogVisible = true;
        getSharedPreferences(PERMISSION_PREFERENCES, MODE_PRIVATE)
                .edit()
                .putBoolean(FULL_SCREEN_ACCESS_PROMPTED, true)
                .apply();
        new AlertDialog.Builder(this)
                .setTitle("Allow calls on the lock screen")
                .setMessage(
                        "Enable Full screen alerts so EZ Call can show an incoming call "
                                + "and let you answer without unlocking the phone."
                )
                .setPositiveButton(
                        "Open settings",
                        (dialog, which) -> FullScreenCallAccess.openSettings(this)
                )
                .setNegativeButton("Not now", null)
                .setOnDismissListener(dialog -> fullScreenAccessDialogVisible = false)
                .show();
    }

    private boolean hasContactsPermission() {
        return checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    private void loadDeviceContactNumbers() {
        if (!hasContactsPermission() || contactsExecutor.isShutdown()) {
            applyContactFilter();
            return;
        }

        deviceContactsLoaded = false;
        loadingContacts = true;
        contactStatusMessage = "Loading phone contacts...";
        renderContacts(currentQuery);
        int generation = ++contactsLoadGeneration;
        contactsExecutor.execute(() -> {
            DeviceContactNumbers.Snapshot snapshot =
                    DeviceContactNumbers.readSnapshot(getContentResolver());
            runOnUiThread(() -> {
                if (generation != contactsLoadGeneration || isFinishing() || isDestroyed()) {
                    return;
                }
                deviceContactNumbers = new HashSet<>(snapshot.phoneNumbers);
                deviceContactNames = new HashMap<>(snapshot.displayNamesByNumber);
                deviceContactsLoaded = true;
                applyContactFilter();
            });
        });
    }

    private void applyContactFilter() {
        callLogs.clear();
        if (!hasContactsPermission()) {
            loadingContacts = false;
            contactStatusMessage = "Contacts access is required.";
            renderContacts(currentQuery);
            return;
        }
        if (!firebaseProfilesLoaded || !deviceContactsLoaded) {
            loadingContacts = true;
            contactStatusMessage = "Loading contacts...";
            renderContacts(currentQuery);
            return;
        }

        for (FirebaseCallRepository.UserProfile profile : registeredProfiles) {
            if (!DeviceContactNumbers.contains(deviceContactNumbers, profile.normalizedPhoneNumber)) {
                continue;
            }
            FirebaseCallRepository.CallHistoryEntry history = latestCallByUid.get(profile.uid);
            if (history == null) {
                history = latestCallByPhone.get(profile.normalizedPhoneNumber);
            }
            callLogs.add(new ContactLog(
                    profile.uid,
                    DeviceContactNumbers.displayName(
                            deviceContactNames,
                            profile.normalizedPhoneNumber,
                            profile.displayName
                    ),
                    profile.phoneNumber,
                    profile.photoBase64,
                    history == null ? 0 : history.createdAtMillis,
                    history == null ? "" : history.status,
                    history != null && history.incoming
            ));
        }
        callLogs.sort((left, right) -> left.name.compareToIgnoreCase(right.name));
        loadingContacts = false;
        contactStatusMessage = callLogs.isEmpty()
                ? "No registered EZ Call users were found in your phone contacts."
                : "";
        renderContacts(currentQuery);
    }

    private void requestContactsPermissionOrOpenSettings() {
        if (hasContactsPermission()) {
            loadDeviceContactNumbers();
            return;
        }
        if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_APP_PERMISSIONS);
            return;
        }
        Intent settingsIntent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())
        );
        startActivity(settingsIntent);
    }

    private String callIdForPhoneNumber(String phoneNumber) {
        return "memory-calls-phone-"
                + phoneNumber.replaceAll("[^0-9]", "")
                + "-"
                + System.currentTimeMillis();
    }

    private String lastCallLabel(long callAtMillis) {
        if (callAtMillis <= 0) {
            return "No calls yet";
        }

        long elapsedMillis = Math.max(0, System.currentTimeMillis() - callAtMillis);
        long elapsedMinutes = elapsedMillis / 60_000L;
        if (elapsedMinutes < 1) {
            return "Just now";
        }
        if (elapsedMinutes < 60) {
            return elapsedMinutes + " min ago";
        }

        long elapsedHours = elapsedMinutes / 60;
        if (elapsedHours < 24) {
            return elapsedHours + (elapsedHours == 1 ? " hr ago" : " hrs ago");
        }

        long elapsedDays = elapsedHours / 24;
        if (elapsedDays == 1) {
            return "Yesterday";
        }
        if (elapsedDays < 7) {
            return elapsedDays + " days ago";
        }
        return new SimpleDateFormat("MMM d", Locale.getDefault())
                .format(new Date(callAtMillis));
    }

    private void setProfileImage(ImageView imageView, String photoBase64) {
        Bitmap bitmap = ProfilePhotoUtils.decodeBase64(photoBase64);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        } else {
            imageView.setImageResource(R.drawable.ic_default_user);
        }
    }

    private TextView text(String value, int sp, String textColor, int style) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color(textColor));
        textView.setTypeface(Typeface.DEFAULT, style);
        textView.setIncludeFontPadding(true);
        return textView;
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

    private GradientDrawable contactGlass(String accent, int radius) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        withAlpha(accent, 0x14),
                        color("#08FFFFFF"),
                        color("#08FFFFFF")
                }
        );
        drawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        drawable.setGradientCenter(0.5f, 0f);
        drawable.setGradientRadius(dp(260));
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), withAlpha(accent, 0x22));
        return drawable;
    }

    private GradientDrawable callButtonGlass(String accent, int radius) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{withAlpha(accent, 0x33), withAlpha(accent, 0x18)}
        );
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), withAlpha(accent, 0x44));
        return drawable;
    }

    private GradientDrawable tintedGlass(
            String accent,
            int fillAlpha,
            int borderAlpha,
            int radius
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(withAlpha(accent, fillAlpha));
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), withAlpha(accent, borderAlpha));
        return drawable;
    }

    private GradientDrawable darkGlass(int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color("#BF05060C"));
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), color("#14FFFFFF"));
        return drawable;
    }

    private GradientDrawable mainBackground() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{color("#07080F"), color("#07080F"), color("#05060C")}
        );
    }

    private void addAmbientGlows(FrameLayout page) {
        FrameLayout.LayoutParams purpleParams = new FrameLayout.LayoutParams(
                dp(300),
                dp(300),
                Gravity.TOP | Gravity.LEFT
        );
        purpleParams.setMargins(-dp(30), dp(90), 0, 0);
        page.addView(ambientGlow("#7C5CFC"), purpleParams);

        FrameLayout.LayoutParams cyanParams = new FrameLayout.LayoutParams(
                dp(280),
                dp(280),
                Gravity.BOTTOM | Gravity.RIGHT
        );
        cyanParams.setMargins(0, 0, -dp(130), dp(70));
        page.addView(ambientGlow("#5CF0FC"), cyanParams);
    }

    private View ambientGlow(String accent) {
        View glow = new View(this);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        drawable.setGradientRadius(dp(140));
        drawable.setColors(new int[]{
                withAlpha(accent, 0x1F),
                withAlpha(accent, 0x0F),
                Color.TRANSPARENT
        });
        glow.setBackground(drawable);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            glow.setRenderEffect(RenderEffect.createBlurEffect(
                    dp(24),
                    dp(24),
                    Shader.TileMode.CLAMP
            ));
        }
        return glow;
    }

    private int withAlpha(String hex, int alpha) {
        return (color(hex) & 0x00FFFFFF) | (alpha << 24);
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

    private GradientDrawable headerBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color("#F4FFF0"));
        float radius = dp(30);
        drawable.setCornerRadii(new float[]{
                0, 0,
                0, 0,
                radius, radius,
                radius, radius
        });
        return drawable;
    }

    private GradientDrawable verticalGradient(String top, String bottom) {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{color(top), color(bottom)}
        );
    }

    private int color(String hex) {
        return AppTheme.color(this, hex);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class ContactLog {
        final String uid;
        final String name;
        final String phoneNumber;
        final String photoBase64;
        final long lastCallAtMillis;
        final String lastCallStatus;
        final boolean lastCallIncoming;

        ContactLog(
                String uid,
                String name,
                String phoneNumber,
                String photoBase64,
                long lastCallAtMillis,
                String lastCallStatus,
                boolean lastCallIncoming
        ) {
            this.uid = uid;
            this.name = name;
            this.phoneNumber = phoneNumber;
            this.photoBase64 = photoBase64;
            this.lastCallAtMillis = lastCallAtMillis;
            this.lastCallStatus = lastCallStatus;
            this.lastCallIncoming = lastCallIncoming;
        }
    }

    private enum ContentTab {
        CALLS,
        PEOPLE
    }
}

package com.ezcall.onetoone;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import io.michaelrocks.libphonenumber.android.NumberParseException;
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil;
import io.michaelrocks.libphonenumber.android.Phonenumber;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class CountryPhoneInput extends LinearLayout {
    private static final String FALLBACK_REGION = "US";

    private final TextView countryButton;
    private final EditText numberInput;
    private volatile PhoneNumberUtil phoneNumberUtil;
    private List<CountryOption> countryOptions = Collections.emptyList();
    private String selectedRegionCode;
    private String pendingPhoneNumber = "";

    CountryPhoneInput(Context context) {
        super(context);
        selectedRegionCode = preferredRegion(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(4), 0, dp(4), 0);
        setBackground(rounded("#10FFFFFF", dp(12), "#1AFFFFFF", 1));

        countryButton = new TextView(context);
        countryButton.setText("Loading...");
        countryButton.setTextSize(14);
        countryButton.setTextColor(AppTheme.primaryText(context));
        countryButton.setGravity(Gravity.CENTER);
        countryButton.setPadding(dp(8), 0, dp(8), 0);
        countryButton.setEnabled(false);
        countryButton.setContentDescription("Select country calling code");
        countryButton.setOnClickListener(view -> showCountryPicker());
        addView(countryButton, new LayoutParams(dp(104), ViewGroup.LayoutParams.MATCH_PARENT));

        View divider = new View(context);
        divider.setBackgroundColor(themeColor("#26FFFFFF"));
        addView(divider, new LayoutParams(dp(1), dp(28)));

        numberInput = new EditText(context);
        numberInput.setHint("Phone number");
        numberInput.setTextSize(16);
        numberInput.setTextColor(AppTheme.primaryText(context));
        numberInput.setHintTextColor(themeColor("#777582"));
        numberInput.setSingleLine(true);
        numberInput.setInputType(InputType.TYPE_CLASS_PHONE);
        numberInput.setPadding(dp(12), 0, dp(12), 0);
        numberInput.setBackgroundColor(Color.TRANSPARENT);
        addView(numberInput, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        Thread metadataLoader = new Thread(() -> loadCountryMetadata(context.getApplicationContext()));
        metadataLoader.setName("ez-call-phone-metadata");
        metadataLoader.start();
    }

    String internationalNumber() {
        String rawNumber = numberInput.getText().toString().trim();
        if (rawNumber.isEmpty()) {
            throw new IllegalArgumentException("Enter the phone number people should call.");
        }

        PhoneNumberUtil util = phoneNumberUtil;
        if (util == null) {
            throw new IllegalArgumentException("Country codes are still loading. Try again in a moment.");
        }

        try {
            String parsingRegion = rawNumber.startsWith("+") ? "ZZ" : selectedRegionCode;
            Phonenumber.PhoneNumber parsed = util.parse(rawNumber, parsingRegion);
            if (!util.isPossibleNumber(parsed)) {
                throw new IllegalArgumentException("Enter a possible phone number for the selected country.");
            }
            return util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException error) {
            throw new IllegalArgumentException("Enter a valid phone number for the selected country.");
        }
    }

    void setPhoneNumber(String phoneNumber) {
        pendingPhoneNumber = phoneNumber == null ? "" : phoneNumber.trim();
        numberInput.setText(pendingPhoneNumber);
        applyPendingPhoneNumber();
    }

    void clear() {
        pendingPhoneNumber = "";
        numberInput.setText("");
    }

    private void loadCountryMetadata(Context context) {
        PhoneNumberUtil util = PhoneNumberUtil.createInstance(context);
        List<CountryOption> options = new ArrayList<>();
        Locale displayLocale = Locale.getDefault();
        for (String regionCode : util.getSupportedRegions()) {
            int dialCode = util.getCountryCodeForRegion(regionCode);
            if (dialCode <= 0) {
                continue;
            }
            Locale regionLocale = new Locale.Builder().setRegion(regionCode).build();
            String countryName = regionLocale.getDisplayCountry(displayLocale);
            if (countryName == null || countryName.trim().isEmpty()) {
                countryName = regionCode;
            }
            options.add(new CountryOption(regionCode, countryName, dialCode));
        }
        Collator collator = Collator.getInstance(displayLocale);
        options.sort((left, right) -> collator.compare(left.countryName, right.countryName));

        post(() -> {
            phoneNumberUtil = util;
            countryOptions = options;
            if (findCountry(selectedRegionCode) == null) {
                selectedRegionCode = FALLBACK_REGION;
            }
            updateCountryButton();
            countryButton.setEnabled(true);
            applyPendingPhoneNumber();
        });
    }

    private void applyPendingPhoneNumber() {
        PhoneNumberUtil util = phoneNumberUtil;
        if (util == null || pendingPhoneNumber.isEmpty() || !pendingPhoneNumber.startsWith("+")) {
            return;
        }

        try {
            Phonenumber.PhoneNumber parsed = util.parse(pendingPhoneNumber, "ZZ");
            String detectedRegion = util.getRegionCodeForNumber(parsed);
            if (detectedRegion != null && findCountry(detectedRegion) != null) {
                selectedRegionCode = detectedRegion;
                updateCountryButton();
            }
            numberInput.setText(util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.NATIONAL));
            numberInput.setSelection(numberInput.length());
            pendingPhoneNumber = "";
        } catch (NumberParseException ignored) {
            // Leave legacy or incomplete values untouched so the user can edit them.
        }
    }

    private void showCountryPicker() {
        if (countryOptions.isEmpty()) {
            return;
        }

        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(14));
        panel.setBackground(rounded("#F20B0C17", dp(14), "#667C5CFC", 1));

        TextView title = new TextView(getContext());
        title.setText("Select country or region");
        title.setTextSize(20);
        title.setTextColor(AppTheme.primaryText(getContext()));
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        panel.addView(title, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        EditText search = new EditText(getContext());
        search.setHint("Search country or code");
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setTextColor(AppTheme.primaryText(getContext()));
        search.setHintTextColor(themeColor("#777582"));
        search.setPadding(dp(12), 0, dp(12), 0);
        search.setBackground(rounded("#10FFFFFF", dp(8), "#1AFFFFFF", 1));
        LayoutParams searchParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        searchParams.setMargins(0, dp(14), 0, dp(10));
        panel.addView(search, searchParams);

        CountryAdapter adapter = new CountryAdapter(countryOptions);
        ListView countryList = new ListView(getContext());
        countryList.setAdapter(adapter);
        countryList.setDivider(new ColorDrawable(themeColor("#18FFFFFF")));
        countryList.setDividerHeight(dp(1));
        countryList.setOnItemClickListener((parent, view, position, id) -> {
            CountryOption selected = adapter.getItem(position);
            if (selected != null) {
                selectedRegionCode = selected.regionCode;
                pendingPhoneNumber = "";
                updateCountryButton();
            }
            dialog.dismiss();
            numberInput.requestFocus();
        });
        panel.addView(countryList, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(420)
        ));

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                adapter.filter(text == null ? "" : text.toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.setOnShowListener(unused -> {
            Window shownWindow = dialog.getWindow();
            if (shownWindow != null) {
                shownWindow.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
    }

    private void updateCountryButton() {
        CountryOption selected = findCountry(selectedRegionCode);
        if (selected == null) {
            countryButton.setText(selectedRegionCode);
            return;
        }
        countryButton.setText(selected.regionCode + "  +" + selected.dialCode);
        countryButton.setContentDescription(
                selected.countryName + " country code plus " + selected.dialCode
        );
    }

    private CountryOption findCountry(String regionCode) {
        for (CountryOption option : countryOptions) {
            if (option.regionCode.equalsIgnoreCase(regionCode)) {
                return option;
            }
        }
        return null;
    }

    private String preferredRegion(Context context) {
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            String simRegion = normalizeRegion(telephonyManager.getSimCountryIso());
            if (!simRegion.isEmpty()) {
                return simRegion;
            }
            String networkRegion = normalizeRegion(telephonyManager.getNetworkCountryIso());
            if (!networkRegion.isEmpty()) {
                return networkRegion;
            }
        }
        String localeRegion = normalizeRegion(Locale.getDefault().getCountry());
        return localeRegion.isEmpty() ? FALLBACK_REGION : localeRegion;
    }

    private String normalizeRegion(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.US);
    }

    private GradientDrawable rounded(String fill, int radius, String stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(themeColor(fill));
        drawable.setCornerRadius(radius);
        if (strokeDp > 0) {
            drawable.setStroke(dp(strokeDp), themeColor(stroke));
        }
        return drawable;
    }

    private int themeColor(String token) {
        return AppTheme.color(getContext(), token);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class CountryOption {
        final String regionCode;
        final String countryName;
        final int dialCode;

        CountryOption(String regionCode, String countryName, int dialCode) {
            this.regionCode = regionCode;
            this.countryName = countryName;
            this.dialCode = dialCode;
        }

        String searchText() {
            return (countryName + " " + regionCode + " +" + dialCode).toLowerCase(Locale.ROOT);
        }

        String displayText() {
            return countryName + " (" + regionCode + ")   +" + dialCode;
        }
    }

    private final class CountryAdapter extends BaseAdapter {
        private final List<CountryOption> allOptions;
        private final List<CountryOption> visibleOptions;

        CountryAdapter(List<CountryOption> options) {
            allOptions = new ArrayList<>(options);
            visibleOptions = new ArrayList<>(options);
        }

        void filter(String query) {
            String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
            visibleOptions.clear();
            if (normalizedQuery.isEmpty()) {
                visibleOptions.addAll(allOptions);
            } else {
                for (CountryOption option : allOptions) {
                    if (option.searchText().contains(normalizedQuery)) {
                        visibleOptions.add(option);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return visibleOptions.size();
        }

        @Override
        public CountryOption getItem(int position) {
            return position >= 0 && position < visibleOptions.size()
                    ? visibleOptions.get(position)
                    : null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView row = convertView instanceof TextView
                    ? (TextView) convertView
                    : new TextView(getContext());
            CountryOption option = getItem(position);
            row.setText(option == null ? "" : option.displayText());
            row.setTextSize(15);
            row.setTextColor(AppTheme.primaryText(getContext()));
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), 0, dp(12), 0);
            row.setMinHeight(dp(52));
            return row;
        }
    }
}

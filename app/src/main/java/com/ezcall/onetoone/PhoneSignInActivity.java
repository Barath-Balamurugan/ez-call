package com.ezcall.onetoone;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class PhoneSignInActivity extends Activity {
    private CountryPhoneInput phoneInput;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppTheme.applyWindow(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(22), dp(24), dp(22), dp(24));
        root.setBackgroundColor(color("#07080F"));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24), dp(26), dp(24), dp(24));
        card.setBackground(rounded("#111827", dp(24), "#2B3545", 1));
        card.setElevation(dp(5));
        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView brand = text("EZ Call", 17, "#F95830", Typeface.BOLD);
        brand.setGravity(Gravity.CENTER);
        card.addView(brand);

        TextView title = text("Set this phone", 31, "#FFFFFF", Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(0, dp(6), 0, 0);
        card.addView(title, titleParams);

        TextView body = text(
                "Enter the number this device should answer calls for.",
                18,
                "#C7D2E1",
                Typeface.NORMAL
        );
        body.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bodyParams.setMargins(0, dp(8), 0, dp(24));
        card.addView(body, bodyParams);

        TextView fieldLabel = text("Phone number", 16, "#E5E7EB", Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.setMargins(0, 0, 0, dp(8));
        card.addView(fieldLabel, labelParams);

        phoneInput = new CountryPhoneInput(this);
        card.addView(phoneInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(62)
        ));

        Button continueButton = primaryButton("Use this phone");
        continueButton.setOnClickListener(view -> continueWithPhoneNumber());
        LinearLayout.LayoutParams continueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(62)
        );
        continueParams.setMargins(0, dp(16), 0, dp(16));
        card.addView(continueButton, continueParams);

        TextView testHint = text(
                "Testing tip: use +15550100000 on one device and +15550101001 on the other.",
                15,
                "#C7D2E1",
                Typeface.NORMAL
        );
        testHint.setGravity(Gravity.CENTER);
        testHint.setPadding(dp(12), dp(12), dp(12), dp(12));
        testHint.setBackground(rounded("#0F1F1A", dp(14), "#1C5F43", 1));
        card.addView(testHint);

        statusText = text("", 16, "#C7D2E1", Typeface.NORMAL);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusParams.setMargins(0, dp(14), 0, 0);
        card.addView(statusText, statusParams);

        setContentView(root);
    }

    private void continueWithPhoneNumber() {
        String phoneNumber;
        try {
            phoneNumber = phoneInput.internationalNumber();
        } catch (IllegalArgumentException error) {
            statusText.setText(error.getMessage());
            return;
        }

        FirebaseCallRepository.saveLocalPhoneNumber(this, phoneNumber);
        FirebaseCallRepository.registerDeviceForPhoneNumber(this, phoneNumber);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(20);
        button.setTextColor(color("#111111"));
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(rounded("#F95830", dp(18), "#FF7958", 1));
        button.setElevation(dp(2));
        return button;
    }

    private TextView text(String value, int sp, String color, int style) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color(color));
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

    private int color(String hex) {
        return AppTheme.color(this, hex);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

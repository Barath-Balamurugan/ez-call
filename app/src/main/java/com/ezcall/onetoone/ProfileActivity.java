package com.ezcall.onetoone;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ProfileActivity extends Activity {
    private static final int PICK_PROFILE_PHOTO_REQUEST = 13;
    private static final int PROFILE_PHOTO_MAX_SIZE = 640;
    private static final int PROFILE_PHOTO_JPEG_QUALITY = 72;

    private EditText nameInput;
    private CountryPhoneInput phoneInput;
    private TextView emailText;
    private TextView statusText;
    private ImageView photoPreview;
    private Button saveButton;
    private String selectedPhotoBase64 = "";
    private PhoneVerificationPrompt phoneVerificationPrompt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppTheme.applyWindow(this);
        buildUi();
        loadProfile();
    }

    @Override
    protected void onDestroy() {
        if (phoneVerificationPrompt != null) {
            phoneVerificationPrompt.dismiss();
            phoneVerificationPrompt = null;
        }
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackground(screenBackground());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(24), dp(38), dp(24), dp(20));
        header.setBackgroundColor(Color.TRANSPARENT);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(headerRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView brand = text("EZ CALL", 13, "#7C5CFC", Typeface.BOLD);
        brand.setLetterSpacing(0.12f);
        headerRow.addView(brand, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        TextView close = text("Done", 15, "#FFFFFF", Typeface.BOLD);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(15), dp(8), dp(15), dp(8));
        close.setBackground(rounded("#267C5CFC", dp(10), "#667C5CFC", 1));
        close.setOnClickListener(view -> finish());
        headerRow.addView(close);

        TextView title = text("Your profile", 36, "#FFFFFF", Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(0, dp(14), 0, 0);
        header.addView(title, titleParams);

        TextView subtitle = text(
                "Keep your calling details accurate so people you know can reach you.",
                16,
                "#8E8B99",
                Typeface.NORMAL
        );
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.setMargins(0, dp(8), 0, 0);
        header.addView(subtitle, subtitleParams);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(18), dp(24), dp(34));
        root.addView(form, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        photoPreview = new ImageView(this);
        photoPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photoPreview.setBackground(rounded("#0F101C", dp(20), "#667C5CFC", 1));
        photoPreview.setClipToOutline(true);
        photoPreview.setElevation(dp(4));
        LinearLayout.LayoutParams photoParams = new LinearLayout.LayoutParams(dp(140), dp(140));
        photoParams.gravity = Gravity.CENTER_HORIZONTAL;
        form.addView(photoPreview, photoParams);

        Button choosePhotoButton = secondaryButton("Change profile photo");
        choosePhotoButton.setOnClickListener(view -> chooseProfilePhoto());
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        chooseParams.setMargins(0, dp(16), 0, dp(24));
        form.addView(choosePhotoButton, chooseParams);

        nameInput = input("Full name", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        form.addView(fieldGroup("Full name", nameInput));

        phoneInput = new CountryPhoneInput(this);
        form.addView(fieldGroup("Phone number for calls", phoneInput));

        TextView emailLabel = text("Login email", 13, "#CBC8D4", Typeface.BOLD);
        LinearLayout.LayoutParams emailLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        emailLabelParams.setMargins(0, 0, 0, dp(7));
        form.addView(emailLabel, emailLabelParams);

        emailText = text("", 16, "#D8D5E0", Typeface.BOLD);
        emailText.setPadding(dp(15), 0, dp(15), 0);
        emailText.setGravity(Gravity.CENTER_VERTICAL);
        emailText.setBackground(rounded("#10FFFFFF", dp(12), "#1AFFFFFF", 1));
        form.addView(emailText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        ));

        TextView emailHint = text(
                "Your login email is managed securely through Firebase Authentication.",
                14,
                "#8E8B99",
                Typeface.NORMAL
        );
        LinearLayout.LayoutParams emailHintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        emailHintParams.setMargins(0, dp(8), 0, dp(18));
        form.addView(emailHint, emailHintParams);

        statusText = text("", 14, "#D8D5E0", Typeface.BOLD);
        statusText.setGravity(Gravity.CENTER);
        form.addView(statusText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        saveButton = primaryButton("Save changes");
        saveButton.setOnClickListener(view -> saveProfile());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        saveParams.setMargins(0, dp(12), 0, dp(30));
        form.addView(saveButton, saveParams);

        TextView accountLabel = text("ACCOUNT", 12, "#7C5CFC", Typeface.BOLD);
        accountLabel.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams accountLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        accountLabelParams.setMargins(0, 0, 0, dp(10));
        form.addView(accountLabel, accountLabelParams);

        Button signOutButton = dangerButton("Sign out");
        signOutButton.setOnClickListener(view -> signOut());
        LinearLayout.LayoutParams signOutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        signOutParams.setMargins(0, dp(12), 0, 0);
        form.addView(signOutButton, signOutParams);

        setContentView(scrollView);
    }
    private void loadProfile() {
        FirebaseCallRepository.UserProfile cachedProfile = FirebaseCallRepository.cachedProfile(this);
        nameInput.setText(cachedProfile.displayName);
        phoneInput.setPhoneNumber(cachedProfile.phoneNumber);
        emailText.setText(cachedProfile.email.isEmpty() ? "No email found" : cachedProfile.email);
        selectedPhotoBase64 = cachedProfile.photoBase64;
        setProfileImage(selectedPhotoBase64);

        FirebaseUser currentUser = FirebaseCallRepository.isConfigured(this)
                ? FirebaseAuth.getInstance().getCurrentUser()
                : null;
        if (currentUser == null) {
            statusText.setText("Log in to edit your profile.");
            saveButton.setEnabled(false);
            return;
        }

        FirebaseCallRepository.fetchAndCacheProfileForCurrentUser(this, new FirebaseCallRepository.ProfileLoadListener() {
            @Override
            public void onProfileLoaded(FirebaseCallRepository.UserProfile profile) {
                nameInput.setText(profile.displayName);
                phoneInput.setPhoneNumber(profile.phoneNumber);
                emailText.setText(profile.email.isEmpty() ? "No email found" : profile.email);
                selectedPhotoBase64 = profile.photoBase64;
                setProfileImage(selectedPhotoBase64);
            }

            @Override
            public void onMissingProfile() {
                statusText.setText("Profile not found. Save to create it.");
            }

            @Override
            public void onFailure(Exception error) {
                statusText.setText(readableError(error));
            }
        });
    }

    private void saveProfile() {
        String displayName = clean(nameInput);

        if (displayName.isEmpty()) {
            statusText.setText("Enter your name.");
            return;
        }
        String phoneNumber = internationalPhoneNumberOrNull();
        if (phoneNumber == null) {
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            setBusy(false, "You are not logged in.");
            return;
        }

        // The users/{phone} document is keyed by phone number and Firestore only accepts it
        // from an account whose verified phone number matches. Moving to a new number must
        // therefore update Firebase Auth first, or the write is rejected.
        if (PhoneVerificationPrompt.sameNumber(phoneNumber, user.getPhoneNumber())) {
            writeProfile(user, displayName, phoneNumber);
            return;
        }
        verifyNewPhoneNumberThenSave(user, displayName, phoneNumber);
    }

    private void verifyNewPhoneNumberThenSave(
            FirebaseUser user,
            String displayName,
            String phoneNumber
    ) {
        if (phoneVerificationPrompt != null) {
            phoneVerificationPrompt.dismiss();
        }
        phoneVerificationPrompt = new PhoneVerificationPrompt(
                this,
                new PhoneVerificationPrompt.Callbacks() {
                    @Override
                    public void onPhoneCredentialReady(
                            PhoneAuthCredential credential,
                            PhoneVerificationPrompt.Result result
                    ) {
                        applyVerifiedPhoneNumber(
                                user,
                                displayName,
                                phoneNumber,
                                credential,
                                result
                        );
                    }

                    @Override
                    public void onPhoneVerificationStatus(String message) {
                        setBusy(true, message);
                    }

                    @Override
                    public void onPhoneVerificationCancelled() {
                        setBusy(false, "Phone number not changed.");
                    }

                    @Override
                    public void onPhoneVerificationFailed(Exception error) {
                        setBusy(false, readablePhoneVerificationError(error));
                    }
                }
        );
        phoneVerificationPrompt.start(phoneNumber);
    }

    private void applyVerifiedPhoneNumber(
            FirebaseUser user,
            String displayName,
            String phoneNumber,
            PhoneAuthCredential credential,
            PhoneVerificationPrompt.Result result
    ) {
        if (credential == null) {
            result.accept();
            writeProfile(user, displayName, phoneNumber);
            return;
        }
        // updatePhoneNumber replaces an existing phone provider; accounts that never had
        // one (an older Google sign-in) need the provider linked instead.
        boolean hasPhoneProvider = user.getProviderData().stream()
                .anyMatch(info -> PhoneAuthProvider.PROVIDER_ID.equals(info.getProviderId()));
        Task<?> update = hasPhoneProvider
                ? user.updatePhoneNumber(credential)
                : user.linkWithCredential(credential);
        update.addOnSuccessListener(unused -> {
                    result.accept();
                    writeProfile(user, displayName, phoneNumber);
                })
                .addOnFailureListener(result::reject);
    }

    private void writeProfile(FirebaseUser user, String displayName, String phoneNumber) {
        setBusy(true, "Saving profile...");
        user.updateProfile(new UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build());

        FirebaseCallRepository.updateCurrentUserProfile(
                this,
                displayName,
                phoneNumber,
                selectedPhotoBase64,
                new FirebaseCallRepository.ProfileSaveListener() {
                    @Override
                    public void onSuccess() {
                        setBusy(false, "Profile updated.");
                    }

                    @Override
                    public void onFailure(Exception error) {
                        setBusy(false, readableError(error));
                    }
                }
        );
    }

    private String readablePhoneVerificationError(Exception error) {
        if (error instanceof FirebaseAuthUserCollisionException) {
            return "That phone number is already registered to another EZ Call account.";
        }
        if (error instanceof FirebaseTooManyRequestsException) {
            return "Too many verification attempts. Try again later.";
        }
        if (error instanceof FirebaseAuthRecentLoginRequiredException) {
            return "Sign out and back in, then change your phone number.";
        }
        return readableError(error);
    }

    private void signOut() {
        signOutAndOpenAuth("Signed out.", "");
    }

    private void signOutAndOpenAuth(String message, String email) {
        FirebaseCallRepository.signOut(this);
        GoogleSignInState.clear(this, () -> {
            // Credential cleanup is best-effort and must not delay navigation.
        });

        Intent intent = new Intent(this, AuthActivity.class);
        intent.putExtra(AuthActivity.EXTRA_OPEN_SIGN_IN, true);
        intent.putExtra(AuthActivity.EXTRA_STATUS_MESSAGE, message);
        if (email != null && !email.isEmpty()) {
            intent.putExtra(AuthActivity.EXTRA_EMAIL, email);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_PROFILE_PHOTO_REQUEST || resultCode != RESULT_OK || data == null) {
            return;
        }

        Uri imageUri = data.getData();
        if (imageUri == null) {
            statusText.setText("Could not read that photo.");
            return;
        }

        try {
            Bitmap bitmap = loadScaledBitmap(imageUri);
            selectedPhotoBase64 = encodePhoto(bitmap);
            photoPreview.setImageBitmap(bitmap);
            statusText.setText("Photo updated. Tap Save changes.");
        } catch (IOException error) {
            statusText.setText("Could not load that photo. Try another image.");
        }
    }

    private void chooseProfilePhoto() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Choose profile photo"), PICK_PROFILE_PHOTO_REQUEST);
    }

    private void setBusy(boolean busy, String status) {
        saveButton.setEnabled(!busy);
        saveButton.setAlpha(busy ? 0.65f : 1f);
        statusText.setText(status);
    }

    private void setProfileImage(String photoBase64) {
        Bitmap bitmap = bitmapFromBase64(photoBase64);
        if (bitmap != null) {
            photoPreview.setImageBitmap(bitmap);
        } else {
            photoPreview.setImageResource(R.drawable.ic_default_user);
        }
    }

    private Bitmap bitmapFromBase64(String photoBase64) {
        return ProfilePhotoUtils.decodeBase64(photoBase64);
    }

    private Bitmap loadScaledBitmap(Uri imageUri) throws IOException {
        Bitmap original;
        try (InputStream inputStream = getContentResolver().openInputStream(imageUri)) {
            if (inputStream == null) {
                throw new IOException("Image stream was empty.");
            }
            original = BitmapFactory.decodeStream(inputStream);
        }

        if (original == null) {
            throw new IOException("Bitmap decode failed.");
        }

        int width = original.getWidth();
        int height = original.getHeight();
        int largestSide = Math.max(width, height);
        if (largestSide <= PROFILE_PHOTO_MAX_SIZE) {
            return original;
        }

        float scale = PROFILE_PHOTO_MAX_SIZE / (float) largestSide;
        int scaledWidth = Math.max(1, Math.round(width * scale));
        int scaledHeight = Math.max(1, Math.round(height * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(original, scaledWidth, scaledHeight, true);
        if (scaled != original) {
            original.recycle();
        }
        return scaled;
    }

    private String encodePhoto(Bitmap bitmap) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, PROFILE_PHOTO_JPEG_QUALITY, outputStream);
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
    }

    private LinearLayout fieldGroup(String label, View input) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        groupParams.setMargins(0, 0, 0, dp(10));
        group.setLayoutParams(groupParams);

        TextView labelView = text(label, 13, "#CBC8D4", Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.setMargins(0, 0, 0, dp(7));
        group.addView(labelView, labelParams);

        group.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        ));
        return group;
    }

    private EditText input(String hint, int inputType) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setTextSize(16);
        editText.setTextColor(AppTheme.primaryText(this));
        editText.setHintTextColor(color("#777582"));
        editText.setSingleLine(true);
        editText.setInputType(inputType);
        editText.setPadding(dp(15), 0, dp(15), 0);
        editText.setBackground(rounded("#10FFFFFF", dp(12), "#1AFFFFFF", 1));
        return editText;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(17);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(primaryBackground());
        button.setElevation(dp(2));
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(AppTheme.primaryText(this));
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(rounded("#10FFFFFF", dp(12), "#1AFFFFFF", 1));
        return button;
    }

    private Button dangerButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(color("#FC5C7D"));
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(rounded("#26FC5C7D", dp(12), "#66FC5C7D", 1));
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

    private String clean(EditText editText) {
        return editText.getText().toString().trim();
    }

    private String internationalPhoneNumberOrNull() {
        try {
            return phoneInput.internationalNumber();
        } catch (IllegalArgumentException error) {
            statusText.setText(error.getMessage());
            return null;
        }
    }

    private String readableError(Exception error) {
        if (error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()) {
            return "Something went wrong. Try again.";
        }
        return error.getMessage();
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

    private GradientDrawable primaryBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{color("#7C5CFC"), color("#4F35CC")}
        );
        drawable.setCornerRadius(dp(12));
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

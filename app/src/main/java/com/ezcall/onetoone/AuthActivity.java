package com.ezcall.onetoone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseTooManyRequestsException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class AuthActivity extends Activity {
    static final String EXTRA_OPEN_SIGN_IN = "open_sign_in";
    static final String EXTRA_STATUS_MESSAGE = "auth_status_message";
    static final String EXTRA_EMAIL = "auth_email";
    private static final int PICK_PROFILE_PHOTO_REQUEST = 12;
    private static final int PROFILE_PHOTO_MAX_SIZE = 640;
    private static final int PROFILE_PHOTO_JPEG_QUALITY = 72;

    private boolean createAccountMode = true;
    private boolean completingGoogleProfile;
    private LinearLayout photoGroup;
    private LinearLayout nameGroup;
    private LinearLayout phoneGroup;
    private LinearLayout emailGroup;
    private LinearLayout passwordGroup;
    private LinearLayout confirmPasswordGroup;
    private TextView titleText;
    private TextView subtitleText;
    private TextView toggleText;
    private LinearLayout googleDivider;
    private TextView forgotPasswordText;
    private TextView statusText;
    private Button actionButton;
    private Button googleButton;
    private EditText nameInput;
    private CountryPhoneInput phoneInput;
    private EditText emailInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;
    private ImageView photoPreview;
    private ScrollView authScrollView;
    private String selectedPhotoBase64 = "";
    private CredentialManager credentialManager;
    private AlertDialog smsCodeDialog;
    private EditText smsCodeInput;
    private String phoneBeingVerified = "";
    private String phoneVerificationId = "";
    private PhoneAuthProvider.ForceResendingToken phoneResendToken;
    private PhoneVerifiedAction pendingPhoneVerifiedAction;
    private int phoneVerificationGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppTheme.applyWindow(this);
        credentialManager = CredentialManager.create(this);
        buildUi();
        applyLaunchState();
        restoreExistingSessionIfPossible();
    }

    private void applyLaunchState() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_SIGN_IN, false)) {
            createAccountMode = false;
            completingGoogleProfile = false;
            applyMode();
        }
        String email = intent.getStringExtra(EXTRA_EMAIL);
        if (email != null && !email.trim().isEmpty()) {
            emailInput.setText(email.trim());
        }
        String message = intent.getStringExtra(EXTRA_STATUS_MESSAGE);
        if (message != null && !message.trim().isEmpty()) {
            statusText.setText(message.trim());
        }
    }

    @Override
    protected void onDestroy() {
        phoneVerificationGeneration++;
        pendingPhoneVerifiedAction = null;
        if (smsCodeDialog != null) {
            smsCodeDialog.dismiss();
            smsCodeDialog = null;
        }
        super.onDestroy();
    }

    private void buildUi() {
        authScrollView = new ScrollView(this);
        authScrollView.setFillViewport(true);
        authScrollView.setBackground(screenBackground());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        authScrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(24), dp(44), dp(24), dp(22));
        header.setBackgroundColor(Color.TRANSPARENT);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView brand = text("EZ CALL", 13, "#7C5CFC", Typeface.BOLD);
        brand.setLetterSpacing(0.12f);
        header.addView(brand);

        titleText = text("", 36, "#FFFFFF", Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(0, dp(14), 0, 0);
        header.addView(titleText, titleParams);

        subtitleText = text("", 16, "#8E8B99", Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.setMargins(0, dp(8), 0, 0);
        header.addView(subtitleText, subtitleParams);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(14), dp(24), dp(32));
        root.addView(form, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        nameInput = input("Full name", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        nameGroup = fieldGroup("Full name", nameInput);
        form.addView(nameGroup);

        phoneInput = new CountryPhoneInput(this);
        phoneGroup = fieldGroup("Phone number for calls", phoneInput);
        form.addView(phoneGroup);

        emailInput = input("name@example.com", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailGroup = fieldGroup("Email address", emailInput);
        form.addView(emailGroup);

        passwordInput = passwordInput("Enter your password");
        passwordGroup = fieldGroup("Password", passwordInput);
        form.addView(passwordGroup);

        confirmPasswordInput = passwordInput("Repeat your password");
        confirmPasswordGroup = fieldGroup("Confirm password", confirmPasswordInput);
        form.addView(confirmPasswordGroup);

        photoGroup = profilePhotoGroup();
        form.addView(photoGroup);

        forgotPasswordText = text("Forgot password?", 14, "#9D83FF", Typeface.BOLD);
        forgotPasswordText.setGravity(Gravity.END);
        forgotPasswordText.setPadding(dp(8), dp(4), 0, dp(8));
        forgotPasswordText.setOnClickListener(view -> sendPasswordReset());
        form.addView(forgotPasswordText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        statusText = text("", 14, "#D8D5E0", Typeface.BOLD);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusParams.setMargins(0, dp(4), 0, 0);
        form.addView(statusText, statusParams);

        actionButton = primaryButton("");
        actionButton.setOnClickListener(view -> {
            if (completingGoogleProfile) {
                completeGoogleProfile();
            } else if (createAccountMode) {
                createAccount();
            } else {
                logIn();
            }
        });
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        actionParams.setMargins(0, dp(14), 0, 0);
        form.addView(actionButton, actionParams);

        googleDivider = divider();
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dividerParams.setMargins(0, dp(22), 0, dp(16));
        form.addView(googleDivider, dividerParams);

        googleButton = googleButton();
        googleButton.setOnClickListener(view -> signInWithGoogle());
        form.addView(googleButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        ));

        toggleText = text("", 15, "#9D83FF", Typeface.BOLD);
        toggleText.setGravity(Gravity.CENTER);
        toggleText.setPadding(dp(10), dp(24), dp(10), dp(12));
        toggleText.setOnClickListener(view -> {
            if (completingGoogleProfile) {
                cancelProfileCompletion();
                return;
            }
            createAccountMode = !createAccountMode;
            applyMode();
        });
        form.addView(toggleText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        applyMode();
        setContentView(authScrollView);
    }

    private void applyMode() {
        boolean showProfileFields = createAccountMode || completingGoogleProfile;
        photoGroup.setVisibility(showProfileFields ? View.VISIBLE : View.GONE);
        nameGroup.setVisibility(showProfileFields ? View.VISIBLE : View.GONE);
        phoneGroup.setVisibility(showProfileFields ? View.VISIBLE : View.GONE);
        emailGroup.setVisibility(completingGoogleProfile ? View.GONE : View.VISIBLE);
        passwordGroup.setVisibility(completingGoogleProfile ? View.GONE : View.VISIBLE);
        confirmPasswordGroup.setVisibility(createAccountMode && !completingGoogleProfile ? View.VISIBLE : View.GONE);
        forgotPasswordText.setVisibility(!createAccountMode && !completingGoogleProfile ? View.VISIBLE : View.GONE);
        toggleText.setVisibility(View.VISIBLE);
        googleDivider.setVisibility(completingGoogleProfile ? View.GONE : View.VISIBLE);
        googleButton.setVisibility(completingGoogleProfile ? View.GONE : View.VISIBLE);

        if (completingGoogleProfile) {
            titleText.setText("Complete your profile");
            subtitleText.setText("Add your phone number and photo to finish setting up calls.");
            actionButton.setText("Save and continue");
            toggleText.setText("Use a different account");
        } else {
            titleText.setText(createAccountMode ? "Create your account" : "Welcome back");
            subtitleText.setText(createAccountMode
                    ? "Register to make secure video calls with people you know."
                    : "Sign in to see your contacts and continue calling.");
            actionButton.setText(createAccountMode ? "Create account" : "Sign in");
            toggleText.setText(createAccountMode
                    ? "Already have an account?  Sign in"
                    : "New to EZ Call?  Create account");
        }
        statusText.setText("");
        authScrollView.post(() -> authScrollView.fullScroll(View.FOCUS_UP));
    }
    private void restoreExistingSessionIfPossible() {
        if (!FirebaseCallRepository.isConfigured(this) || FirebaseAuth.getInstance().getCurrentUser() == null) {
            return;
        }

        setBusy(true, "Restoring your calling profile...");
        FirebaseCallRepository.fetchAndCacheProfileForCurrentUser(this, new FirebaseCallRepository.ProfileLoadListener() {
            @Override
            public void onProfileLoaded(FirebaseCallRepository.UserProfile profile) {
                openMain();
            }

            @Override
            public void onMissingProfile() {
                showProfileCompletion();
            }

            @Override
            public void onFailure(Exception error) {
                setBusy(false, readableError(error));
            }
        });
    }

    private void createAccount() {
        String displayName = clean(nameInput);
        String email = clean(emailInput);
        String password = passwordInput.getText().toString();
        String confirmedPassword = confirmPasswordInput.getText().toString();

        if (displayName.isEmpty()) {
            statusText.setText("Enter your name.");
            return;
        }
        String phoneNumber = internationalPhoneNumberOrNull();
        if (phoneNumber == null) {
            return;
        }
        if (!isEmail(email)) {
            statusText.setText("Enter a valid email address.");
            return;
        }
        if (password.length() < 6) {
            statusText.setText("Password must be at least 6 characters.");
            return;
        }
        if (!password.equals(confirmedPassword)) {
            statusText.setText("Passwords do not match.");
            return;
        }

        PendingEmailRegistration registration = new PendingEmailRegistration(
                displayName,
                email,
                password,
                phoneNumber,
                selectedPhotoBase64
        );
        beginPhoneVerification(
                phoneNumber,
                credential -> createEmailAccountAfterPhoneVerification(registration, credential)
        );
    }

    private void createEmailAccountAfterPhoneVerification(
            PendingEmailRegistration registration,
            PhoneAuthCredential phoneCredential
    ) {
        setBusy(true, "Creating your verified account...");
        FirebaseAuth.getInstance()
                .createUserWithEmailAndPassword(registration.email, registration.password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        FirebaseAuth.getInstance().signOut();
                        setBusy(false, "Could not create your account. Try again.");
                        return;
                    }
                    user.linkWithCredential(phoneCredential)
                            .addOnSuccessListener(linked -> saveNewEmailProfile(user, registration))
                            .addOnFailureListener(error -> deleteNewAccountAfterPhoneFailure(user, error));
                })
                .addOnFailureListener(error -> setBusy(false, readableError(error)));
    }

    private void saveNewEmailProfile(
            FirebaseUser user,
            PendingEmailRegistration registration
    ) {
        user.updateProfile(new UserProfileChangeRequest.Builder()
                .setDisplayName(registration.displayName)
                .build());
        FirebaseCallRepository.saveUserProfile(
                this,
                user.getUid(),
                registration.displayName,
                registration.email,
                registration.phoneNumber,
                registration.photoBase64,
                new FirebaseCallRepository.ProfileSaveListener() {
                    @Override
                    public void onSuccess() {
                        FirebaseCallRepository.registerDeviceForPhoneNumber(
                                AuthActivity.this,
                                registration.phoneNumber
                        );
                        openMain();
                    }

                    @Override
                    public void onFailure(Exception error) {
                        handleNewAccountProfileFailure(user, error);
                    }
                }
        );
    }

    private void deleteNewAccountAfterPhoneFailure(FirebaseUser user, Exception error) {
        user.delete().addOnCompleteListener(unused -> {
            FirebaseAuth.getInstance().signOut();
            setBusy(false, readablePhoneVerificationError(error));
        });
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
            statusText.setText("Profile photo selected.");
        } catch (IOException error) {
            statusText.setText("Could not load that photo. Try another image.");
        }
    }

    private void logIn() {
        String email = clean(emailInput);
        String password = passwordInput.getText().toString();

        if (!isEmail(email)) {
            statusText.setText("Enter your email address.");
            return;
        }
        if (password.isEmpty()) {
            statusText.setText("Enter your password.");
            return;
        }

        setBusy(true, "Logging in...");
        FirebaseAuth.getInstance()
                .signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> FirebaseCallRepository.fetchAndCacheProfileForCurrentUser(
                        this,
                        new FirebaseCallRepository.ProfileLoadListener() {
                            @Override
                            public void onProfileLoaded(FirebaseCallRepository.UserProfile profile) {
                                openMain();
                            }

                            @Override
                            public void onMissingProfile() {
                                showProfileCompletion();
                            }

                            @Override
                            public void onFailure(Exception error) {
                                setBusy(false, readableError(error));
                            }
                        }
                ))
                .addOnFailureListener(error -> setBusy(false, readableError(error)));
    }

    private void sendPasswordReset() {
        EditText resetEmailInput = input(
                "name@example.com",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        );
        resetEmailInput.setText(clean(emailInput));

        LinearLayout dialogContent = new LinearLayout(this);
        dialogContent.setOrientation(LinearLayout.VERTICAL);
        dialogContent.setPadding(dp(22), dp(6), dp(22), 0);
        dialogContent.addView(resetEmailInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        ));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Reset password")
                .setMessage("Enter the email address used for your EZ Call account.")
                .setView(dialogContent)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send link", null)
                .create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String email = clean(resetEmailInput);
                    if (!isEmail(email)) {
                        resetEmailInput.setError("Enter a valid email address.");
                        resetEmailInput.requestFocus();
                        return;
                    }
                    emailInput.setText(email);
                    dialog.dismiss();
                    sendPasswordResetTo(email);
                }));
        dialog.show();
    }

    private void sendPasswordResetTo(String email) {
        setBusy(true, "Sending password reset email...");
        FirebaseAuth.getInstance()
                .sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> setBusy(
                        false,
                        "Password reset link requested for " + email
                                + ". Check your inbox and spam folder."
                ))
                .addOnFailureListener(error -> setBusy(false, readableError(error)));
    }

    private void handleNewAccountProfileFailure(FirebaseUser user, Exception error) {
        if (!(error instanceof PhoneProfileOwnership.AlreadyRegisteredException)
                || user == null) {
            setBusy(false, readableError(error));
            return;
        }
        user.delete().addOnCompleteListener(unused -> {
            FirebaseAuth.getInstance().signOut();
            setBusy(false, readableError(error));
        });
    }

    private void signInWithGoogle() {
        if (!FirebaseCallRepository.isConfigured(this)) {
            statusText.setText("Firebase is not configured.");
            return;
        }

        setBusy(true, "Opening Google sign-in...");
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(R.string.default_web_client_id))
                .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        credentialManager.getCredentialAsync(
                this,
                request,
                new CancellationSignal(),
                command -> runOnUiThread(command),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleGoogleCredential(result.getCredential());
                    }

                    @Override
                    public void onError(GetCredentialException error) {
                        setBusy(false, readableGoogleError(error));
                    }
                }
        );
    }

    private void handleGoogleCredential(Credential credential) {
        if (!(credential instanceof CustomCredential)
                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            setBusy(false, "Google returned an unsupported sign-in credential.");
            return;
        }

        try {
            CustomCredential customCredential = (CustomCredential) credential;
            GoogleIdTokenCredential googleCredential = GoogleIdTokenCredential.createFrom(customCredential.getData());
            AuthCredential firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.getIdToken(), null);
            FirebaseAuth.getInstance()
                    .signInWithCredential(firebaseCredential)
                    .addOnSuccessListener(result -> loadProfileAfterAuthentication())
                    .addOnFailureListener(error -> setBusy(false, readableError(error)));
        } catch (RuntimeException error) {
            setBusy(false, "Google returned an invalid sign-in response. Update the app and try again.");
        }
    }

    private void loadProfileAfterAuthentication() {
        FirebaseCallRepository.fetchAndCacheProfileForCurrentUser(this, new FirebaseCallRepository.ProfileLoadListener() {
            @Override
            public void onProfileLoaded(FirebaseCallRepository.UserProfile profile) {
                openMain();
            }

            @Override
            public void onMissingProfile() {
                showProfileCompletion();
            }

            @Override
            public void onFailure(Exception error) {
                setBusy(false, readableError(error));
            }
        });
    }

    private void showProfileCompletion() {
        completingGoogleProfile = true;
        createAccountMode = false;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
                nameInput.setText(user.getDisplayName().trim());
            }
            if (user.getEmail() != null) {
                emailInput.setText(user.getEmail());
            }
        }
        applyMode();
        setBusy(false, "Enter your phone number to finish setup.");
    }

    private void completeGoogleProfile() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            completingGoogleProfile = false;
            applyMode();
            statusText.setText("Your sign-in expired. Sign in with Google again.");
            return;
        }

        String displayName = clean(nameInput);
        if (displayName.isEmpty()) {
            statusText.setText("Enter your name.");
            return;
        }
        String phoneNumber = internationalPhoneNumberOrNull();
        if (phoneNumber == null) {
            return;
        }

        beginPhoneVerification(
                phoneNumber,
                credential -> completeGoogleProfileAfterPhoneVerification(
                        user,
                        displayName,
                        phoneNumber,
                        selectedPhotoBase64,
                        credential
                )
        );
    }

    private void completeGoogleProfileAfterPhoneVerification(
            FirebaseUser user,
            String displayName,
            String phoneNumber,
            String photoBase64,
            PhoneAuthCredential credential
    ) {
        setBusy(true, "Saving your verified calling profile...");
        if (credential == null) {
            saveGoogleProfile(user, displayName, phoneNumber, photoBase64);
            return;
        }
        user.linkWithCredential(credential)
                .addOnSuccessListener(linked -> saveGoogleProfile(
                        user,
                        displayName,
                        phoneNumber,
                        photoBase64
                ))
                .addOnFailureListener(error -> setBusy(
                        false,
                        readablePhoneVerificationError(error)
                ));
    }

    private void saveGoogleProfile(
            FirebaseUser user,
            String displayName,
            String phoneNumber,
            String photoBase64
    ) {
        user.updateProfile(new UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build());
        FirebaseCallRepository.saveUserProfile(
                this,
                user.getUid(),
                displayName,
                user.getEmail() == null ? "" : user.getEmail(),
                phoneNumber,
                photoBase64,
                new FirebaseCallRepository.ProfileSaveListener() {
                    @Override
                    public void onSuccess() {
                        FirebaseCallRepository.registerDeviceForPhoneNumber(AuthActivity.this, phoneNumber);
                        openMain();
                    }

                    @Override
                    public void onFailure(Exception error) {
                        setBusy(false, readableError(error));
                    }
                }
        );
    }

    private void beginPhoneVerification(String phoneNumber, PhoneVerifiedAction action) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null
                && FirebaseCallRepository.normalizePhoneNumber(phoneNumber).equals(
                FirebaseCallRepository.normalizePhoneNumber(currentUser.getPhoneNumber())
        )) {
            action.onVerified(null);
            return;
        }

        phoneBeingVerified = phoneNumber;
        phoneVerificationId = "";
        phoneResendToken = null;
        pendingPhoneVerifiedAction = action;
        setBusy(true, "Sending a verification code to " + phoneNumber + "...");
        sendPhoneVerificationCode(null);
    }

    private void sendPhoneVerificationCode(
            PhoneAuthProvider.ForceResendingToken forceResendingToken
    ) {
        int generation = ++phoneVerificationGeneration;
        PhoneAuthOptions.Builder options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
                .setPhoneNumber(phoneBeingVerified)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(PhoneAuthCredential credential) {
                        if (generation == phoneVerificationGeneration) {
                            completePhoneVerification(credential);
                        }
                    }

                    @Override
                    public void onVerificationFailed(FirebaseException error) {
                        if (generation == phoneVerificationGeneration) {
                            failPhoneVerification(error);
                        }
                    }

                    @Override
                    public void onCodeSent(
                            String verificationId,
                            PhoneAuthProvider.ForceResendingToken resendingToken
                    ) {
                        if (generation != phoneVerificationGeneration) {
                            return;
                        }
                        phoneVerificationId = verificationId;
                        phoneResendToken = resendingToken;
                        showSmsCodeDialog();
                    }
                });
        if (forceResendingToken != null) {
            options.setForceResendingToken(forceResendingToken);
        }
        PhoneAuthProvider.verifyPhoneNumber(options.build());
    }

    private void showSmsCodeDialog() {
        setBusy(true, "Verification code sent to " + phoneBeingVerified + ".");
        if (smsCodeDialog != null && smsCodeDialog.isShowing()) {
            smsCodeDialog.setMessage("Enter the new 6-digit code sent to " + phoneBeingVerified + ".");
            setSmsDialogBusy(false);
            return;
        }

        smsCodeInput = input("6-digit code", InputType.TYPE_CLASS_NUMBER);
        smsCodeInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        smsCodeInput.setGravity(Gravity.CENTER);
        smsCodeInput.setTextColor(AppTheme.primaryText(this));
        smsCodeInput.setHintTextColor(color("#777582"));
        smsCodeInput.setBackground(rounded("#0F101C", dp(12), "#454451", 1));

        LinearLayout dialogContent = new LinearLayout(this);
        dialogContent.setOrientation(LinearLayout.VERTICAL);
        dialogContent.setPadding(dp(22), dp(6), dp(22), 0);
        dialogContent.addView(smsCodeInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
        ));

        smsCodeDialog = new AlertDialog.Builder(this)
                .setTitle("Verify phone number")
                .setMessage("Enter the 6-digit code sent to " + phoneBeingVerified + ".")
                .setView(dialogContent)
                .setNegativeButton("Cancel", (dialog, which) -> cancelPhoneVerification())
                .setNeutralButton("Resend", null)
                .setPositiveButton("Verify", null)
                .create();
        smsCodeDialog.setOnCancelListener(dialog -> cancelPhoneVerification());
        smsCodeDialog.setOnShowListener(unused -> {
            smsCodeDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> verifySmsCode());
            smsCodeDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(view -> resendSmsCode());
            smsCodeInput.requestFocus();
        });
        smsCodeDialog.show();
    }

    private void verifySmsCode() {
        String code = clean(smsCodeInput);
        if (code.length() != 6) {
            smsCodeInput.setError("Enter the 6-digit code.");
            smsCodeInput.requestFocus();
            return;
        }
        if (phoneVerificationId.isEmpty()) {
            statusText.setText("Request a new verification code.");
            return;
        }
        setSmsDialogBusy(true);
        statusText.setText("Checking verification code...");
        try {
            completePhoneVerification(
                    PhoneAuthProvider.getCredential(phoneVerificationId, code)
            );
        } catch (IllegalArgumentException error) {
            setSmsDialogBusy(false);
            smsCodeInput.setError("Enter the 6-digit code.");
        }
    }

    private void resendSmsCode() {
        if (phoneResendToken == null) {
            statusText.setText("Wait for the current code, then try again.");
            return;
        }
        setSmsDialogBusy(true);
        smsCodeInput.setText("");
        statusText.setText("Sending a new verification code...");
        sendPhoneVerificationCode(phoneResendToken);
    }

    private void completePhoneVerification(PhoneAuthCredential credential) {
        PhoneVerifiedAction action = pendingPhoneVerifiedAction;
        if (action == null) {
            return;
        }
        phoneVerificationGeneration++;
        pendingPhoneVerifiedAction = null;
        phoneVerificationId = "";
        phoneResendToken = null;
        if (smsCodeDialog != null) {
            smsCodeDialog.dismiss();
            smsCodeDialog = null;
        }
        smsCodeInput = null;
        setBusy(true, "Phone number verified.");
        action.onVerified(credential);
    }

    private void failPhoneVerification(Exception error) {
        phoneVerificationGeneration++;
        pendingPhoneVerifiedAction = null;
        phoneVerificationId = "";
        phoneResendToken = null;
        if (smsCodeDialog != null) {
            smsCodeDialog.dismiss();
            smsCodeDialog = null;
        }
        smsCodeInput = null;
        setBusy(false, readablePhoneVerificationError(error));
    }

    private void cancelPhoneVerification() {
        phoneVerificationGeneration++;
        pendingPhoneVerifiedAction = null;
        phoneVerificationId = "";
        phoneResendToken = null;
        smsCodeDialog = null;
        smsCodeInput = null;
        setBusy(false, "Phone verification cancelled.");
    }

    private void setSmsDialogBusy(boolean busy) {
        if (smsCodeDialog == null || !smsCodeDialog.isShowing()) {
            return;
        }
        smsCodeDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!busy);
        smsCodeDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(!busy);
        smsCodeDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!busy);
        if (smsCodeInput != null) {
            smsCodeInput.setEnabled(!busy);
        }
    }

    private String readablePhoneVerificationError(Exception error) {
        if (error instanceof FirebaseAuthUserCollisionException) {
            return "Already registered phone number.";
        }
        if (error instanceof FirebaseTooManyRequestsException) {
            return "Too many verification attempts. Try again later.";
        }
        if (error instanceof FirebaseAuthInvalidCredentialsException) {
            return "The SMS verification code is invalid or expired. Request a new code.";
        }
        if (error instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) error).getErrorCode();
            if ("ERROR_CREDENTIAL_ALREADY_IN_USE".equals(code)
                    || "ERROR_PHONE_NUMBER_ALREADY_EXISTS".equals(code)) {
                return "Already registered phone number.";
            }
            if ("ERROR_PROVIDER_ALREADY_LINKED".equals(code)) {
                return "This account already has a different verified phone number.";
            }
        }
        return readableError(error);
    }

    private void cancelProfileCompletion() {
        setBusy(true, "Signing out...");
        FirebaseCallRepository.signOut(this);
        GoogleSignInState.clear(this, () -> {
            completingGoogleProfile = false;
            createAccountMode = false;
            nameInput.setText("");
            phoneInput.clear();
            selectedPhotoBase64 = "";
            photoPreview.setImageResource(R.drawable.ic_default_user);
            applyMode();
            setBusy(false, "Choose an account to continue.");
        });
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void setBusy(boolean busy, String status) {
        actionButton.setEnabled(!busy);
        toggleText.setEnabled(!busy);
        googleButton.setEnabled(!busy);
        forgotPasswordText.setEnabled(!busy);
        actionButton.setAlpha(busy ? 0.65f : 1f);
        toggleText.setAlpha(busy ? 0.65f : 1f);
        googleButton.setAlpha(busy ? 0.65f : 1f);
        forgotPasswordText.setAlpha(busy ? 0.65f : 1f);
        statusText.setText(status);
    }

    private String readableGoogleError(GetCredentialException error) {
        if (error == null) {
            return "Google sign-in did not complete. Try again.";
        }
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Google sign-in was cancelled or no Google account is available.";
        }
        return "Google sign-in failed. " + message;
    }

    private boolean isEmail(String email) {
        return !email.isEmpty() && !TextUtils.isEmpty(email) && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
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

    private LinearLayout profilePhotoGroup() {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        groupParams.setMargins(0, 0, 0, dp(12));
        group.setLayoutParams(groupParams);

        TextView labelView = text("Profile photo (optional)", 13, "#CBC8D4", Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.setMargins(0, 0, 0, dp(7));
        group.addView(labelView, labelParams);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        photoPreview = new ImageView(this);
        photoPreview.setImageResource(R.drawable.ic_default_user);
        photoPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photoPreview.setBackground(rounded("#0F101C", dp(14), "#667C5CFC", 1));
        photoPreview.setClipToOutline(true);
        photoPreview.setElevation(dp(3));
        row.addView(photoPreview, new LinearLayout.LayoutParams(dp(80), dp(80)));

        Button chooseButton = secondaryButton("Choose your photo");
        chooseButton.setOnClickListener(view -> chooseProfilePhoto());
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(
                0,
                dp(48),
                1
        );
        chooseParams.setMargins(dp(14), 0, 0, 0);
        row.addView(chooseButton, chooseParams);

        group.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return group;
    }

    private void chooseProfilePhoto() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Choose profile photo"), PICK_PROFILE_PHOTO_REQUEST);
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

    private EditText passwordInput(String hint) {
        EditText editText = input(
                hint,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        setPasswordVisibilityIcon(editText, false);
        editText.setOnTouchListener((view, event) -> {
            if (event.getAction() != MotionEvent.ACTION_UP) {
                return false;
            }
            if (editText.getCompoundDrawables()[2] == null) {
                return false;
            }
            if (!PasswordVisibilityHitTarget.contains(
                    event.getX(),
                    editText.getWidth(),
                    editText.getTotalPaddingRight()
            )) {
                return false;
            }

            boolean currentlyVisible = PasswordVisibilityState.isVisible(
                    editText.getInputType()
            );
            setPasswordVisibilityIcon(editText, !currentlyVisible);
            editText.setSelection(editText.length());
            view.performClick();
            return true;
        });
        return editText;
    }

    private void setPasswordVisibilityIcon(EditText editText, boolean visible) {
        editText.setInputType(InputType.TYPE_CLASS_TEXT | (visible
                ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                : InputType.TYPE_TEXT_VARIATION_PASSWORD));
        editText.setCompoundDrawablesWithIntrinsicBounds(
                0,
                0,
                visible ? R.drawable.ic_visibility : R.drawable.ic_visibility_off,
                0
        );
        editText.setCompoundDrawableTintList(ColorStateList.valueOf(color("#9D83FF")));
        editText.setCompoundDrawablePadding(dp(12));
        editText.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
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

    private Button googleButton() {
        Button button = new Button(this);
        button.setText("Continue with Google");
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(AppTheme.primaryText(this));
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(rounded("#10FFFFFF", dp(12), "#1AFFFFFF", 1));
        return button;
    }

    private LinearLayout divider() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        View leftLine = new View(this);
        leftLine.setBackgroundColor(color("#1AFFFFFF"));
        row.addView(leftLine, new LinearLayout.LayoutParams(0, dp(1), 1));

        TextView label = text("OR CONTINUE WITH", 11, "#777582", Typeface.BOLD);
        label.setLetterSpacing(0.06f);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        labelParams.setMargins(dp(14), 0, dp(14), 0);
        row.addView(label, labelParams);

        View rightLine = new View(this);
        rightLine.setBackgroundColor(color("#1AFFFFFF"));
        row.addView(rightLine, new LinearLayout.LayoutParams(0, dp(1), 1));
        return row;
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

    private interface PhoneVerifiedAction {
        void onVerified(PhoneAuthCredential credential);
    }

    private static final class PendingEmailRegistration {
        final String displayName;
        final String email;
        final String password;
        final String phoneNumber;
        final String photoBase64;

        PendingEmailRegistration(
                String displayName,
                String email,
                String password,
                String phoneNumber,
                String photoBase64
        ) {
            this.displayName = displayName;
            this.email = email;
            this.password = password;
            this.phoneNumber = phoneNumber;
            this.photoBase64 = photoBase64;
        }
    }
}

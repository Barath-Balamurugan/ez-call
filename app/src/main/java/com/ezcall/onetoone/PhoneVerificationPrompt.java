package com.ezcall.onetoone;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

/**
 * Sends an SMS verification code and collects it from the user.
 *
 * <p>A phone credential built from a typed code is not validated locally. It is only
 * proven correct once Firebase accepts it (link, update, or sign-in). This prompt
 * therefore keeps its dialog on screen until the caller reports the outcome through
 * {@link Result}, so a mistyped digit stays recoverable: the code field clears, the
 * Resend button stays available, and the verification session is preserved.
 */
final class PhoneVerificationPrompt {
    interface Callbacks {
        /**
         * Check {@code credential} against Firebase and report back through {@code result}.
         *
         * <p>{@code credential} is null when the number is already verified on the signed-in
         * account and no SMS was needed; in that case the check cannot fail.
         */
        void onPhoneCredentialReady(PhoneAuthCredential credential, Result result);

        void onPhoneVerificationStatus(String message);

        void onPhoneVerificationCancelled();

        void onPhoneVerificationFailed(Exception error);
    }

    interface Result {
        /** Firebase accepted the credential. Dismisses the dialog and ends the session. */
        void accept();

        /**
         * Firebase rejected the credential. A wrong or expired code reopens the dialog for
         * another attempt; anything else ends the session through
         * {@link Callbacks#onPhoneVerificationFailed}.
         */
        void reject(Exception error);
    }

    private static final long CODE_TIMEOUT_SECONDS = 60L;
    private static final int CODE_LENGTH = 6;

    private final Activity activity;
    private final Callbacks callbacks;

    private AlertDialog codeDialog;
    private EditText codeInput;
    private String phoneNumber = "";
    private String verificationId = "";
    private PhoneAuthProvider.ForceResendingToken resendToken;
    private int generation;
    private boolean checkInFlight;
    private boolean active;

    PhoneVerificationPrompt(Activity activity, Callbacks callbacks) {
        this.activity = activity;
        this.callbacks = callbacks;
    }

    /**
     * Verifies {@code requestedPhoneNumber}. Skips the SMS round trip when the signed-in
     * account already has that number verified.
     */
    void start(String requestedPhoneNumber) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && sameNumber(requestedPhoneNumber, currentUser.getPhoneNumber())) {
            active = true;
            submit(null);
            return;
        }

        phoneNumber = requestedPhoneNumber;
        verificationId = "";
        resendToken = null;
        checkInFlight = false;
        active = true;
        callbacks.onPhoneVerificationStatus(
                "Sending a verification code to " + phoneNumber + "..."
        );
        sendCode(null);
    }

    /** Tears the prompt down without notifying the caller. For {@code onDestroy}. */
    void dismiss() {
        endSession();
    }

    private void sendCode(PhoneAuthProvider.ForceResendingToken forceResendingToken) {
        int requestGeneration = ++generation;
        PhoneAuthOptions.Builder options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
                .setPhoneNumber(phoneNumber)
                .setTimeout(CODE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(PhoneAuthCredential credential) {
                        if (requestGeneration == generation) {
                            submit(credential);
                        }
                    }

                    @Override
                    public void onVerificationFailed(FirebaseException error) {
                        if (requestGeneration == generation) {
                            fail(error);
                        }
                    }

                    @Override
                    public void onCodeSent(
                            String sentVerificationId,
                            PhoneAuthProvider.ForceResendingToken sentResendToken
                    ) {
                        if (requestGeneration != generation) {
                            return;
                        }
                        verificationId = sentVerificationId;
                        resendToken = sentResendToken;
                        showCodeDialog();
                    }
                });
        if (forceResendingToken != null) {
            options.setForceResendingToken(forceResendingToken);
        }
        PhoneAuthProvider.verifyPhoneNumber(options.build());
    }

    private void showCodeDialog() {
        callbacks.onPhoneVerificationStatus("Verification code sent to " + phoneNumber + ".");
        if (codeDialog != null && codeDialog.isShowing()) {
            codeDialog.setMessage("Enter the new " + CODE_LENGTH + "-digit code sent to "
                    + phoneNumber + ".");
            setDialogBusy(false);
            return;
        }
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        codeInput = new EditText(activity);
        codeInput.setHint(CODE_LENGTH + "-digit code");
        codeInput.setTextSize(16);
        codeInput.setSingleLine(true);
        codeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        codeInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(CODE_LENGTH)});
        codeInput.setGravity(Gravity.CENTER);
        codeInput.setTextColor(Color.WHITE);
        codeInput.setHintTextColor(Color.parseColor("#777582"));
        codeInput.setPadding(dp(15), 0, dp(15), 0);
        codeInput.setBackground(roundedField());

        LinearLayout dialogContent = new LinearLayout(activity);
        dialogContent.setOrientation(LinearLayout.VERTICAL);
        dialogContent.setPadding(dp(22), dp(6), dp(22), 0);
        dialogContent.addView(codeInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
        ));

        codeDialog = new AlertDialog.Builder(activity)
                .setTitle("Verify phone number")
                .setMessage("Enter the " + CODE_LENGTH + "-digit code sent to " + phoneNumber + ".")
                .setView(dialogContent)
                .setNegativeButton("Cancel", (dialog, which) -> cancel())
                .setNeutralButton("Resend", null)
                .setPositiveButton("Verify", null)
                .create();
        codeDialog.setCanceledOnTouchOutside(false);
        codeDialog.setOnCancelListener(dialog -> cancel());
        codeDialog.setOnShowListener(unused -> {
            // Wired after show so a rejected code can keep the dialog open instead of
            // letting the default button behaviour dismiss it.
            codeDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> verifyTypedCode());
            codeDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(view -> resendCode());
            codeInput.requestFocus();
        });
        codeDialog.show();
    }

    private void verifyTypedCode() {
        if (codeInput == null) {
            return;
        }
        String code = codeInput.getText().toString().trim();
        if (code.length() != CODE_LENGTH) {
            codeInput.setError("Enter the " + CODE_LENGTH + "-digit code.");
            codeInput.requestFocus();
            return;
        }
        if (verificationId.isEmpty()) {
            codeInput.setError("Tap Resend to get a new code.");
            return;
        }
        try {
            submit(PhoneAuthProvider.getCredential(verificationId, code));
        } catch (IllegalArgumentException error) {
            codeInput.setError("Enter the " + CODE_LENGTH + "-digit code.");
        }
    }

    private void resendCode() {
        if (resendToken == null) {
            callbacks.onPhoneVerificationStatus("Wait for the current code, then try again.");
            return;
        }
        checkInFlight = false;
        setDialogBusy(true);
        if (codeInput != null) {
            codeInput.setText("");
            codeInput.setError(null);
        }
        callbacks.onPhoneVerificationStatus("Sending a new verification code...");
        sendCode(resendToken);
    }

    private void submit(PhoneAuthCredential credential) {
        if (!active || checkInFlight) {
            return;
        }
        checkInFlight = true;
        setDialogBusy(true);
        callbacks.onPhoneVerificationStatus(
                credential == null
                        ? "Phone number already verified."
                        : "Checking your verification code..."
        );
        callbacks.onPhoneCredentialReady(credential, new Result() {
            @Override
            public void accept() {
                if (!active) {
                    return;
                }
                endSession();
            }

            @Override
            public void reject(Exception error) {
                if (!active) {
                    return;
                }
                checkInFlight = false;
                if (isRetryableCodeError(error) && codeDialog != null && codeDialog.isShowing()) {
                    setDialogBusy(false);
                    if (codeInput != null) {
                        codeInput.setText("");
                        codeInput.setError("Incorrect or expired code.");
                        codeInput.requestFocus();
                    }
                    callbacks.onPhoneVerificationStatus(
                            "That code did not work. Enter it again, or tap Resend for a new one."
                    );
                    return;
                }
                fail(error);
            }
        });
    }

    private void fail(Exception error) {
        endSession();
        callbacks.onPhoneVerificationFailed(error);
    }

    private void cancel() {
        endSession();
        callbacks.onPhoneVerificationCancelled();
    }

    private void endSession() {
        active = false;
        checkInFlight = false;
        generation++;
        verificationId = "";
        resendToken = null;
        if (codeDialog != null) {
            codeDialog.setOnCancelListener(null);
            codeDialog.dismiss();
            codeDialog = null;
        }
        codeInput = null;
    }

    private void setDialogBusy(boolean busy) {
        if (codeDialog == null || !codeDialog.isShowing()) {
            return;
        }
        codeDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!busy);
        codeDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(!busy);
        codeDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!busy);
        if (codeInput != null) {
            codeInput.setEnabled(!busy);
        }
    }

    /** True when the user can fix the problem by retyping or resending the code. */
    static boolean isRetryableCodeError(Exception error) {
        return PhoneVerificationRetryPolicy.allowsAnotherCodeAttempt(
                error instanceof FirebaseAuthInvalidCredentialsException,
                error instanceof FirebaseAuthException
                        ? ((FirebaseAuthException) error).getErrorCode()
                        : ""
        );
    }

    static boolean sameNumber(String left, String right) {
        String normalizedLeft = FirebaseCallRepository.normalizePhoneNumber(left);
        return !normalizedLeft.isEmpty()
                && normalizedLeft.equals(FirebaseCallRepository.normalizePhoneNumber(right));
    }

    private GradientDrawable roundedField() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor("#0F101C"));
        drawable.setCornerRadius(dp(12));
        drawable.setStroke(dp(1), Color.parseColor("#454451"));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}

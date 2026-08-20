package com.ezcall.onetoone;

import android.app.Activity;
import android.os.CancellationSignal;

import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.exceptions.ClearCredentialException;

final class GoogleSignInState {
    private GoogleSignInState() {
    }

    static void clear(Activity activity, Runnable completion) {
        CredentialManager.create(activity).clearCredentialStateAsync(
                new ClearCredentialStateRequest(),
                new CancellationSignal(),
                command -> activity.runOnUiThread(command),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override
                    public void onResult(Void result) {
                        completion.run();
                    }

                    @Override
                    public void onError(ClearCredentialException error) {
                        completion.run();
                    }
                }
        );
    }
}

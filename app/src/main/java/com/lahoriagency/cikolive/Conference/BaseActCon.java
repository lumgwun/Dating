package com.lahoriagency.cikolive.Conference;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;
import com.lahoriagency.cikolive.Classes.SharedPrefsHelper;
import com.lahoriagency.cikolive.NewPackage.ChatHelperCon;
import com.lahoriagency.cikolive.NewPackage.DialogsManagerCon;
import com.lahoriagency.cikolive.NewPackage.ErrorUtilsCon;
import com.lahoriagency.cikolive.R;
import com.quickblox.chat.QBChatService;
import com.quickblox.core.QBEntityCallback;
import com.quickblox.core.exception.QBResponseException;
import com.quickblox.users.QBUsers;
import com.quickblox.users.model.QBUser;

public  class BaseActCon extends AppCompatActivity {
    private static final String TAG = BaseActCon.class.getSimpleName();
    private static final String DUMMY_VALUE = "dummy_value";

    private ProgressDialog progressDialog = null;
    private Snackbar snackbar;

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        outState.putInt(DUMMY_VALUE, 0);
        super.onSaveInstanceState(outState, outPersistentState);
    }
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    protected SharedPrefsHelper getSharedPrefsHelper() {
        return ((AppConference) getApplicationContext()).getSharedPrefsHelper();
    }

    protected QBUsersHolder getQBUsersHolder() {
        return ((AppConference) getApplicationContext()).getQBUsersHolder();
    }

    protected QBDialogsHolder getQBDialogsHolder() {
        return ((AppConference) getApplicationContext()).getQBDialogsHolder();
    }

    protected ChatHelperCon getChatHelper() {
        return ((AppConference) getApplicationContext()).getChatHelper();
    }

    protected DialogsManagerCon getDialogsManager() {
        return ((AppConference) getApplicationContext()).getDialogsManager();
    }

    @Override
    protected void onPause() {
        super.onPause();
        hideProgressDialog();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    protected void showErrorSnackbar(@StringRes int resId, Exception e, View.OnClickListener clickListener) {
        View rootView = getWindow().getDecorView().findViewById(android.R.id.content);
        if (rootView != null) {
            ErrorUtilsCon.showSnackbar(getApplicationContext(), rootView, resId, e,
                    R.string.dlg_retry, clickListener);
        }
    }

    protected void showInfoSnackbar(String message, @StringRes int actionLabel, View.OnClickListener clickListener) {
        View rootView = getWindow().getDecorView().findViewById(android.R.id.content);
        if (rootView != null) {
            snackbar = ErrorUtilsCon.showInfoSnackbar(getApplicationContext(), rootView, message, actionLabel, clickListener);
        }
    }

    protected void hideSnackbar() {
        if (snackbar != null && snackbar.isShown()) {
            snackbar.dismiss();
        }
    }

    protected void showProgressDialog(@StringRes Integer messageId) {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);

            // Disable the back button
            DialogInterface.OnKeyListener keyListener = (dialog, keyCode, event) -> keyCode == KeyEvent.KEYCODE_BACK;
            progressDialog.setOnKeyListener(keyListener);
        }
        progressDialog.setMessage(getString(messageId));
        try {
            progressDialog.show();
        } catch (Exception e) {
            if (e.getMessage() != null) {
                Log.d(TAG, e.getMessage());
            }
        }
    }

    protected void hideProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            try {
                progressDialog.dismiss();
                progressDialog = null;
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
    }

    protected boolean isProgressDialogShowing() {
        if (progressDialog != null) {
            return progressDialog.isShowing();
        } else {
            return false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideNotifications();
        QBUser currentUser = getSharedPrefsHelper().getQbUser();
        if (currentUser != null && !QBChatService.getInstance().isLoggedIn()) {
            Log.d(TAG, "Resuming with Relogin");
            QBUsers.signIn(currentUser).performAsync(new QBEntityCallback<QBUser>() {
                @Override
                public void onSuccess(QBUser user, Bundle bundle) {
                    Log.d(TAG, "Relogin Successful");
                    reloginToChat();
                }

                @Override
                public void onError(QBResponseException e) {
                    if (e.getMessage() != null) {
                        Log.d(TAG, e.getMessage());
                    }
                }
            });
        } else {
            Log.d(TAG, "Resuming without Relogin to Chat");
            onResumeFinished();
        }
    }

    private void reloginToChat() {
        QBUser currentUser = getSharedPrefsHelper().getQbUser();
        getChatHelper().loginToChat(currentUser, new QBEntityCallback<Void>() {
            @Override
            public void onSuccess(Void aVoid, Bundle bundle) {
                Log.d(TAG, "Relogin to Chat Successful");
                onResumeFinished();
            }

            @Override
            public void onError(QBResponseException e) {
                Log.d(TAG, "Relogin to Chat Error: " + e.getMessage());
                onResumeFinished();
            }
        });
    }

    private void hideNotifications() {
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancelAll();
        }
    }

    public void onResumeFinished() {
        // Need to Override onResumeFinished() method in nested classes if we need to handle returning from background in Activity
    }

}
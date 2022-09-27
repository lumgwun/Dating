package com.lahoriagency.cikolive.Conference;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;

import com.lahoriagency.cikolive.CallActivity;
import com.lahoriagency.cikolive.Classes.CallService;
import com.lahoriagency.cikolive.Classes.SettingsUtils;
import com.lahoriagency.cikolive.Classes.WebRtcSessionManager;
import com.lahoriagency.cikolive.DialogsActivity;
import com.lahoriagency.cikolive.Fragments.ConversationFragment;
import com.lahoriagency.cikolive.Fragments.ScreenShareFragment;
import com.lahoriagency.cikolive.Interfaces.Consts;
import com.lahoriagency.cikolive.Interfaces.ConversationFragmentCallback;
import com.lahoriagency.cikolive.Interfaces.ReconnectionCallback;
import com.lahoriagency.cikolive.NewPackage.ConfChatAct;
import com.lahoriagency.cikolive.NewPackage.ToastUtilsCon;
import com.lahoriagency.cikolive.R;
import com.quickblox.conference.ConferenceSession;
import com.quickblox.conference.WsException;
import com.quickblox.conference.WsHangUpException;
import com.quickblox.conference.WsNoResponseException;
import com.quickblox.conference.callbacks.ConferenceSessionCallbacks;
import com.quickblox.videochat.webrtc.BaseSession;
import com.quickblox.videochat.webrtc.QBRTCScreenCapturer;
import com.quickblox.videochat.webrtc.QBRTCSession;
import com.quickblox.videochat.webrtc.QBRTCTypes;
import com.quickblox.videochat.webrtc.callbacks.QBRTCClientVideoTracksCallbacks;
import com.quickblox.videochat.webrtc.callbacks.QBRTCSessionEventsCallback;
import com.quickblox.videochat.webrtc.callbacks.QBRTCSessionStateCallback;
import com.quickblox.videochat.webrtc.view.QBRTCVideoTrack;

import org.jivesoftware.smack.ConnectionListener;
import org.webrtc.CameraVideoCapturer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CallActCon extends BaseActCon implements QBRTCSessionStateCallback<ConferenceSession>, ConferenceSessionCallbacks,
        ConversationFragmentCallback, ScreenShareFragment.OnSharingEvents {
    private static final String TAG = CallActCon.class.getSimpleName();
    private static final String ICE_FAILED_REASON = "ICE failed";
    private static final int REQUEST_CODE_OPEN_CONVERSATION_CHAT = 183;
    private static final int REQUEST_CODE_MANAGE_GROUP = 132;

    private ConferenceSession currentSession;
    private SharedPreferences settingsSharedPref;
    private String currentDialogID;
    private String currentRoomID;
    private String currentRoomTitle;
    private ServiceConnection callServiceConnection;

    private ArrayList<CallService.CurrentCallStateCallback> currentCallStateCallbackList = new ArrayList<>();
    private volatile boolean connectedToJanus;
    private CallService callService;
    private final Set<ReconnectionCallback> reconnectionCallbacks = new HashSet<>();
    private final ReconnectionListenerImpl reconnectionListener = new ReconnectionListenerImpl(TAG);
    private LinearLayout reconnectingLayout;

    public static void start(Context context, String roomID, String roomTitle, String dialogID,
                             List<Integer> occupants, boolean listenerRole) {
        Intent intent = new Intent(context, CallActCon.class);
        intent.putExtra(Consts.EXTRA_ROOM_ID, roomID);
        intent.putExtra(Consts.EXTRA_ROOM_TITLE, roomTitle);
        intent.putExtra(Consts.EXTRA_DIALOG_ID, dialogID);
        intent.putExtra(Consts.EXTRA_DIALOG_OCCUPANTS, (Serializable) occupants);
        intent.putExtra(Consts.EXTRA_AS_LISTENER, listenerRole);

        context.startActivity(intent);
        CallService.start(context, roomID, roomTitle, dialogID, occupants, listenerRole);
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, CallActCon.class);
        context.startActivity(intent);
    }

    @Override
    public void addReconnectionCallback(ReconnectionCallback reconnectionCallback) {
        reconnectionCallbacks.add(reconnectionCallback);
    }

    @Override
    public void removeReconnectionCallback(ReconnectionCallback reconnectionCallback) {
        reconnectionCallbacks.remove(reconnectionCallback);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_call_act_con);
        currentRoomID = getIntent().getStringExtra(Consts.EXTRA_ROOM_ID);
        currentDialogID = getIntent().getStringExtra(Consts.EXTRA_DIALOG_ID);
        currentRoomTitle = getIntent().getStringExtra(Consts.EXTRA_ROOM_TITLE);
        PreferenceManager.setDefaultValues(this, R.xml.preferences_video, false);
        PreferenceManager.setDefaultValues(this, R.xml.preferences_audio, false);

        reconnectingLayout = findViewById(R.id.llReconnectingCon);

        Window w = getWindow();
        w.setStatusBarColor(ContextCompat.getColor(this, R.color.color_new_blue));

        // TODO: To set fullscreen style in a call:
        //w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
    }
    private void initScreen() {
        SettingsUtils.setSettingsStrategy(settingsSharedPref, CallActCon.this);

        WebRtcSessManagerCon sessionManager = WebRtcSessManagerCon.getInstance();
        if (sessionManager.getCurrentSession() == null) {
            //we have already currentSession == null, so it's no reason to do further initialization
            finish();
            return;
        }
        currentSession = sessionManager.getCurrentSession();
        initListeners(currentSession);

        if (callService != null && callService.isSharingScreenState()) {
            if (callService.getReconnectionState() == CallService.ReconnectionState.COMPLETED) {
                QBRTCScreenCapturer.requestPermissions(this);
            } else {
                startScreenSharing(null);
            }
        } else {
            startConversationFragment();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        settingsSharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        bindCallService();
        if (callService != null) {
            switch (callService.getReconnectionState()) {
                case COMPLETED:
                    reconnectingLayout.setVisibility(View.GONE);
                    for (ReconnectionCallback reconnectionCallback : reconnectionCallbacks) {
                        reconnectionCallback.completed();
                    }
                    break;
                case IN_PROGRESS:
                    reconnectingLayout.setVisibility(View.VISIBLE);
                    for (ReconnectionCallback reconnectionCallback : reconnectionCallbacks) {
                        reconnectionCallback.inProgress();
                    }
                    break;
                case FAILED:
                    ToastUtilsCon.shortToast(CallActCon.this, R.string.reconnection_failed);
                    callService.leaveCurrentSession();
                    finish();
                    break;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (callServiceConnection != null) {
            unbindService(callServiceConnection);
            callServiceConnection = null;
        }

        callService.unsubscribeReconnectionListener(reconnectionListener);
        removeListeners();
    }

    @Override
    public void finish() {
        String dialogID = currentDialogID;
        if (callService != null) {
            dialogID = callService.getDialogID();
        }
        DialogsActivity.start(this, dialogID);
        Log.d(TAG, "Starting Dialogs Activity to open dialog with ID : " + dialogID);

        Log.d(TAG, "finish CallActivity");
        super.finish();
    }

    private void bindCallService() {
        callServiceConnection = new CallServiceConnection();
        Intent intent = new Intent(this, CallService.class);
        bindService(intent, callServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void leaveCurrentSession() {
        callService.leaveCurrentSession();
        finish();
    }

    private void initListeners(ConferenceSession session) {
        if (session != null) {
            Log.d(TAG, "Init new ConferenceSession");
            this.currentSession.addSessionCallbacksListener(CallActCon.this);
            this.currentSession.addConferenceSessionListener(CallActCon.this);
        }
    }

    private void releaseCurrentSession() {
        Log.d(TAG, "Release current session");
        if (currentSession != null) {
            leaveCurrentSession();
            removeListeners();
            this.currentSession = null;
        }
    }

    private void removeListeners() {
        if (currentSession != null) {
            this.currentSession.removeSessionCallbacksListener(CallActCon.this);
            this.currentSession.removeConferenceSessionListener(CallActCon.this);
        }
    }

    // ---------------Chat callback methods implementation  ----------------------//

    @Override
    public void onConnectionClosedForUser(ConferenceSession session, Integer userID) {
        Log.d(TAG, "QBRTCSessionStateCallbackImpl onConnectionClosedForUser userID=" + userID);
    }

    @Override
    public void onConnectedToUser(ConferenceSession session, final Integer userID) {
        Log.d(TAG, "onConnectedToUser userID= " + userID + " sessionID= " + session.getSessionID());
    }

    @Override
    public void onStateChanged(ConferenceSession session, BaseSession.QBRTCSessionState state) {
        if (BaseSession.QBRTCSessionState.QB_RTC_SESSION_CONNECTED.equals(state)) {
            connectedToJanus = true;
            Log.d(TAG, "onStateChanged and begin subscribeToPublishersIfNeed");
        }
    }

    @Override
    public void onDisconnectedFromUser(ConferenceSession session, Integer userID) {
        Log.d(TAG, "QBRTCSessionStateCallbackImpl onDisconnectedFromUser userID=" + userID);
    }

    private void startConversationFragment() {
        ArrayList<Integer> opponentsIDsList = callService.getOpponentsIDsList();
        boolean asListenerRole = callService.isListenerRole();
        boolean sharingScreenState = callService.isSharingScreenState();
        Bundle bundle = new Bundle();
        bundle.putIntegerArrayList(Consts.EXTRA_DIALOG_OCCUPANTS, opponentsIDsList);
        bundle.putBoolean(Consts.EXTRA_AS_LISTENER, asListenerRole);
        bundle.putBoolean("from_screen_sharing", sharingScreenState);
        bundle.putString(Consts.EXTRA_ROOM_TITLE, currentRoomTitle);
        bundle.putString(Consts.EXTRA_ROOM_ID, currentRoomID);
        ConversationFragment conversationFragment = new ConversationFragment();
        conversationFragment.setArguments(bundle);
        callService.setOnlineParticipantsChangeListener(conversationFragment);
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_containerCon,
                conversationFragment, conversationFragment.getClass().getSimpleName())
                .commitAllowingStateLoss();
    }

    @Override
    public void onBackPressed() {

    }

    ////////////////////////////// Conversation Fragment Callbacks ////////////////////////////

    @Override
    public void addClientConnectionCallback(QBRTCSessionStateCallback clientConnectionCallbacks) {
        if (currentSession != null) {
            currentSession.addSessionCallbacksListener(clientConnectionCallbacks);
        }
    }

    @Override
    public void addConnectionListener(ConnectionListener connectionCallback) {

    }

    @Override
    public void removeConnectionListener(ConnectionListener connectionCallback) {

    }

    @Override
    public void addSessionStateListener(QBRTCSessionStateCallback clientConnectionCallbacks) {

    }

    @Override
    public void removeSessionStateListener(QBRTCSessionStateCallback clientConnectionCallbacks) {

    }

    @Override
    public void addVideoTrackListener(QBRTCClientVideoTracksCallbacks<QBRTCSession> callback) {

    }

    @Override
    public void removeVideoTrackListener(QBRTCClientVideoTracksCallbacks<QBRTCSession> callback) {

    }

    @Override
    public void addSessionEventsListener(QBRTCSessionEventsCallback eventsCallback) {

    }

    @Override
    public void removeSessionEventsListener(QBRTCSessionEventsCallback eventsCallback) {

    }

    @Override
    public void addCurrentCallStateListener(CallActivity.CurrentCallStateCallback currentCallStateCallback) {

    }

    @Override
    public void removeCurrentCallStateListener(CallActivity.CurrentCallStateCallback currentCallStateCallback) {

    }

    @Override
    public void addOnChangeAudioDeviceListener(CallActivity.OnChangeAudioDevice onChangeDynamicCallback) {

    }

    @Override
    public void removeOnChangeAudioDeviceListener(CallActivity.OnChangeAudioDevice onChangeDynamicCallback) {

    }

    @Override
    public void onSetAudioEnabled(boolean isAudioEnabled) {
        callService.setAudioEnabled(isAudioEnabled);
    }

    @Override
    public void onLeaveCurrentSession() {
        leaveCurrentSession();
    }

    @Override
    public void onSwitchCamera(CameraVideoCapturer.CameraSwitchHandler cameraSwitchHandler) {
        callService.switchCamera(cameraSwitchHandler);
    }

    @Override
    public void acceptCall(Map<String, String> userInfo) {

    }

    @Override
    public void startCall(Map<String, String> userInfo) {

    }

    @Override
    public boolean isCameraFront() {
        return false;
    }

    @Override
    public boolean currentSessionExist() {
        return false;
    }

    @Override
    public List<Integer> getOpponents() {
        return null;
    }

    @Override
    public Integer getCallerId() {
        return null;
    }

    @Override
    public BaseSession.QBRTCSessionState getCurrentSessionState() {
        return null;
    }

    @Override
    public QBRTCTypes.QBRTCConnectionState getPeerChannel(Integer userId) {
        return null;
    }

    @Override
    public boolean isMediaStreamManagerExist() {
        return false;
    }

    @Override
    public boolean isCallState() {
        return false;
    }

    @Override
    public QBRTCVideoTrack getVideoTrack(Integer userId) {
        return null;
    }

    @Override
    public void onStartJoinConference() {
        callService.joinConference();
    }

    @Override
    public void onStartScreenSharing() {
        QBRTCScreenCapturer.requestPermissions(this);
    }

    @Override
    public boolean isScreenSharingState() {
        return callService.isSharingScreenState();
    }

    @Override
    public HashMap<Integer, QBRTCVideoTrack> getVideoTrackMap() {
        return callService.getVideoTrackMap();
    }

    @Override
    public void onReturnToChat() {
        ConfChatAct.startForResultFromCall(CallActCon.this, REQUEST_CODE_OPEN_CONVERSATION_CHAT,
                callService.getDialogID(), true);
    }

    @Override
    public void onManageGroup() {
        ArrayList<Integer> publishers = callService.getActivePublishers();
        publishers.add(0, getChatHelper().getCurrentUser().getId());

        ManageGroupActCon.startForResult(CallActCon.this, REQUEST_CODE_MANAGE_GROUP,
                currentRoomTitle, publishers);
    }

    @Override
    public String getDialogID() {
        return callService.getDialogID();
    }

    @Override
    public String getRoomID() {
        return callService.getRoomID();
    }

    @Override
    public String getRoomTitle() {
        return callService.getRoomTitle();
    }

    @Override
    public boolean isListenerRole() {
        return callService.isListenerRole();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i(TAG, "onActivityResult requestCode=" + requestCode + ", resultCode= " + resultCode);
        if (requestCode == QBRTCScreenCapturer.REQUEST_MEDIA_PROJECTION
                && resultCode == Activity.RESULT_OK && data != null) {
            startScreenSharing(data);
            Log.i(TAG, "Starting Screen Capture");
        } else if (requestCode == QBRTCScreenCapturer.REQUEST_MEDIA_PROJECTION && resultCode == Activity.RESULT_CANCELED) {
            callService.stopScreenSharing();
            startConversationFragment();
        }
        if (requestCode == REQUEST_CODE_OPEN_CONVERSATION_CHAT) {
            Log.d(TAG, "Returning back from ChatActivity");
        }
    }

    private void startScreenSharing(final Intent data) {
        ScreenShareFragment screenShareFragment = ScreenShareFragment.newInstance();
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_containerCon,
                screenShareFragment, ScreenShareFragment.class.getSimpleName())
                .commitAllowingStateLoss();

        callService.setVideoEnabled(true);
        callService.startScreenSharing(data);
    }

    @Override
    public void onStopPreview() {
        callService.stopScreenSharing();
        startConversationFragment();
    }

    @Override
    public void onSetVideoEnabled(boolean isNeedEnableCam) {
        callService.setVideoEnabled(isNeedEnableCam);
    }

    @Override
    public void onSwitchAudio() {

    }

    @Override
    public void onHangUpCurrentSession() {

    }

    @Override
    public void removeClientConnectionCallback(QBRTCSessionStateCallback clientConnectionCallbacks) {
        if (currentSession != null) {
            currentSession.removeSessionCallbacksListener(clientConnectionCallbacks);
        }
    }

    @Override
    public void addCurrentCallStateCallback(CallService.CurrentCallStateCallback currentCallStateCallback) {
        currentCallStateCallbackList.add(currentCallStateCallback);
    }

    @Override
    public void removeCurrentCallStateCallback(CallService.CurrentCallStateCallback currentCallStateCallback) {
        currentCallStateCallbackList.remove(currentCallStateCallback);
    }

    ////////////////////////////// ConferenceSessionCallbacks ////////////////////////////

    @Override
    public void onPublishersReceived(ArrayList<Integer> publishersList) {
        Log.d(TAG, "OnPublishersReceived connectedToJanus " + connectedToJanus);
    }

    @Override
    public void onPublisherLeft(Integer userID) {
        Log.d(TAG, "OnPublisherLeft userID" + userID);
    }

    @Override
    public void onMediaReceived(String type, boolean success) {
        Log.d(TAG, "OnMediaReceived type " + type + ", success" + success);
    }

    @Override
    public void onSlowLinkReceived(boolean uplink, int nacks) {
        Log.d(TAG, "OnSlowLinkReceived uplink " + uplink + ", nacks" + nacks);
    }

    @Override
    public void onError(WsException exception) {
        if (WsHangUpException.class.isInstance(exception) && exception.getMessage() != null && exception.getMessage().equals(ICE_FAILED_REASON)) {
            ToastUtilsCon.shortToast(CallActCon.this, exception.getMessage());
            Log.d(TAG, "OnError exception= " + exception.getMessage());
            releaseCurrentSession();
            finish();
        } else {
            ToastUtilsCon.shortToast(CallActCon.this, (WsNoResponseException.class.isInstance(exception)) ? getString(R.string.packet_failed) : exception.getMessage());
        }
    }

    @Override
    public void onSessionClosed(final ConferenceSession session) {
        Log.d(TAG, "Session " + session.getSessionID() + " start stop session");
    }

    private class CallServiceConnection implements ServiceConnection {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            CallService.stop(CallActCon.this);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CallService.CallServiceBinder binder = (CallService.CallServiceBinder) service;
            callService = binder.getService();
            if (callService.currentSessionExist()) {
                currentDialogID = callService.getDialogID();
                if (callService.getReconnectionState() != CallService.ReconnectionState.IN_PROGRESS){
                    initScreen();
                }
            } else {
                //we have already currentSession == null, so it's no reason to do further initialization
                CallService.stop(CallActCon.this);
                finish();
            }
            callService.subscribeReconnectionListener(reconnectionListener);
        }
    }

    private class ReconnectionListenerImpl implements CallService.ReconnectionListener {
        private final String tag;

        ReconnectionListenerImpl(String tag) {
            this.tag = tag;
        }

        @Override
        public void onChangedState(CallService.ReconnectionState reconnectionState) {
            switch (reconnectionState) {
                case COMPLETED:
                    reconnectingLayout.setVisibility(View.GONE);
                    for (ReconnectionCallback reconnectionCallback : reconnectionCallbacks) {
                        reconnectionCallback.completed();
                    }
                    initScreen();
                    callService.setReconnectionState(CallService.ReconnectionState.DEFAULT);
                    break;
                case IN_PROGRESS:
                    reconnectingLayout.setVisibility(View.VISIBLE);
                    for (ReconnectionCallback reconnectionCallback : reconnectionCallbacks) {
                        reconnectionCallback.inProgress();
                    }
                    break;
                case FAILED:
                    ToastUtilsCon.shortToast(CallActCon.this, R.string.reconnection_failed);
                    callService.leaveCurrentSession();
                    finish();
                    break;
            }
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 53 * hash + tag.hashCode();
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            boolean equals;
            if (obj instanceof ReconnectionListenerImpl) {
                equals = TAG.equals(((ReconnectionListenerImpl) obj).tag);
            } else {
                equals = super.equals(obj);
            }
            return equals;
        }
    }
}
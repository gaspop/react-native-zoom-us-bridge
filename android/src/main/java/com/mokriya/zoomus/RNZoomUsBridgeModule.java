package com.mokriya.zoomus;

import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Callback;

import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;

import us.zoom.sdk.InMeetingAudioController;
import us.zoom.sdk.InMeetingChatMessage;
import us.zoom.sdk.InMeetingEventHandler;
import us.zoom.sdk.InMeetingService;
import us.zoom.sdk.InMeetingServiceListener;
import us.zoom.sdk.MeetingEndReason;
import us.zoom.sdk.ZoomSDK;
import us.zoom.sdk.ZoomError;
import us.zoom.sdk.ZoomSDKInitParams;
import us.zoom.sdk.ZoomSDKInitializeListener;

import us.zoom.sdk.MeetingStatus;
import us.zoom.sdk.MeetingError;
import us.zoom.sdk.MeetingService;
import us.zoom.sdk.MeetingServiceListener;

import us.zoom.sdk.StartMeetingOptions;
import us.zoom.sdk.StartMeetingParamsWithoutLogin;

import us.zoom.sdk.JoinMeetingOptions;
import us.zoom.sdk.JoinMeetingParams;

import com.mokriya.zoomus.RNZoomUsBridgeHelper;

import java.util.List;

public class RNZoomUsBridgeModule extends ReactContextBaseJavaModule implements ZoomSDKInitializeListener, MeetingServiceListener, InMeetingServiceListener, LifecycleEventListener {

    private final static String TAG = "RNZoomUsBridge";
    private final static String IN_MEETING = "InMeeting";
    private final ReactApplicationContext reactContext;

    private Boolean isInitialized = false;
    private Promise initializePromise;
    private Promise meetingPromise;
    private Promise otherPromise;

    public RNZoomUsBridgeModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        reactContext.addLifecycleEventListener(this);
    }

    public void sendEvent(ReactContext reactContext, String eventName, WritableMap params) {
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);
    }

    @Override
    public String getName() {
        return "RNZoomUsBridge";
    }

    @ReactMethod
    public void initialize(final String appKey, final String appSecret, final Promise promise) {
        if (isInitialized) {
            promise.resolve("Already initialize Zoom SDK successfully.");
            return;
        }

        isInitialized = true;

        try {
            initializePromise = promise;

            reactContext.getCurrentActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ZoomSDK zoomSDK = ZoomSDK.getInstance();

                    ZoomSDKInitParams params = new ZoomSDKInitParams();
                    params.appKey = appKey;
                    params.appSecret = appSecret;

                    zoomSDK.initialize(reactContext.getCurrentActivity(), RNZoomUsBridgeModule.this, params);
                }
            });
        } catch (Exception ex) {
            promise.reject("ERR_UNEXPECTED_EXCEPTION", ex);
        }
    }

    @ReactMethod
    public void startMeeting(
        final String meetingNo,
        final String displayName,
        final String userId,
        final String zoomAccessToken,
        Promise promise
    ) {
        try {
            meetingPromise = promise;

            ZoomSDK zoomSDK = ZoomSDK.getInstance();
            if (!zoomSDK.isInitialized()) {
                promise.reject("ERR_ZOOM_START", "ZoomSDK has not been initialized successfully");
                return;
            }

            final MeetingService meetingService = zoomSDK.getMeetingService();
            if(meetingService.getMeetingStatus() != MeetingStatus.MEETING_STATUS_IDLE) {
                long lMeetingNo = 0;
                try {
                    lMeetingNo = Long.parseLong(meetingNo);
                } catch (NumberFormatException e) {
                    promise.reject("ERR_ZOOM_START", "Invalid meeting number: " + meetingNo);
                    return;
                }

                if(meetingService.getCurrentRtcMeetingNumber() == lMeetingNo) {
                    meetingService.returnToMeeting(reactContext.getCurrentActivity());
                    promise.resolve("Already joined zoom meeting");
                    return;
                }
            }

            StartMeetingOptions opts = new StartMeetingOptions();
            StartMeetingParamsWithoutLogin params = new StartMeetingParamsWithoutLogin();
            params.displayName = displayName;
            params.meetingNo = meetingNo;
            params.userId = userId;
            params.userType = MeetingService.USER_TYPE_API_USER;
            params.zoomAccessToken = zoomAccessToken;
            params.zoomToken = zoomAccessToken;

            int startMeetingResult = meetingService.startMeetingWithParams(reactContext.getCurrentActivity(), params, opts);
            Log.i(TAG, "startMeeting, startMeetingResult=" + startMeetingResult);

            if (startMeetingResult != MeetingError.MEETING_ERROR_SUCCESS) {
                promise.reject("ERR_ZOOM_START", "startMeeting, errorCode=" + startMeetingResult);
            }
        } catch (Exception ex) {
            promise.reject("ERR_UNEXPECTED_EXCEPTION", ex);
        }
    }

    @ReactMethod
    public void createJWT(
        final String apiKey,
        final String apiSecret,
        Promise promise
    ) {
        try {
            otherPromise = promise;

            String accessToken = RNZoomUsBridgeHelper.createJWTAccessToken(apiKey, apiSecret);
            Log.i(TAG, "accessToken=" + accessToken);

            promise.resolve(accessToken);
        } catch (Exception ex) {
            promise.reject("ERR_UNEXPECTED_EXCEPTION", ex);
        }
    }

    @ReactMethod
    public void joinMeeting(
        final String meetingNo,
        final String displayName,
        final String meetingPassword,
        Promise promise
    ) {
        try {
            meetingPromise = promise;

            ZoomSDK zoomSDK = ZoomSDK.getInstance();
            if (!zoomSDK.isInitialized()) {
                promise.reject("ERR_ZOOM_JOIN", "ZoomSDK has not been initialized successfully");
                return;
            }

            final MeetingService meetingService = zoomSDK.getMeetingService();

            JoinMeetingOptions opts = new JoinMeetingOptions();
            JoinMeetingParams params = new JoinMeetingParams();
            params.displayName = displayName;
            params.meetingNo = meetingNo;
            params.password = meetingPassword;

            int joinMeetingResult = meetingService.joinMeetingWithParams(reactContext.getCurrentActivity(), params, opts);
            Log.i(TAG, "joinMeeting, joinMeetingResult=" + joinMeetingResult);

            if (joinMeetingResult != MeetingError.MEETING_ERROR_SUCCESS) {
                promise.reject("ERR_ZOOM_JOIN", "joinMeeting, errorCode=" + joinMeetingResult);
            }
        } catch (Exception ex) {
            promise.reject("ERR_UNEXPECTED_EXCEPTION", ex);
        }
    }

    @Override
    public void onZoomSDKInitializeResult(int errorCode, int internalErrorCode) {
        Log.i(TAG, "onZoomSDKInitializeResult, errorCode=" + errorCode + ", internalErrorCode=" + internalErrorCode);
        if (errorCode != ZoomError.ZOOM_ERROR_SUCCESS) {
            initializePromise.reject(
                    "ERR_ZOOM_INITIALIZATION",
                    "Error: " + errorCode + ", internalErrorCode=" + internalErrorCode
            );
        } else {
            registerListener();
            WritableMap params = Arguments.createMap();
            sendEvent(reactContext, "SDKInitialized", params);
            initializePromise.resolve("Initialize Zoom SDK successfully.");
        }
    }

    @Override
    public void onMeetingStatusChanged(MeetingStatus meetingStatus, int errorCode, int internalErrorCode) {
        Log.i(TAG, "onMeetingStatusChanged, meetingStatus=" + meetingStatus + ", errorCode=" + errorCode + ", internalErrorCode=" + internalErrorCode);
        WritableMap params = Arguments.createMap();

        if (meetingStatus == MeetingStatus.MEETING_STATUS_FAILED) {
            if (meetingPromise != null) {
                meetingPromise.reject(
                        "ERR_ZOOM_MEETING",
                        "Error: " + errorCode + ", internalErrorCode=" + internalErrorCode
                );
                meetingPromise = null;
            }

        } else if (meetingStatus == MeetingStatus.MEETING_STATUS_DISCONNECTING) {
            //sendEvent(reactContext, "meetingEnded", params);

        } else if (meetingStatus == MeetingStatus.MEETING_STATUS_INMEETING) {
            if (meetingPromise != null) {
                sendEvent(reactContext, "meetingStarted", params);
                meetingPromise.resolve("Connected to zoom meeting");
                meetingPromise = null;
            }

        } else {
            params.putString("eventProperty", "onMeetingStatusChanged, meetingStatus=" + meetingStatus + ", errorCode=" + errorCode + ", internalErrorCode=" + internalErrorCode);
            sendEvent(reactContext, "meetingStatusChanged", params);
        }
    }

    private void registerListener() {
        Log.i(TAG, "registerListener");
        ZoomSDK zoomSDK = ZoomSDK.getInstance();
        MeetingService meetingService = zoomSDK.getMeetingService();
        if(meetingService != null) {
            meetingService.addListener(this);
        }
        InMeetingService inMeetingService = zoomSDK.getInMeetingService();
        if (inMeetingService != null) {
            inMeetingService.addListener(this);
        }
    }

    private void unregisterListener() {
        Log.i(TAG, "unregisterListener");
        ZoomSDK zoomSDK = ZoomSDK.getInstance();
        if(zoomSDK.isInitialized()) {
            MeetingService meetingService = zoomSDK.getMeetingService();
            meetingService.removeListener(this);
            InMeetingService inMeetingService = zoomSDK.getInMeetingService();
            inMeetingService.removeListener(this);
        }
    }

    @Override
    public void onCatalystInstanceDestroy() {
        unregisterListener();
    }

    // React LifeCycle
    @Override
    public void onHostDestroy() {
        unregisterListener();
    }

    @Override
    public void onHostPause() {}

    @Override
    public void onHostResume() {}

    @Override
    public void onZoomAuthIdentityExpired() {
        Log.e(TAG,"onZoomAuthIdentityExpired in init");
    }
    
    
    // InMeetingListenerService
    @Override
    public void onMeetingLeaveComplete(long l) {
        Log.i(IN_MEETING,"onMeetingLeaveComplete");

        WritableMap params = Arguments.createMap();
        params.putString("reason", getReasonText((int) l));
        sendEvent(reactContext, "meetingEnded", params);
    }

    public String getReasonText(int reason) {
        switch (reason) {
            case MeetingEndReason.END_BY_SELF:
                return "USER_LEFT";
            case MeetingEndReason.KICK_BY_HOST:
                return "KICKED_OUT_BY_HOST";
            case MeetingEndReason.END_BY_HOST:
                return "ENDED_BY_HOST";
            case MeetingEndReason.END_FOR_JBHTIMEOUT:
                return "JBH_TIMEOUT";
            case MeetingEndReason.END_FOR_FREEMEET_TIMEOUT:
                return "FREE_MEETING_TIMEOUT";
            case MeetingEndReason.END_FOR_NOATEENDEE:
                return "ENDED_FOR_NO_ATTENDEE";
            case MeetingEndReason.END_BY_HOST_START_ANOTHERMEETING:
                return "ENDED_BY_HOST_FOR_ANOTHER_MEETING";
            case MeetingEndReason.END_BY_SDK_CONNECTION_BROKEN:
                return "CONNECTION_BROKEN";
            default:
                return "UNKNOWN";
        }
    }

    @Override
    public void onMeetingNeedPasswordOrDisplayName(boolean b, boolean b1, InMeetingEventHandler inMeetingEventHandler) {
        Log.i(IN_MEETING,"onMeetingNeedPasswordOrDisplayName");
    }

    @Override
    public void onWebinarNeedRegister() {
        Log.i(IN_MEETING,"onWebinarNeedRegister");
    }

    @Override
    public void onJoinWebinarNeedUserNameAndEmail(InMeetingEventHandler inMeetingEventHandler) {
        Log.i(IN_MEETING,"onJoinWebinarNeedUser...");
    }

    @Override
    public void onMeetingNeedColseOtherMeeting(InMeetingEventHandler inMeetingEventHandler) {
        Log.i(IN_MEETING,"onMeetingNeedCloseOtherMeeting");
    }

    @Override
    public void onMeetingFail(int i, int i1) {
        Log.i(IN_MEETING,"onMeetingFail");
    }

    @Override
    public void onMeetingUserJoin(List<Long> list) {
        Log.i(IN_MEETING,"onMeetingUserJoin");
    }

    @Override
    public void onMeetingUserLeave(List<Long> list) {
        Log.i(IN_MEETING,"onMeetingUserLeave");
    }

    @Override
    public void onMeetingUserUpdated(long l) {
        Log.i(IN_MEETING,"onMeetingUserUpdated");
    }

    @Override
    public void onMeetingHostChanged(long l) {
        Log.i(IN_MEETING,"onMeetingHostChanged");
    }

    @Override
    public void onMeetingCoHostChanged(long l) {
        Log.i(IN_MEETING,"onMeetingCoHostChanged");
    }

    @Override
    public void onActiveVideoUserChanged(long l) {
        Log.i(IN_MEETING,"onActiveVideoUserChanged");
    }

    @Override
    public void onActiveSpeakerVideoUserChanged(long l) {
        Log.i(IN_MEETING,"onActiveSpeakerVideoUserChanged");
    }

    @Override
    public void onSpotlightVideoChanged(boolean b) {
        Log.i(IN_MEETING,"onSpotliteVideoChanged");
    }

    @Override
    public void onUserVideoStatusChanged(long l) {
        Log.i(IN_MEETING,"onUserVideoStatusChanged");
    }

    @Override
    public void onUserNetworkQualityChanged(long l) {
        Log.i(IN_MEETING,"onUserNetworkQualityChanged");
    }

    @Override
    public void onMicrophoneStatusError(InMeetingAudioController.MobileRTCMicrophoneError mobileRTCMicrophoneError) {
        Log.i(IN_MEETING,"OnMicrophoneStatusError");
    }

    @Override
    public void onUserAudioStatusChanged(long l) {
        Log.i(IN_MEETING,"onUserAudioStatusChanged");
    }

    @Override
    public void onHostAskUnMute(long l) {
        Log.i(IN_MEETING,"onHostAskUnMute");
    }

    @Override
    public void onHostAskStartVideo(long l) {
        Log.i(IN_MEETING,"onHostAskStartVideo");
    }

    @Override
    public void onUserAudioTypeChanged(long l) {
        Log.i(IN_MEETING,"onUserAudioTypeChanged");
    }

    @Override
    public void onMyAudioSourceTypeChanged(int i) {
        Log.i(IN_MEETING,"onMyAudioSourceTypeChanged");
    }

    @Override
    public void onLowOrRaiseHandStatusChanged(long l, boolean b) {
        Log.i(IN_MEETING,"onLowOrRaiseHandStatusChanged");
    }

    @Override
    public void onMeetingSecureKeyNotification(byte[] bytes) {
        Log.i(IN_MEETING,"onMeetingSecureKeyNotification");
    }

    @Override
    public void onChatMessageReceived(InMeetingChatMessage inMeetingChatMessage) {
        Log.i(IN_MEETING,"onChatMessageReceived");
    }

    @Override
    public void onSilentModeChanged(boolean b) {
        Log.i(IN_MEETING,"onSilentModeChanged");
    }

    @Override
    public void onFreeMeetingReminder(boolean b, boolean b1, boolean b2) {
        Log.i(IN_MEETING,"onFreeMeetingReminder");
    }

    @Override
    public void onMeetingActiveVideo(long l) {
        Log.i(IN_MEETING,"onMeetingActiveVideo");
    }

    @Override
    public void onSinkAttendeeChatPriviledgeChanged(int i) {
        Log.i(IN_MEETING,"onSinkAttendeeChat...");
    }

    @Override
    public void onSinkAllowAttendeeChatNotification(int i) {
        Log.i(IN_MEETING,"onSinkAllowAttendee...");
    }
}

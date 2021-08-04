package com.ldeng7.learningwebrtc.webrtcclient;

import android.util.Log;

import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

class LeaSDPObserver implements SdpObserver {
    private static final String LOG_TAG = LeaWebRTCClient.LOG_TAG + ":sdp";

    private final LeaWebRTCClient webRTCClient;
    private final LeaWebSocketClient webSocketClient;
    private final LeaWebSocketMessage.EPhase phase;

    LeaSDPObserver(final LeaWebRTCClient webRTCClient, final LeaWebSocketClient webSocketClient,
            final LeaWebSocketMessage.EPhase phase) {
        this.webRTCClient = webRTCClient;
        this.webSocketClient = webSocketClient;
        this.phase = phase;
    }

    @Override public void onSetSuccess() {}
    @Override public void onSetFailure(String s) {}

    @Override
    public void onCreateSuccess(SessionDescription sdp) {
        this.webRTCClient.getPeerConnection().setLocalDescription(this, sdp);
        LeaWebSocketMessage.SdpData data = new LeaWebSocketMessage.SdpData();
        data.type = sdp.type.canonicalForm();
        data.sdp = sdp.description;
        this.webSocketClient.send(this.phase, data);
    }

    @Override
    public void onCreateFailure(String s) {
        Log.e(LOG_TAG, "creation: " + s);
    }
}

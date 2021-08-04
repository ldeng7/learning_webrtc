package com.ldeng7.learningwebrtc.webrtcclient;

import android.util.Log;
import android.widget.Toast;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;

import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

class LeaWebSocketClient extends WebSocketClient {
    private static final String LOG_TAG = LeaWebRTCClient.LOG_TAG + ":websocket";

    private final LeaWebRTCClient webRTCClient;

    LeaWebSocketClient(LeaWebRTCClient webRTCClient) {
        super(webRTCClient.conf.wsUri, new Draft_17());
        this.webRTCClient = webRTCClient;
    }

    void send(LeaWebSocketMessage.EPhase phase, Object data) {
        Log.i(LOG_TAG, "send phase " + phase.ordinal());
        String json = LeaWebSocketMessage.encode(phase, data);
        super.send(json);
    }

    private void onRecvDial(final LeaWebSocketMessage.Response<Boolean> resp) {
        if (!resp.success) {
            Toast.makeText(this.webRTCClient.appContext, resp.message, Toast.LENGTH_LONG).show();
            this.webRTCClient.stop();
            return;
        }

        this.webRTCClient.createPeerConnection();
        if (!resp.data) {
            return;
        }
        this.webRTCClient.getPeerConnection().createOffer(
            new LeaSDPObserver(this.webRTCClient, this, LeaWebSocketMessage.EPhase.OFFER),
            new MediaConstraints());
    }

    private void onRecvOffer(LeaWebSocketMessage.Response<LeaWebSocketMessage.SdpData> resp) {
        final PeerConnection pc = this.webRTCClient.getPeerConnection();
        final LeaSDPObserver so = new LeaSDPObserver(this.webRTCClient, this,
            LeaWebSocketMessage.EPhase.ANS);
        pc.setRemoteDescription(so, new SessionDescription(
            SessionDescription.Type.fromCanonicalForm(resp.data.type), resp.data.sdp));
        pc.createAnswer(so, new MediaConstraints());
    }

    private void onRecvAns(LeaWebSocketMessage.Response<LeaWebSocketMessage.SdpData> resp) {
        this.webRTCClient.getPeerConnection().setRemoteDescription(
            new LeaSDPObserver(this.webRTCClient, this, LeaWebSocketMessage.EPhase.ANS),
            new SessionDescription(
                SessionDescription.Type.fromCanonicalForm(resp.data.type), resp.data.sdp));
    }

    private void onRecvCand(LeaWebSocketMessage.Response<LeaWebSocketMessage.CandidateData> resp) {
        this.webRTCClient.getPeerConnection().addIceCandidate(
            new IceCandidate(resp.data.sdpMid, resp.data.sdpMLineIndex, resp.data.candidate));
    }

    @Override
    public void onOpen(ServerHandshake shd) {
//        if (null == Looper.myLooper()) {
//            Looper.prepare();
//        }
        LeaWebSocketMessage.DialRequestData data = new LeaWebSocketMessage.DialRequestData();
        data.localUid = this.webRTCClient.conf.localUid;
        data.remoteUid = this.webRTCClient.conf.remoteUid;
        this.send(LeaWebSocketMessage.EPhase.DIAL, data);
    }

    @Override
    public void onMessage(String msg) {
        LeaWebSocketMessage.Response resp;
        try {
            resp = LeaWebSocketMessage.decode(msg);
        } catch (Exception e) {
            Log.e(LOG_TAG, "message parsing: " + e.toString());
            return;
        }
        Log.i(LOG_TAG, "recv phase " + resp.phase.ordinal());
        switch (resp.phase) {
            case DIAL:
                this.onRecvDial(resp);
                break;
            case OFFER:
                this.onRecvOffer(resp);
                break;
            case ANS:
                this.onRecvAns(resp);
                break;
            case CAND:
                this.onRecvCand(resp);
                break;
            case STOP:
                this.webRTCClient.stop();
                break;
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        Log.i(LOG_TAG, "on close: " + reason);
        this.webRTCClient.stop();
    }

    @Override
    public void onError(Exception e) {
        Log.e(LOG_TAG, "on error: " + e.toString());
    }
}

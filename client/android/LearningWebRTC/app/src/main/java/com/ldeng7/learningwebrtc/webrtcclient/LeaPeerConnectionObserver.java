package com.ldeng7.learningwebrtc.webrtcclient;

import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;

class LeaPeerConnectionObserver implements PeerConnection.Observer {
    private final LeaWebRTCClient webRTCClient;
    private final LeaWebSocketClient webSocketClient;

    LeaPeerConnectionObserver(final LeaWebRTCClient webRTCClient, final LeaWebSocketClient webSocketClient) {
        this.webRTCClient = webRTCClient;
        this.webSocketClient = webSocketClient;
    }

    @Override public void onSignalingChange(PeerConnection.SignalingState signalingState) {}
    @Override public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {}
    @Override public void onIceConnectionReceivingChange(boolean b) {}
    @Override public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}
    @Override public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}
    @Override public void onRemoveStream(MediaStream mediaStream) {}
    @Override public void onRenegotiationNeeded() {}
    @Override public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {}
    @Override public void onDataChannel(DataChannel dataChannel) {}

    @Override
    public void onIceCandidate(IceCandidate iceCandidate) {
        LeaWebSocketMessage.CandidateData data = new LeaWebSocketMessage.CandidateData();
        data.candidate = iceCandidate.sdp;
        data.sdpMid = iceCandidate.sdpMid;
        data.sdpMLineIndex = iceCandidate.sdpMLineIndex;
        this.webSocketClient.send(LeaWebSocketMessage.EPhase.CAND, data);
    }

    @Override
    public void onAddStream(MediaStream mediaStream) {
        this.webRTCClient.addRemoteStream(mediaStream);
    }
}

package com.ldeng7.learningwebrtc;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceServer;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public abstract class WebRTCClient {
    public static class Conf {
        public String localUid;
        public String remoteUid;
        public String wsServer;
        public String stunServer;
        public String turnServer;
        public String turnUser;
        public String turnCredential;
        private URI wsUri;
        private List<IceServer> iceServers;
    }

    abstract public void onStop();

    private static final String LOG_TAG = "webrtc client";

    private Conf conf;
    private final Context appContext;
    private final EglBase eglBase;
    private VideoSource videoSource;
    private AudioSource audioSource;
    private MediaStream localStream;
    private MediaStream remoteStream;
    private SurfaceTextureHelper surfaceTextureHelper;
    private VideoCapturer videoCapturer;
    private SurfaceViewRenderer localVideoView;
    private SurfaceViewRenderer remoteVideoView;
    private WebSocketClient wsClient;
    private final PeerConnectionFactory pcFactory;
    private PeerConnection peerConn;

    public WebRTCClient(Context context) {
        this.appContext = context;
        this.eglBase = EglBase.create();
        EglBase.Context eglBaseContext = this.eglBase.getEglBaseContext();

        PeerConnectionFactory.InitializationOptions pcfio = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions();
        PeerConnectionFactory.initialize(pcfio);
        Logging.enableLogToDebugOutput(Logging.Severity.LS_VERBOSE);
        AudioDeviceModule adm = JavaAudioDeviceModule.builder(this.appContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule();
        this.pcFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglBaseContext, true, true))
            .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBaseContext))
            .createPeerConnectionFactory();
        adm.release();
    }

    private void showErr(String msg) {
        Toast.makeText(this.appContext, msg, Toast.LENGTH_LONG).show();
    }

    private boolean setConf(Conf conf) {
        if (conf.localUid.length() == 0 || conf.remoteUid.length() == 0) {
            this.showErr("invalid uid");
            return false;
        }
        if (conf.stunServer.length() == 0 && conf.turnServer.length() == 0) {
            this.showErr("invalid stun/turn server");
            return false;
        }

        try {
            conf.wsUri = new URI(conf.wsServer);
        } catch (URISyntaxException e) {
            this.showErr("invalid websocket server");
            return false;
        }

        conf.iceServers = new ArrayList<>();
        if (conf.stunServer.length() != 0) {
            conf.iceServers.add(IceServer.builder(conf.stunServer).createIceServer());
        }
        if (conf.turnServer.length() != 0) {
            IceServer e = IceServer.builder(conf.turnServer)
                .setUsername(conf.turnUser)
                .setPassword(conf.turnCredential)
                .createIceServer();
            conf.iceServers.add(e);
        }

        this.conf = conf;
        return true;
    }

    private boolean createVideoCapturer() {
        CameraEnumerator e;
        if (Camera2Enumerator.isSupported(this.appContext)) {
            e = new Camera2Enumerator(this.appContext);
        } else {
            e = new Camera1Enumerator(true);
        }

        for (String n : e.getDeviceNames()) {
            if (e.isFrontFacing(n)) {
                VideoCapturer vc = e.createCapturer(n, null);
                if (vc != null) {
                    this.videoCapturer = vc;
                    return true;
                }
            }
        }
        return false;
    }

    private void loadLocalMedia() {
        this.videoSource = this.pcFactory.createVideoSource(this.videoCapturer.isScreencast());
        this.audioSource = this.pcFactory.createAudioSource(new MediaConstraints());
        this.localStream = this.pcFactory.createLocalMediaStream("ls");
        VideoTrack vt = this.pcFactory.createVideoTrack("v0", this.videoSource);
        AudioTrack at = this.pcFactory.createAudioTrack("a0", this.audioSource);
        this.localStream.addTrack(vt);
        this.localStream.addTrack(at);

        EglBase.Context eglBaseContext = this.eglBase.getEglBaseContext();
        this.surfaceTextureHelper = SurfaceTextureHelper.create("video", eglBaseContext);
        this.videoCapturer.initialize(this.surfaceTextureHelper, this.appContext,
            this.videoSource.getCapturerObserver());
        this.videoCapturer.startCapture(480, 360, 30);

        this.localVideoView.init(eglBaseContext, null);
        this.remoteVideoView.init(eglBaseContext, null);
        vt.addSink(this.localVideoView);
    }

    private void wsSend(WebRTCWsMessage.EPhase phase, Object data) {
        String json = WebRTCWsMessage.encode(phase, data);
        this.wsClient.send(json);
    }

    private SdpObserver createSdpObserver(WebRTCWsMessage.EPhase phase) {
        return new SdpObserver() {
            @Override public void onSetSuccess() {}
            @Override public void onSetFailure(String s) {}
            @Override public void onCreateSuccess(SessionDescription sdp) {
                WebRTCClient.this.peerConn.setLocalDescription(this, sdp);
                WebRTCWsMessage.SdpData data = new WebRTCWsMessage.SdpData();
                data.type = sdp.type.canonicalForm();
                data.sdp = sdp.description;
                WebRTCClient.this.wsSend(phase, data);
            }
            @Override public void onCreateFailure(String s) {
                Log.e(LOG_TAG, "sdp creation:" + s);
            }
        };
    }

    private void onWsRecvDial(WebRTCWsMessage.Response resp) {
        if (!resp.success) {
            this.stop();
            this.showErr(resp.message);
            return;
        }

        PeerConnection.Observer observer = new PeerConnection.Observer() {
            @Override public void onSignalingChange(PeerConnection.SignalingState signalingState) {}
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {}
            @Override public void onIceConnectionReceivingChange(boolean b) {}
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}
            @Override public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}
            @Override public void onRemoveStream(MediaStream mediaStream) {}
            @Override public void onRenegotiationNeeded() {}
            @Override public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {}

            @Override public void onIceCandidate(IceCandidate iceCandidate) {
                WebRTCWsMessage.CandidateData data = new WebRTCWsMessage.CandidateData();
                data.candidate = iceCandidate.sdp;
                data.sdpMid = iceCandidate.sdpMid;
                data.sdpMLineIndex = iceCandidate.sdpMLineIndex;
                WebRTCClient.this.wsSend(WebRTCWsMessage.EPhase.CAND, data);
            }
            @Override public void onAddStream(MediaStream mediaStream) {
                WebRTCClient.this.remoteStream = mediaStream;
                VideoTrack vt = mediaStream.videoTracks.get(0);
                vt.addSink(WebRTCClient.this.remoteVideoView);
            }
            @Override public void onDataChannel(DataChannel dataChannel) {
            }
        };
        this.peerConn = this.pcFactory.createPeerConnection(this.conf.iceServers, observer);
        this.peerConn.addStream(this.localStream);

        if (!(Boolean)(resp.data)) {
            return;
        }
        MediaConstraints mc = new MediaConstraints();
        this.peerConn.createOffer(this.createSdpObserver(WebRTCWsMessage.EPhase.OFFER), mc);
    }

    private void onWsRecvOffer(WebRTCWsMessage.Response resp) {
        SdpObserver observer = this.createSdpObserver(WebRTCWsMessage.EPhase.ANS);
        WebRTCWsMessage.SdpData data = (WebRTCWsMessage.SdpData)(resp.data);
        this.peerConn.setRemoteDescription(observer,
            new SessionDescription(SessionDescription.Type.fromCanonicalForm(data.type), data.sdp));
        MediaConstraints mc = new MediaConstraints();
        this.peerConn.createAnswer(observer, mc);
    }

    private void onWsRecvAns(WebRTCWsMessage.Response resp) {
        SdpObserver observer = this.createSdpObserver(WebRTCWsMessage.EPhase.ANS);
        WebRTCWsMessage.SdpData data = (WebRTCWsMessage.SdpData)(resp.data);
        this.peerConn.setRemoteDescription(observer,
            new SessionDescription(SessionDescription.Type.fromCanonicalForm(data.type), data.sdp));
    }

    private void onWsRecvCand(WebRTCWsMessage.Response resp) {
        WebRTCWsMessage.CandidateData data = (WebRTCWsMessage.CandidateData)(resp.data);
        this.peerConn.addIceCandidate(new IceCandidate(data.sdpMid, data.sdpMLineIndex, data.candidate));
    }

    private void onWsMessage(String message) {
        WebRTCWsMessage.Response resp;
        try {
            resp = WebRTCWsMessage.decode(message);
        } catch (Exception e) {
            Log.e(LOG_TAG, "websocket message parsing:" + e.toString());
            return;
        }
        switch (resp.phase) {
            case DIAL:
                this.onWsRecvDial(resp);
                break;
            case OFFER:
                this.onWsRecvOffer(resp);
                break;
            case ANS:
                this.onWsRecvAns(resp);
                break;
            case CAND:
                this.onWsRecvCand(resp);
                break;
            case STOP:
                this.stop();
                break;
        }
    }

    private void wsConnect() {
        this.wsClient = new WebSocketClient(this.conf.wsUri, new Draft_17()) {
            @Override public void onOpen(ServerHandshake shs) {
                WebRTCWsMessage.DialRequestData data = new WebRTCWsMessage.DialRequestData();
                data.localUid = WebRTCClient.this.conf.localUid;
                data.remoteUid = WebRTCClient.this.conf.remoteUid;
                WebRTCClient.this.wsSend(WebRTCWsMessage.EPhase.DIAL, data);
            }
            @Override public void onMessage(String message) {
                WebRTCClient.this.onWsMessage(message);
            }
            @Override public void onClose(int code, String reason, boolean remote) {
                Log.i(LOG_TAG, "websocket closed: " + reason);
                WebRTCClient.this.stop();
            }
            @Override public void onError(Exception e) {
                Log.e(LOG_TAG, "websocket:" + e.toString());
            }
        };
        this.wsClient.connect();
    }

    public boolean start(Conf conf, SurfaceViewRenderer localVideoView, SurfaceViewRenderer remoteVideoView) {
        if (!this.setConf(conf)) {
            return false;
        }
        if (!this.createVideoCapturer()) {
            this.showErr("camera not found");
            return false;
        }

        this.localVideoView = localVideoView;
        this.remoteVideoView = remoteVideoView;
        this.loadLocalMedia();

        this.wsConnect();
        return true;
    }

    public void stop() {
        if (null != this.peerConn) {
            this.peerConn.close();
            this.peerConn = null;
        }
        if (null != this.wsClient) {
            this.wsClient.close();
            this.wsClient = null;
        }
        if (null != this.localVideoView) {
            this.localVideoView.release();
            this.localVideoView = null;
        }
        if (null != this.remoteVideoView) {
            this.remoteVideoView.release();
            this.remoteVideoView = null;
        }
        if (null != this.videoCapturer) {
            this.videoCapturer.dispose();
            this.videoCapturer = null;
        }
        if (null != this.surfaceTextureHelper) {
            this.surfaceTextureHelper.dispose();
            this.surfaceTextureHelper = null;
        }
        if (null != this.localStream) {
            this.localStream.dispose();
            this.localStream = null;
        }
        if (null != this.remoteStream) {
            this.remoteStream.dispose();
            this.remoteStream = null;
        }
        if (null != this.videoSource) {
            this.videoSource.dispose();
            this.videoSource = null;
        }
        if (null != this.audioSource) {
            this.audioSource.dispose();
            this.audioSource = null;
        }
        this.onStop();
    }
}

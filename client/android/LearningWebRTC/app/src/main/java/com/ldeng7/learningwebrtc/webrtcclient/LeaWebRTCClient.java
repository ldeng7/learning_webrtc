package com.ldeng7.learningwebrtc.webrtcclient;

import android.content.Context;
import android.widget.Toast;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceServer;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public abstract class LeaWebRTCClient {
    public static class Conf implements Serializable {
        public String localUid;
        public String remoteUid;
        public String wsServer;
        public String stunServer;
        public String turnServer;
        public String turnUser;
        public String turnCredential;
        public boolean noVideo;

        URI wsUri;
        List<IceServer> iceServers;
    }

    abstract public void onConnected();
    abstract public void onDataChannelMsg(String s);
    abstract public void onStop();

    static final String LOG_TAG = "webrtc-client";

    Conf conf;
    final Context appContext;
    private final EglBase eglBase;
    private SurfaceViewRenderer localVideo;
    private SurfaceViewRenderer remoteVideo;
    private MediaStream localStream;
    private MediaStream remoteStream;
    private VideoSource videoSource;
    private AudioSource audioSource;
    private SurfaceTextureHelper surfaceTextureHelper;
    private VideoCapturer videoCapturer;
    private LeaWebSocketClient wsClient;
    private final PeerConnectionFactory pcFactory;
    private PeerConnection peerConn;
    private LeaDataChannel dataChan;

    public LeaWebRTCClient(final Context context) {
        this.appContext = context;
        this.eglBase = EglBase.create();
        final EglBase.Context eglBaseContext = this.eglBase.getEglBaseContext();

        PeerConnectionFactory.InitializationOptions pcfio = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions();
        PeerConnectionFactory.initialize(pcfio);
        Logging.enableLogToDebugOutput(Logging.Severity.LS_VERBOSE);

        final AudioDeviceModule adm = JavaAudioDeviceModule.builder(this.appContext)
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

    public EglBase getEglBase() {
        return this.eglBase;
    }

    public LeaDataChannel getDataChannel() {
        return this.dataChan;
    }

    PeerConnection getPeerConnection() {
        return this.peerConn;
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
            final IceServer e = IceServer.builder(conf.turnServer)
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
            if (!e.isFrontFacing(n)) {
                continue;
            }
            VideoCapturer vc = e.createCapturer(n, null);
            if (vc != null) {
                this.videoCapturer = vc;
                return true;
            }
        }
        return false;
    }

    private void loadLocalMedia() {
        this.localStream = this.pcFactory.createLocalMediaStream("ls");
        if (!this.conf.noVideo) {
            this.videoSource = this.pcFactory.createVideoSource(this.videoCapturer.isScreencast());
            final VideoTrack vt = this.pcFactory.createVideoTrack("v0", this.videoSource);
            this.localStream.addTrack(vt);
            vt.addSink(this.localVideo);
        }
        this.audioSource = this.pcFactory.createAudioSource(new MediaConstraints());
        final AudioTrack at = this.pcFactory.createAudioTrack("a0", this.audioSource);
        this.localStream.addTrack(at);

        if (!this.conf.noVideo) {
            this.surfaceTextureHelper = SurfaceTextureHelper.create("video",
                this.eglBase.getEglBaseContext());
            this.videoCapturer.initialize(this.surfaceTextureHelper, this.appContext,
                this.videoSource.getCapturerObserver());
            this.videoCapturer.startCapture(480, 360, 30);
        }
    }

    void createPeerConnection() {
        final PeerConnection.Observer pco = new LeaPeerConnectionObserver(this, this.wsClient);
        this.peerConn = this.pcFactory.createPeerConnection(this.conf.iceServers, pco);
        this.peerConn.addStream(this.localStream);
        this.dataChan = new LeaDataChannel(this, 1, "dc1");
    }

    void addRemoteStream(MediaStream mediaStream) {
        this.remoteStream = mediaStream;
        if (!this.conf.noVideo && mediaStream.videoTracks.size() >= 1) {
            mediaStream.videoTracks.get(0).addSink(this.remoteVideo);
        }
        this.onConnected();
    }

    public boolean start(Conf conf, SurfaceViewRenderer localVideo, SurfaceViewRenderer remoteVideo) {
        if (!this.setConf(conf)) {
            return false;
        }
        if (!conf.noVideo && !this.createVideoCapturer()) {
            this.showErr("camera not found");
            return false;
        }

        this.localVideo = localVideo;
        this.remoteVideo = remoteVideo;
        this.loadLocalMedia();

        this.wsClient = new LeaWebSocketClient(this);
        this.wsClient.connect();
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
        if (null != this.videoCapturer) {
            this.videoCapturer.dispose();
            this.videoCapturer = null;
        }
        if (null != this.surfaceTextureHelper) {
            this.surfaceTextureHelper.dispose();
            this.surfaceTextureHelper = null;
        }
        if (null != this.videoSource) {
            this.videoSource.dispose();
            this.videoSource = null;
        }
        if (null != this.audioSource) {
            this.audioSource.dispose();
            this.audioSource = null;
        }
        if (null != this.localStream) {
            this.localStream.dispose();
            this.localStream = null;
        }
        if (null != this.remoteStream) {
            this.remoteStream.dispose();
            this.remoteStream = null;
        }
        this.onStop();
    }
}

package com.ldeng7.learningwebrtc.webrtcclient;

import org.webrtc.DataChannel;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class LeaDataChannel implements DataChannel.Observer {
    private final LeaWebRTCClient webRTCClient;
    private final DataChannel ch;

    LeaDataChannel(final LeaWebRTCClient webRTCClient, int id, String label) {
        this.webRTCClient = webRTCClient;
        DataChannel.Init dci = new DataChannel.Init();
        dci.negotiated = true;
        dci.id = id;
        this.ch = webRTCClient.getPeerConnection().createDataChannel(label, dci);
        this.ch.registerObserver(this);
    }

    @Override public void onBufferedAmountChange(long l) {}
    @Override public void onStateChange() {}

    @Override
    public void onMessage(DataChannel.Buffer buffer) {
        String s = StandardCharsets.UTF_8.decode(buffer.data).toString();
        this.webRTCClient.onDataChannelMsg(s);
    }

    public void send(String s) {
        final ByteBuffer b = StandardCharsets.UTF_8.encode(s);
        this.ch.send(new DataChannel.Buffer(b, false));
    }
}

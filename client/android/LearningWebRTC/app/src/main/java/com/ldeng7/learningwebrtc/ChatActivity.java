package com.ldeng7.learningwebrtc;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;

import com.ldeng7.learningwebrtc.databinding.ActivityChatBinding;
import com.ldeng7.learningwebrtc.webrtcclient.LeaWebRTCClient;

import org.webrtc.EglBase;

public class ChatActivity extends Activity {
    final static String INTENT_KEY_CONF = "conf";

    private ActivityChatBinding binding;
    private Handler mainHandler;
    private LeaWebRTCClient webRTCClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = ActivityChatBinding.inflate(this.getLayoutInflater());
        this.mainHandler = new Handler();
        this.setContentView(this.binding.getRoot());

        this.binding.dataChannelSendButton.setOnClickListener(view ->
            this.webRTCClient.getDataChannel().send(
                this.binding.dataChannelEditText.getText().toString()));

        this.webRTCClient = new LeaWebRTCClient(this.getApplicationContext()) {
            @Override public void onConnected() {
                ChatActivity.this.onWebRTCClientConnected();
            }

            @Override public void onDataChannelMsg(String s) {
                ChatActivity.this.mainHandler.post(() ->
                    ChatActivity.this.binding.dataChannelText.setText(s));
            }

            @Override public void onStop() {
                ChatActivity.this.webRTCClient = null;
                ChatActivity.this.mainHandler.post(ChatActivity.this::finish);
            }
        };
        final EglBase.Context eglBaseContext = this.webRTCClient.getEglBase().getEglBaseContext();
        this.binding.localVideo.init(eglBaseContext, null);
        this.binding.remoteVideo.init(eglBaseContext, null);

        new Thread(() -> {
            LeaWebRTCClient.Conf conf = (LeaWebRTCClient.Conf) (
                this.getIntent().getSerializableExtra(INTENT_KEY_CONF));
            if (!this.webRTCClient.start(conf, this.binding.localVideo, this.binding.remoteVideo)) {
                this.mainHandler.post(this::finish);
            }
        }).start();
    }

    private void onWebRTCClientConnected() {
        this.mainHandler.post(() -> {
            this.binding.dataChannelSendButton.setEnabled(true);
        });
    }

    @Override
    protected void onDestroy() {
        if (null != this.webRTCClient) {
            this.webRTCClient.stop();
            this.webRTCClient = null;
        }
        super.onDestroy();
    }
}

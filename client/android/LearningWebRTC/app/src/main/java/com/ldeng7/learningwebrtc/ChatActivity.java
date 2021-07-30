package com.ldeng7.learningwebrtc;

import android.app.Activity;
import android.os.Bundle;

import com.ldeng7.learningwebrtc.databinding.ActivityChatBinding;

public class ChatActivity extends Activity {
    final static String INTENT_KEY_CONF = "conf";

    private ActivityChatBinding binding;
    private WebRTCClient webRTCClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = ActivityChatBinding.inflate(this.getLayoutInflater());
        this.setContentView(this.binding.getRoot());

        this.binding.stopButton.setOnClickListener(view -> this.webRTCClient.stop());

        this.webRTCClient = new WebRTCClient(this.getApplicationContext()) {
            @Override public void onStop() {
                ChatActivity.this.onWebRTCClientStop();
            }
        };

        WebRTCClient.Conf conf = new WebRTCClient.Conf();
        String[] confStrs = this.getIntent().getStringArrayExtra(INTENT_KEY_CONF);
        conf.localUid = confStrs[0];
        conf.remoteUid = confStrs[1];
        conf.wsServer = confStrs[2];
        conf.stunServer = confStrs[3];
        conf.turnServer = confStrs[4];
        conf.turnUser = confStrs[5];
        conf.turnCredential = confStrs[6];
        if (!this.webRTCClient.start(conf, this.binding.localVideo, this.binding.remoteVideo)) {
            this.finish();
        }
    }

    private void onWebRTCClientStop() {
        this.webRTCClient = null;
        this.finish();
    }

    @Override
    protected void onDestroy() {
        if (null != this.webRTCClient) {
            this.webRTCClient.stop();
        }
        super.onDestroy();
    }
}

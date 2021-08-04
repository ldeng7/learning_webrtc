package com.ldeng7.learningwebrtc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.ldeng7.learningwebrtc.databinding.ActivityPrepareBinding;
import com.ldeng7.learningwebrtc.webrtcclient.LeaWebRTCClient;

public class PrepareActivity extends Activity {
    private ActivityPrepareBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = ActivityPrepareBinding.inflate(this.getLayoutInflater());
        this.setContentView(this.binding.getRoot());

        this.binding.startButton.setOnClickListener(view -> this.onStartButtonClick());
    }

    private void onStartButtonClick() {
        LeaWebRTCClient.Conf conf = new LeaWebRTCClient.Conf();
        conf.localUid = this.binding.localUidEditText.getText().toString();
        conf.remoteUid = this.binding.remoteUidEditText.getText().toString();
        conf.wsServer = this.binding.wsServerEditText.getText().toString();
        conf.stunServer = this.binding.stunServerEditText.getText().toString();
        conf.turnServer = this.binding.turnServerEditText.getText().toString();
        conf.turnUser = this.binding.turnUserEditText.getText().toString();
        conf.turnCredential = this.binding.turnCredEditText.getText().toString();
        conf.noVideo = this.binding.noVideoCheck.isChecked();

        Intent intent = new Intent();
        intent.putExtra(ChatActivity.INTENT_KEY_CONF, conf);
        intent.setClass(this, ChatActivity.class);
        this.startActivityForResult(intent, -1);
    }
}

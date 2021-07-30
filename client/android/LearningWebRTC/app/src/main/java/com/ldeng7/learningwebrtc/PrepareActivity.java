package com.ldeng7.learningwebrtc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.ldeng7.learningwebrtc.databinding.ActivityPrepareBinding;

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
        String[] opts = {
            this.binding.localUidInput.getText().toString(),
            this.binding.remoteUidInput.getText().toString(),
            this.binding.wsServerInput.getText().toString(),
            this.binding.stunServerInput.getText().toString(),
            this.binding.turnServerInput.getText().toString(),
            this.binding.turnUserInput.getText().toString(),
            this.binding.turnCredInput.getText().toString(),
        };
        Intent intent = new Intent();
        intent.putExtra(ChatActivity.INTENT_KEY_CONF, opts);
        intent.setClass(this, ChatActivity.class);
        this.startActivityForResult(intent, -1);
    }
}

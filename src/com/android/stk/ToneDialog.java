/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.stk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.internal.telephony.cat.CatLog;
import com.android.internal.telephony.cat.TextMessage;
import com.android.internal.telephony.cat.ToneSettings;

/**
 * Activity used to display tone dialog.
 *
 */
public class ToneDialog extends Activity {
    TextMessage toneMsg = null;
    ToneSettings settings = null;
    int mSlotId = -1;
    private AlertDialog mAlertDialog;

    private static final String LOG_TAG = new Object(){}.getClass().getEnclosingClass().getName();

    Handler mToneStopper = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                case MSG_ID_STOP_TONE:
                    CatLog.d(LOG_TAG, "Finishing Tone dialog activity");
                    finish();
                    break;
                }
            }
    };

    // Message id to signal tone duration timeout.
    private static final int MSG_ID_STOP_TONE = 0xda;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        CatLog.d(LOG_TAG, "onCreate");
        initFromIntent(getIntent());
        // Register receiver
        IntentFilter filter = new IntentFilter();

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.stk_tone_dialog, null);
        alertDialogBuilder.setView(dialogView);

        TextView tv = (TextView) dialogView.findViewById(R.id.message);
        ImageView iv = (ImageView) dialogView.findViewById(R.id.icon);

        // set text and icon
        if ((null == toneMsg) || (null == toneMsg.text) || (toneMsg.text.equals(""))) {
            CatLog.d(LOG_TAG, "onCreate - null tone text");
        } else {
            tv.setText(toneMsg.text);
        }

        if (toneMsg.icon == null) {
            iv.setImageResource(com.android.internal.R.drawable.ic_volume);
        } else {
            iv.setImageBitmap(toneMsg.icon);
        }

        if (toneMsg.iconSelfExplanatory && toneMsg.icon != null) {
            tv.setVisibility(View.GONE);
        }

        if (null == settings) {
            CatLog.d(LOG_TAG, "onCreate - null settings - finish");
            finish();
            return;
        }

        int timeout = StkApp.calculateDurationInMilis(settings.duration);
        if (timeout == 0) {
            timeout = StkApp.TONE_DEFAULT_TIMEOUT;
        }
        mToneStopper.sendEmptyMessageDelayed(MSG_ID_STOP_TONE, timeout);

        alertDialogBuilder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        sendStopTone();
                        finish();
                    }
                });

        mAlertDialog = alertDialogBuilder.create();
        mAlertDialog.show();
    }

    @Override
    protected void onDestroy() {
        CatLog.d(LOG_TAG, "onDestroy");
        mToneStopper.removeMessages(MSG_ID_STOP_TONE);
        super.onDestroy();

        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
    }

    private void initFromIntent(Intent intent) {
        if (intent == null) {
            finish();
        }
        toneMsg = intent.getParcelableExtra("TEXT");
        settings = intent.getParcelableExtra("TONE");
        mSlotId = intent.getIntExtra(StkAppService.SLOT_ID, -1);
    }

    // Send stop playing tone to StkAppService, when user presses back key.
    private void sendStopTone() {
        Bundle args = new Bundle();
        args.putInt(StkAppService.OPCODE, StkAppService.OP_STOP_TONE_USER);
        args.putInt(StkAppService.SLOT_ID, mSlotId);
        startService(new Intent(this, StkAppService.class).putExtras(args));
    }
}

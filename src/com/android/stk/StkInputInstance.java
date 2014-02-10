/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.stk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Selection;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.TextView.BufferType;

import com.android.internal.telephony.cat.FontSize;
import com.android.internal.telephony.cat.Input;
import com.android.internal.telephony.cat.CatLog;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Context;
import android.provider.Settings.System;

import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.IccCard;


/**
 * Display a request for a text input a long with a text edit form.
 */
public class StkInputInstance {

    Activity parent;
	
    // Members
    int mState;
    Context mContext;
    Input mStkInput = null;
    
    //private CharSequence tempString = null;

    // Constants
    static final int STATE_TEXT = 1;
    static final int STATE_YES_NO = 2;

    static final String YES_STR_RESPONSE = "YES";
    static final String NO_STR_RESPONSE = "NO";

    // Font size factor values.
    static final float NORMAL_FONT_FACTOR = 1;
    static final float LARGE_FONT_FACTOR = 2;
    static final float SMALL_FONT_FACTOR = (1 / 2);

    // message id for time out
    static final int MSG_ID_TIMEOUT = 1;
    static final int MSG_ID_FINISH = 2;
    
    private static final int DELAY_TIME = 300;
    StkAppService appService = StkAppService.getInstance();
    
    private static final String LOGTAG = "Stk-IA ";
    boolean mbSendResp = false;
    int mSimId = -1;

    Handler mTimeoutHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
            case MSG_ID_TIMEOUT:
                sendResponse(StkAppService.RES_ID_TIMEOUT);
                CatLog.d(LOGTAG, "Msg timeout to finish");
                parent.finish();
                break;
            case MSG_ID_FINISH:
                CatLog.d(LOGTAG, "Msg finish to finish");
                parent.finish();
                break;
            }
        }
    };

    public boolean handleClick(View v, EditText edit)
    {
        boolean result = true;
        String input = null;

        switch (v.getId()) {
        case R.id.button_ok:
            // Check that text entered is valid .
            if (!verfiyTypedText(edit)) {
                result = false;
                CatLog.d(LOGTAG, "handleClick, invalid text");
                return result;
            }
            input = edit.getText().toString();
            break;
        // Yes/No layout buttons.
        case R.id.button_yes:
            input = YES_STR_RESPONSE;
            break;
        case R.id.button_no:
            input = NO_STR_RESPONSE;
            break;
        }

        CatLog.d(LOGTAG, "handleClick, ready to response");
        sendResponse(StkAppService.RES_ID_INPUT, input, false);
        return result;
    }

    void handleResume()
    {
        CatLog.d(LOGTAG, "handleResume - mbSendResp[" + mbSendResp + "], sim id: " + mSimId);
        startTimeOut();
    }

    void handlePause()
    {
        CatLog.d(LOGTAG, "handlePause - mbSendResp[" + mbSendResp + "]");
        cancelTimeOut();
    }

    void handleDestroy()
    {
        CatLog.d(LOGTAG, "handleDestroy - before Send End Session mbSendResp[" + mbSendResp + "]");
        if (!mbSendResp) {
            CatLog.d(LOGTAG, "handleDestroy - Send End Session");
            sendResponse(StkAppService.RES_ID_END_SESSION);
        }
    }

    boolean handleKeyDown(int keyCode, KeyEvent event)
    {
        boolean need_finish = false;
        switch (keyCode) {
        case KeyEvent.KEYCODE_BACK:
            CatLog.d(LOGTAG, "handleKeyDown - KEYCODE_BACK");
            sendResponse(StkAppService.RES_ID_BACKWARD, null, false);
            need_finish = true;
            break;
        }
            return need_finish;
    }

    void sendResponse(int resId) {
        sendResponse(resId, null, false);
    }

    void sendResponse(int resId, String input, boolean help) {
        if (mSimId == -1) {
            CatLog.d(LOGTAG, "sim id is invalid");
            return;
        }

        if (StkAppService.getInstance() == null) {
            CatLog.d(LOGTAG, "StkAppService is null, Ignore response: id is " + resId);
            return;
        }

        CatLog.d(LOGTAG, "sendResponse resID[" + resId + "] input[" + input + "] help[" + help + "]");
        mbSendResp = true;
        Bundle args = new Bundle();
        int[] op = new int[2];
        op[0] = StkAppService.OP_RESPONSE;
        op[1] = mSimId;
        args.putIntArray(StkAppService.OPCODE, op);
        args.putInt(StkAppService.RES_ID, resId);
        if (input != null) {
            args.putString(StkAppService.INPUT, input);
        }
        args.putBoolean(StkAppService.HELP, help);
        mContext.startService(new Intent(mContext, StkAppService.class)
                .putExtras(args));
    }

    void handleBeforeTextChanged(CharSequence s, int start, int count,
            int after) {
    }

    void handleTextChanged(CharSequence s, int start, int before, int count) {
        // Reset timeout.
        startTimeOut();
    }

    void handleAfterTextChanged(Editable s, EditText edit) {

        int iStart = edit.getSelectionStart();
        int iEnd = edit.getSelectionEnd();
        if(mStkInput.ucs2 == true){
            if(mStkInput.maxLen > 239/2)
                mStkInput.maxLen = 239/2;
        }
        if (s.length() > mStkInput.maxLen){
            s.delete(mStkInput.maxLen, s.length());
            edit.setText(s);
            int temp = 0;
            if (iStart > 0){
            	temp = iStart > (mStkInput.maxLen)? mStkInput.maxLen:(iStart -1);
            }
            edit.setSelection(temp);
        }
    }

    private boolean verfiyTypedText(EditText edit) {
        // If not enough input was typed in stay on the edit screen.
        if (edit.getText().length() < mStkInput.minLen) {
            return false;
        }

        return true;
    }

    void cancelTimeOut() {
        mTimeoutHandler.removeMessages(MSG_ID_TIMEOUT);
        mTimeoutHandler.removeMessages(MSG_ID_FINISH);
    }

    void startTimeOut() {
        int duration = StkApp.calculateDurationInMilis(mStkInput.duration);
        
        if (duration <= 0) {
            duration = StkApp.UI_TIMEOUT;
        }
        cancelTimeOut();
        mTimeoutHandler.sendMessageDelayed(mTimeoutHandler
                .obtainMessage(MSG_ID_TIMEOUT), duration);
    }

    void delayFinish() {
        mTimeoutHandler.sendMessageDelayed(mTimeoutHandler
                .obtainMessage(MSG_ID_FINISH), DELAY_TIME);
    }
    void handleConfigInputDisplay(TextView prompt, EditText edit, TextView numOfCharsView, TextView inTypeView, View YN, View Normal)
    {
        int inTypeId = R.string.alphabet;

        prompt.setText(mStkInput.text);

		// Handle specific global and text attributes.
        switch (mState) {
        case STATE_TEXT:
            int maxLen = mStkInput.maxLen;
            int minLen = mStkInput.minLen;

            if(mStkInput.ucs2 == true){
                if(mStkInput.maxLen > 239/2)
                    maxLen = mStkInput.maxLen = 239/2;
            }

            // Set number of chars info.
            String lengthLimit = String.valueOf(minLen);
            if (maxLen != minLen) {
                lengthLimit = minLen + " - " + maxLen;
            }
            numOfCharsView.setText(lengthLimit);

            if (!mStkInput.echo) {
                  edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            // Set default text if present.
            if (mStkInput.defaultText != null) {
                edit.setText(mStkInput.defaultText);
            } else {
                // make sure the text is cleared
                edit.setText("", BufferType.EDITABLE);
            }

            break;
        case STATE_YES_NO:
            // Set display mode - normal / yes-no layout
            YN.setVisibility(View.VISIBLE);
            Normal.setVisibility(View.GONE);
            break;
        }

        // Set input type (alphabet/digit) info close to the InText form.
        if (mStkInput.digitOnly) {
            edit.setKeyListener(StkDigitsKeyListener.getInstance());
            inTypeId = R.string.digits;
        }
        inTypeView.setText(inTypeId);
    }

    private float getFontSizeFactor(FontSize size) {
        final float[] fontSizes =
            {NORMAL_FONT_FACTOR, LARGE_FONT_FACTOR, SMALL_FONT_FACTOR};

        return fontSizes[size.ordinal()];
    }
}

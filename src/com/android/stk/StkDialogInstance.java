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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.cat.CatLog;
import com.android.internal.telephony.cat.TextMessage;

/**
 * AlretDialog used for DISPLAY TEXT commands.
 *
 */
public class StkDialogInstance {
    // members
    Activity parent;
    
    TextMessage mTextMsg;
    private static final String LOGTAG = "Stk-DI ";

    protected boolean mbSendResp = false;

    StkAppService appService = StkAppService.getInstance();
    
    Handler mTimeoutHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
            case MSG_ID_TIMEOUT:
                sendResponse(StkAppService.RES_ID_TIMEOUT);
                parent.finish();
                break;
            }
        }
    };

    // message id for time out
    private static final int MSG_ID_TIMEOUT = 1;

    // buttons id
    public static final int OK_BUTTON = R.id.button_ok;
    public static final int CANCEL_BUTTON = R.id.button_cancel;

    protected int mSimId = -1;
    protected boolean mbCancelTimer = true;

    protected void handleOnNewIntent(Intent intent)
    {
        CatLog.d(LOGTAG, "onNewIntent - mbSendResp[" + mbSendResp + "]");
        
        initFromIntent(intent);
        if (mTextMsg == null) {
            parent.finish();
            return;
        }
    }

    protected void handleOnClick(View v)
    {
        String input = null;

        switch (v.getId()) {
        case OK_BUTTON:
            CatLog.d(LOGTAG, "OK Clicked!, mSimId: " + mSimId);
            sendResponse(StkAppService.RES_ID_CONFIRM, true);
            parent.finish();
            break;
        case CANCEL_BUTTON:
            CatLog.d(LOGTAG, "Cancel Clicked!, mSimId: " + mSimId);
            sendResponse(StkAppService.RES_ID_CONFIRM, false);
            parent.finish();
            break;
        }
    }

    protected boolean handleOnKeyDown(int keyCode, KeyEvent event)
    {
        switch (keyCode) {
        case KeyEvent.KEYCODE_BACK:
            CatLog.d(LOGTAG, "onKeyDown - KEYCODE_BACK");
            sendResponse(StkAppService.RES_ID_BACKWARD);
            parent.finish();
            break;
        }
        return false;
    }

    protected void handleOnResume()
    {
        CatLog.d(LOGTAG, "onResume - mbSendResp[" + mbSendResp + "], sim id: " + mSimId);                
        startTimeOut(mTextMsg.userClear);
    }

    protected void handleOnPause()
    {
        CatLog.d(LOGTAG, "onPause, sim id: " + mSimId + " : " + mbCancelTimer);
        if(true == mbCancelTimer) {
            cancelTimeOut();
        }
    }

    protected void handleOnDestroy()
    {
        CatLog.d(LOGTAG, "onDestroy - before Send CONFIRM false mbSendResp[" + mbSendResp + "], sim id: " + mSimId);
        if (!mbSendResp) {
            sendResponse(StkAppService.RES_ID_CONFIRM, false);
        }
    }

    protected void sendResponse(int resId, boolean confirmed) {
        if (mSimId == -1) {
            CatLog.d(LOGTAG, "sim id is invalid");
            return;
        }

        if (StkAppService.getInstance() == null) {
            CatLog.d(LOGTAG, "Ignore response: id is " + resId);
            return;
        }
        
        CatLog.d(LOGTAG, "sendResponse resID[" + resId + "] confirmed[" + confirmed + "]");
        
        mbSendResp = true;
        Bundle args = new Bundle();
        int[] op = new int[2];
        op[0] = StkAppService.OP_RESPONSE;
        op[1] = mSimId;
        args.putIntArray(StkAppService.OPCODE, op);
        args.putInt(StkAppService.RES_ID, resId);
        args.putBoolean(StkAppService.CONFIRMATION, confirmed);
        StkAppService service = StkAppService.getInstance();
        if(service != null) {
            service.sendMessageToServiceHandler(
                    StkAppService.OP_RESPONSE, args, mSimId);
        }
    }

    protected void sendResponse(int resId) {
        sendResponse(resId, true);
    }

    protected void initFromIntent(Intent intent) {

        if (intent != null) {
            mTextMsg = intent.getParcelableExtra("TEXT");
            mSimId = intent.getIntExtra(StkAppService.CMD_SIM_ID, -1);
        } else {
            parent.finish();
        }

        CatLog.d(LOGTAG, "initFromIntent - [" + mTextMsg + "], sim id: " + mSimId);
    }

    protected void cancelTimeOut() {
        CatLog.d(LOGTAG, "cancelTimeOut: " + mSimId);
        mTimeoutHandler.removeMessages(MSG_ID_TIMEOUT);
    }

    protected void startTimeOut(boolean waitForUserToClear) {
        // Reset timeout.
        cancelTimeOut();
        int dialogDuration = StkApp.calculateDurationInMilis(mTextMsg.duration);
        // case 1  userClear = true & responseNeeded = false,
        // Dialog always exists. 
        if(mTextMsg.userClear == true && mTextMsg.responseNeeded == false) {
            return;
        } else {
            // userClear = false. will dissapear after a while.
            if (dialogDuration == 0) {
                if (waitForUserToClear) {
                    dialogDuration = StkApp.DISP_TEXT_WAIT_FOR_USER_TIMEOUT;
                } else {
                    dialogDuration = StkApp.DISP_TEXT_CLEAR_AFTER_DELAY_TIMEOUT;
                }
            }
            CatLog.d(LOGTAG, "startTimeOut: " + mSimId);
            mTimeoutHandler.sendMessageDelayed(mTimeoutHandler
                .obtainMessage(MSG_ID_TIMEOUT), dialogDuration);
        }
    }
}

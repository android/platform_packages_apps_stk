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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.TextView.BufferType;
import com.android.internal.telephony.cat.CatLog;
import com.android.internal.telephony.cat.FontSize;
import com.android.internal.telephony.cat.Input;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.IccCardConstants;

/**
 * Display a request for a text input a long with a text edit form.
 */
public class StkInputActivityIII extends Activity implements View.OnClickListener,
        TextWatcher {

    // Members
    private EditText mTextIn = null;
    private TextView mPromptView = null;
    private View mYesNoLayout = null;
    private View mNormalLayout = null;

    // Constants
    private static final String LOGTAG = "Stk3-IA ";
    private StkInputInstance mInputInstance = new StkInputInstance();

    private final IntentFilter mSIMStateChangeFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);

    private final BroadcastReceiver mSIMStateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {

                String simState = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                int simId = intent.getIntExtra(com.android.internal.telephony.PhoneConstants.SIM_ID_KEY, -1);

                CatLog.d(LOGTAG, "mSIMStateChangeReceiver() - simId[" + simId + "]  state[" + simState + "]");
                if ((simId == com.android.internal.telephony.PhoneConstants.SIM_ID_3) && (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(simState))) {
                    mInputInstance.cancelTimeOut();
                    CatLog.d(LOGTAG, "mSIMStateChangeReceiver, mState: " + mInputInstance.mState);
                    if (mInputInstance.mState == StkInputInstance.STATE_YES_NO) {
                        mInputInstance.sendResponse(StkAppService.RES_ID_INPUT, StkInputInstance.NO_STR_RESPONSE, false);
                    } else {
                        mInputInstance.sendResponse(StkAppService.RES_ID_END_SESSION, null, false);
                    }
                    finish();
                }
            }
        }
    };

    // Click listener to handle buttons press..
    public void onClick(View v) {
        if (mInputInstance.handleClick(v, mTextIn))
        {
            finish();
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        CatLog.d(LOGTAG, "onCreate - mbSendResp[" + mInputInstance.mbSendResp + "]");
        mInputInstance.parent = this;

        // Set the layout for this activity.
        requestWindowFeature(Window.FEATURE_LEFT_ICON);
        setContentView(R.layout.stk_input);

        // Initialize members
        mTextIn = (EditText) this.findViewById(R.id.in_text);
        mPromptView = (TextView) this.findViewById(R.id.prompt);

        // Set buttons listeners.
        Button okButton = (Button) findViewById(R.id.button_ok);
        Button yesButton = (Button) findViewById(R.id.button_yes);
        Button noButton = (Button) findViewById(R.id.button_no);

        okButton.setOnClickListener(this);
        yesButton.setOnClickListener(this);
        noButton.setOnClickListener(this);

        mYesNoLayout = findViewById(R.id.yes_no_layout);
        mNormalLayout = findViewById(R.id.normal_layout);

        // Get the calling intent type: text/key, and setup the
        // display parameters.
        Intent intent = getIntent();
        if (intent != null) {
            mInputInstance.mStkInput = intent.getParcelableExtra("INPUT");
			mInputInstance.mSimId = intent.getIntExtra(StkAppService.CMD_SIM_ID, -1);
			CatLog.d(LOGTAG, "onCreate - sim id: " + mInputInstance.mSimId);
            if (mInputInstance.mStkInput == null) {
                finish();
            } else {
                mInputInstance.mState = mInputInstance.mStkInput.yesNo ? StkInputInstance.STATE_YES_NO : StkInputInstance.STATE_TEXT;
                configInputDisplay();
            }
        } else {
            finish();
        }
        mInputInstance.mContext = getBaseContext();

        registerReceiver(mSIMStateChangeReceiver, mSIMStateChangeFilter);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        mTextIn.addTextChangedListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mInputInstance.handleResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mInputInstance.handlePause();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        mInputInstance.handleDestroy();
        unregisterReceiver(mSIMStateChangeReceiver);
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mInputInstance.handleKeyDown(keyCode, event))
        {
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(android.view.Menu.NONE, StkApp.MENU_ID_END_SESSION, 1,
                R.string.menu_end_session);
        menu.add(0, StkApp.MENU_ID_HELP, 2, R.string.help);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(StkApp.MENU_ID_END_SESSION).setVisible(true);
        menu.findItem(StkApp.MENU_ID_HELP).setVisible(mInputInstance.mStkInput.helpAvailable);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case StkApp.MENU_ID_END_SESSION:
            mInputInstance.sendResponse(StkAppService.RES_ID_END_SESSION);
            finish();
            return true;
        case StkApp.MENU_ID_HELP:
            mInputInstance.sendResponse(StkAppService.RES_ID_INPUT, "", true);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void beforeTextChanged(CharSequence s, int start, int count,
            int after) {
    	mInputInstance.handleBeforeTextChanged(s, start, count, after);
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // Reset timeout.
        mInputInstance.handleTextChanged(s, start, before, count);
    }

    public void afterTextChanged(Editable s) {
        mInputInstance.handleAfterTextChanged(s, mTextIn);
    }

    private void configInputDisplay() {
        TextView numOfCharsView = (TextView) findViewById(R.id.num_of_chars);
        TextView inTypeView = (TextView) findViewById(R.id.input_type);

        if (mInputInstance.mStkInput.icon != null) {
            setFeatureDrawable(Window.FEATURE_LEFT_ICON, new BitmapDrawable(
                    mInputInstance.mStkInput.icon));
        }

        mInputInstance.handleConfigInputDisplay(mPromptView, mTextIn, numOfCharsView, inTypeView, mYesNoLayout, mNormalLayout);
    }
}

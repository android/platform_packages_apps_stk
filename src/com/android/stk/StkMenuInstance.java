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
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ServiceManager;
import android.os.RemoteException;
import android.view.MenuItem;
import android.view.View;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.internal.telephony.cat.Item;
import com.android.internal.telephony.cat.Menu;
import com.android.internal.telephony.cat.CatLog;

import com.android.internal.telephony.PhoneConstants;
import android.telephony.TelephonyManager;

import android.view.Gravity;
import android.widget.Toast;

import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;

import com.android.internal.telephony.cat.AppInterface;
import com.android.internal.telephony.ITelephony;

/**
 * ListActivity used for displaying STK menus. These can be SET UP MENU and
 * SELECT ITEM menus. This activity is started multiple times with different
 * menu content.
 *
 */
class StkMenuInstance {
    Menu mStkMenu = null;
    int mState = STATE_MAIN;
    boolean mAcceptUsersInput = true;
    Context mContext = null;
    int mSimId = -1;

    private static final String LOGTAG = "Stk-MI";
    StkAppService appService = StkAppService.getInstance();
    Activity parent;

    // Internal state values
    static final int STATE_MAIN = 1;
    static final int STATE_SECONDARY = 2;
    static final int STATE_END = 3;

    // Finish result
    static final int FINISH_CAUSE_NO = 1;
    static final int FINISH_CAUSE_NULL_SERVICE = 2;
    static final int FINISH_CAUSE_NULL_MENU = 3;

    // message id for time out
    private static final int MSG_ID_TIMEOUT = 1;

    protected boolean mSendResp = false;
    
    Handler mTimeoutHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MSG_ID_TIMEOUT:
                    mAcceptUsersInput = false;
                    sendResponse(StkAppService.RES_ID_TIMEOUT);
                    break;
            }
        }
    };

    public void handleOnCreate(Context context, Intent intent)
    {
        boolean nothing = true;

        mContext = context;
        nothing = initFromIntent(intent);
        mAcceptUsersInput = true;
        if (false == nothing) {
    	    CatLog.d(LOGTAG, "finish!");            
            parent.finish();
        }
    }

    public void handleNewIntent(Intent intent)
    {
        boolean nothing = true;

        nothing = initFromIntent(intent);
        mAcceptUsersInput = true;
        mSendResp = false;
        if (false == nothing) {
            CatLog.d(LOGTAG, "finish!");
            parent.finish();
        }
    }

    public void handleListItemClick(int position, ProgressBar bar)
    {
        if (!mAcceptUsersInput) {
            CatLog.d(LOGTAG, "mAcceptUsersInput:false");
            return;
        }

        Item item = getSelectedItem(position);
        if (item == null) {
            CatLog.d(LOGTAG, "Item is null");
            return;
        }
        sendResponse(StkAppService.RES_ID_MENU_SELECTION, item.id, false);
        mAcceptUsersInput = false;
        CatLog.d(LOGTAG, "Id: " + item.id + ", mAcceptUsersInput: " + mAcceptUsersInput);
        bar.setVisibility(View.VISIBLE);
        bar.setIndeterminate(true);
    }

    public boolean handleKeyDown(int keyCode, KeyEvent event)
	{
        CatLog.d(LOGTAG, "mAcceptUsersInput: " + mAcceptUsersInput);
        if (!mAcceptUsersInput) {
            return true;
        }

        switch (keyCode) {
        case KeyEvent.KEYCODE_BACK:
            CatLog.d(LOGTAG, "KEYCODE_BACK - mState[" + mState + "]");
            switch (mState) {
            case StkMenuInstance.STATE_SECONDARY:
                CatLog.d(LOGTAG, "STATE_SECONDARY");                
                cancelTimeOut();
                mAcceptUsersInput = false;
                sendResponse(StkAppService.RES_ID_BACKWARD);
                return true;
            case StkMenuInstance.STATE_MAIN:
                break;
            }
            break;
        }

		return false;
	}

    public int handleResume(ImageView iconView, TextView textView, ListActivity list, ProgressBar bar)
    {
        int nothing = FINISH_CAUSE_NO;

        do {
            if(appService == null) {
                CatLog.d(LOGTAG, "can not launch stk menu 'cause null StkAppService");
                nothing = FINISH_CAUSE_NULL_SERVICE;
                break;
            }
            appService.indicateMenuVisibility(true, mSimId);
            mStkMenu = appService.getMenu(mSimId);
            if (mStkMenu == null) {
                nothing = FINISH_CAUSE_NULL_MENU;
                break;
            }
            displayMenu(iconView, textView, list);
            startTimeOut();
            // whenever this activity is resumed after a sub activity was invoked
            // (Browser, In call screen) switch back to main state and enable
            // user's input;
            if (!mAcceptUsersInput) {
                mState = STATE_MAIN;
                mAcceptUsersInput = true;
            }

            // make sure the progress bar is not shown.
            bar.setIndeterminate(false);
            bar.setVisibility(View.GONE);
        } while(false);

        CatLog.d(LOGTAG, "handleResume, result: " + nothing);
        return nothing;
    }

	public void handlePause()
	{
	    appService.indicateMenuVisibility(false, mSimId);
        cancelTimeOut();
	}

    public boolean handlePrepareOptionMenu(android.view.Menu menu)
    {
        boolean helpVisible = false;
        boolean mainVisible = false;

        if (mState == STATE_SECONDARY) {
            mainVisible = true;
        }
        if (mStkMenu != null) {
            helpVisible = mStkMenu.helpAvailable;
        }

        if(mainVisible) {
            menu.findItem(StkApp.MENU_ID_END_SESSION).setTitle(R.string.menu_end_session);
        }
        menu.findItem(StkApp.MENU_ID_END_SESSION).setVisible(mainVisible);
        
        if(helpVisible) {
            menu.findItem(StkApp.MENU_ID_HELP).setTitle(R.string.help);
        }
        menu.findItem(StkApp.MENU_ID_HELP).setVisible(helpVisible);
        return true;
	}

    public boolean handleOptionItemSelected(MenuItem item, ProgressBar bar)
    {
        if (!mAcceptUsersInput) {
            return true;
        }
        switch (item.getItemId()) {
        case StkApp.MENU_ID_END_SESSION:
            cancelTimeOut();
            mAcceptUsersInput = false;
            // send session end response.
            sendResponse(StkAppService.RES_ID_END_SESSION);
            return true;
        case StkApp.MENU_ID_HELP:
            cancelTimeOut();
            mAcceptUsersInput = false;
            // Cannot get the current position, just consider as 0.
            int position = 0;
            Item stkItem = getSelectedItem(position);
            if (stkItem == null) {
                break;
            }
            // send help needed response.
            sendResponse(StkAppService.RES_ID_MENU_SELECTION, stkItem.id, true);
            return true;
        }

		return false;
    }

    void cancelTimeOut() {
        CatLog.d(LOGTAG, "cancelTimeOut: " + mSimId);
        mTimeoutHandler.removeMessages(MSG_ID_TIMEOUT);
    }

    void startTimeOut() {
        if (mState == STATE_SECONDARY) {
            // Reset timeout.
            cancelTimeOut();
            CatLog.d(LOGTAG, "startTimeOut: " + mSimId);
            mTimeoutHandler.sendMessageDelayed(mTimeoutHandler
                    .obtainMessage(MSG_ID_TIMEOUT), StkApp.UI_TIMEOUT);
        }
    }

    // Bind list adapter to the items list.
    void displayMenu(ImageView iconView, TextView textView, ListActivity list) {
        int simCount = TelephonyManager.from(mContext).getSimCount();

        if (mStkMenu != null) {
            // Display title & title icon
            if (mStkMenu.titleIcon != null) {
                iconView.setImageBitmap(mStkMenu.titleIcon);
                iconView.setVisibility(View.VISIBLE);
            } else {
                iconView.setVisibility(View.GONE);
            }
            if (!mStkMenu.titleIconSelfExplanatory) {
                textView.setVisibility(View.VISIBLE);
                if (mStkMenu.title == null) {
                    int resId = R.string.app_name;
                    if (1 < simCount) {
                        if (mSimId == PhoneConstants.SIM_ID_1) {
                            resId = R.string.appI_name;
                        } else if (mSimId == PhoneConstants.SIM_ID_2) {
                            resId = R.string.appII_name;
                        } else if (mSimId == PhoneConstants.SIM_ID_3) {
                            resId = R.string.appIII_name;
                        } else {
                            resId = R.string.appIV_name;                        
                        }
                    } else {
                        resId = R.string.app_name;
                    }                     
                    textView.setText(resId);
                } else {
                    textView.setText(mStkMenu.title);
                }
            } else {
                textView.setVisibility(View.INVISIBLE);
            }
            // create an array adapter for the menu list
            int i = 0;
            for (i = 0; i < mStkMenu.items.size();)
            {
                if (mStkMenu.items.get(i) == null)
                {
                    mStkMenu.items.remove(i);
                    CatLog.d(LOGTAG, "Remove null item from menu.items");
                    continue;
                }
                ++i;
            }
            if (mStkMenu.items.size() == 0)
            {
                CatLog.d(LOGTAG, "should not display the SET_UP_MENU because no item");
            }
            else
            {
                StkMenuAdapter adapter = new StkMenuAdapter(parent,
                        mStkMenu.items, mStkMenu.itemsIconSelfExplanatory);            
                // Bind menu list to the new adapter.
                list.setListAdapter(adapter);
                // Set default item
                list.setSelection(mStkMenu.defaultItem);
            }
        }
    }

    private boolean initFromIntent(Intent intent) {
        boolean nothing = true;
        if (intent != null) {
            mState = intent.getIntExtra("STATE", STATE_MAIN);
            mSimId = intent.getIntExtra(StkAppService.CMD_SIM_ID, -1);
            CatLog.d(LOGTAG, "sim id: " + mSimId + "state: " + mState);
            if(mState == STATE_END) {
                nothing = false;
            }
        } else {
            nothing = false;
        }
        return nothing;
    }

    Item getSelectedItem(int position) {
        Item item = null;
        if (mStkMenu != null) {
            try {
                item = mStkMenu.items.get(position);
            } catch (IndexOutOfBoundsException e) {
                if (StkApp.DBG) {
                    CatLog.d(LOGTAG, "IOOBE Invalid menu");
                }
            } catch (NullPointerException e) {
                if (StkApp.DBG) {
                    CatLog.d(LOGTAG, "NPE Invalid menu");
                }
            }
        }
        return item;
    }

    void sendResponse(int resId) {
        sendResponse(resId, 0, false);
    }

    void sendResponse(int resId, int itemId, boolean help) {
        CatLog.d(LOGTAG, "sendResponse resID[" + resId + "] itemId[" + itemId + "] help[" + help + "]");
        mSendResp = true;
        Bundle args = new Bundle();
        int[] op = new int[2];
        op[0] = StkAppService.OP_RESPONSE;
        op[1] = mSimId;
        args.putIntArray(StkAppService.OPCODE, op);
        args.putInt(StkAppService.RES_ID, resId);
        args.putInt(StkAppService.MENU_SELECTION, itemId);
        args.putBoolean(StkAppService.HELP, help);
        mContext.startService(new Intent(mContext, StkAppService.class)
                .putExtras(args));
    }
    
    void showTextToast(Context context, String msg) {
        Toast toast = Toast.makeText(context, msg, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.BOTTOM, 0, 0);
        toast.show();
    }
    
    boolean isOnLockMode(int sim_id){
        int simState = TelephonyManager.getDefault().getSimState(sim_id);
        CatLog.d(LOGTAG, "lock mode is " + simState);        
        if(TelephonyManager.SIM_STATE_PIN_REQUIRED == simState || TelephonyManager.SIM_STATE_PUK_REQUIRED == simState || TelephonyManager.SIM_STATE_NETWORK_LOCKED == simState)
            return true;
        else
            return false;
    }
}

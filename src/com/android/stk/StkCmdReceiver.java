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

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.cat.CatLog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.android.internal.telephony.cat.AppInterface;

/**
 * Receiver class to get STK intents, broadcasted by telephony layer.
 *
 */
public class StkCmdReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action.equals(AppInterface.CAT_CMD_ACTION)) {
            handleCommandMessage(context, intent, PhoneConstants.SIM_ID_1);
        } else if (action.equals(AppInterface.CAT_CMD_ACTION_2)) {
            handleCommandMessage(context, intent, PhoneConstants.SIM_ID_2);
        } if (action.equals(AppInterface.CAT_CMD_ACTION_3)) {
            handleCommandMessage(context, intent, PhoneConstants.SIM_ID_3);
        } else if (action.equals(AppInterface.CAT_CMD_ACTION_4)) {
            handleCommandMessage(context, intent, PhoneConstants.SIM_ID_4);
        } else if (action.equals(AppInterface.CAT_SESSION_END_ACTION)) {
            handleSessionEnd(context, intent, PhoneConstants.SIM_ID_1);
        } else if (action.equals(AppInterface.CAT_SESSION_END_ACTION_2)) {
            handleSessionEnd(context, intent, PhoneConstants.SIM_ID_2);
        } else if (action.equals(AppInterface.CAT_SESSION_END_ACTION_3)) {
            handleSessionEnd(context, intent, PhoneConstants.SIM_ID_3);
        } else if (action.equals(AppInterface.CAT_SESSION_END_ACTION_4)) {
            handleSessionEnd(context, intent, PhoneConstants.SIM_ID_4);
        }
    }

    private void handleCommandMessage(Context context, Intent intent, int sim) {
        Bundle args = new Bundle();
        int[] op = new int[2];
        op[0] = StkAppService.OP_CMD;
        op[1] = sim;
        args.putIntArray(StkAppService.OPCODE, op);
        args.putParcelable(StkAppService.CMD_MSG, intent
                .getParcelableExtra("STK CMD"));
        CatLog.d("StkCmdReceiver", "handleCommandMessage, args: " + args);
        CatLog.d("StkCmdReceiver", "handleCommandMessage, sim id: " + sim);
        Intent toService = new Intent(context, StkAppService.class);
        toService.putExtras(args);
        context.startService(toService);
    }

    private void handleSessionEnd(Context context, Intent intent, int sim) {
        Bundle args = new Bundle();
        int[] op = new int[2];
        op[0] = StkAppService.OP_END_SESSION;
        op[1] = sim;
        args.putIntArray(StkAppService.OPCODE, op);
        CatLog.d("StkCmdReceiver", "handleSessionEnd, sim id: " + sim);
        Intent toService = new Intent(context, StkAppService.class);
        toService.putExtras(args);
        context.startService(toService);
    }
}

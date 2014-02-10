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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.cat.CatLog;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.IccCardConstants;

/**
 * Boot completed receiver. used to reset the app install state every time the
 * device boots.
 *
 */
public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String LOGTAG = "Stk-BCR ";
    private static final int SIM_COUNT_2 = 2;
    private static final int SIM_COUNT_3 = 3;
    private static final int SIM_COUNT_4 = 4;
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        StkAppInstaller appInstaller = StkAppInstaller.getInstance();
        int simCount = TelephonyManager.from(context).getSimCount();
        // make sure the app icon is removed every time the device boots.
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            Bundle args = new Bundle();
            int[] op = new int[2];
            op[0] = StkAppService.OP_BOOT_COMPLETED;
            args.putIntArray(StkAppService.OPCODE, op);
            context.startService(new Intent(context, StkAppService.class)
                    .putExtras(args));
            for(int i = 0; i < simCount; i++) {
                CatLog.d(LOGTAG, "Initialize the app state in launcher. sim: " + i);
                appInstaller.install(context, i);
                appInstaller.unInstall(context, i);
            }
            CatLog.d(LOGTAG, "[ACTION_BOOT_COMPLETED]");
        }
    }
}

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
import android.content.Intent;
import android.os.Bundle;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.cat.CatLog;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.widget.Toast;

/**
 * Launcher class. Serve as the app's MAIN activity, send an intent to the
 * StkAppService and finish.
 *
 */
public class StkLauncherActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StkAppService service = StkAppService.getInstance();
        
        if (service != null && service.StkQueryAvailable(PhoneConstants.SIM_ID_1) != StkAppService.STK_AVAIL_AVAILABLE)
        {
            int resId = 0;
            int simState = TelephonyManager.getDefault().getSimState(PhoneConstants.SIM_ID_1);
            
            CatLog.d("Stk-LA", "Not available simState:"+simState);
            if(TelephonyManager.SIM_STATE_PIN_REQUIRED == simState || TelephonyManager.SIM_STATE_PUK_REQUIRED == simState || TelephonyManager.SIM_STATE_NETWORK_LOCKED == simState)
                resId = R.string.lable_sim_not_ready;
            else
                resId = R.string.lable_not_available;
            Toast toast = Toast.makeText(getApplicationContext(), resId, Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.BOTTOM, 0, 0);
            toast.show();
            finish();
            return;
        }

        Bundle args = new Bundle();
        int[] op = new int[2];
        op[0] = StkAppService.OP_LAUNCH_APP;
        op[1] = PhoneConstants.SIM_ID_1;
        args.putIntArray(StkAppService.OPCODE, op);
        startService(new Intent(this, StkAppService.class).putExtras(args));

        finish();
    }
}

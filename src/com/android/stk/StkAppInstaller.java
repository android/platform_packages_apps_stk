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

import com.android.internal.telephony.cat.CatLog;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyProperties;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.TelephonyManager;
import android.os.SystemProperties;

/**
 * Application installer for SIM Toolkit.
 *
 */
class StkAppInstaller {
    private static StkAppInstaller mInstance = null;
    public static final int STK_NOT_INSTALLED = 1;
    public static final int STK_INSTALLED = 2;

    private static int[] miSTKInstalled = null;  // 1 -not_ready, 2-ready
    private static final String STK1_LAUNCHER_ACTIVITY = "com.android.stk.StkLauncherActivity";
    private static final String STK2_LAUNCHER_ACTIVITY = "com.android.stk.StkLauncherActivityII";
    private static final String STK3_LAUNCHER_ACTIVITY = "com.android.stk.StkLauncherActivityIII";
    private static final String STK4_LAUNCHER_ACTIVITY = "com.android.stk.StkLauncherActivityIV";
    private static final String LOGTAG = "StkAppInstaller";

    public static StkAppInstaller getInstance(){
        if (null == mInstance)
        {
            int simCount = SystemProperties.getInt(TelephonyProperties.PROPERTY_SIM_COUNT, 1);
            miSTKInstalled = new int[simCount];
            mInstance = new StkAppInstaller();
        }
        return mInstance;
    }

    private StkAppInstaller() {
        CatLog.d(LOGTAG, "init");
        for (int i = 0; i < miSTKInstalled.length; i++) {
            miSTKInstalled[i] = -1;
        }
    }

    void install(Context context, int sim_id) {
        if(null == mInstance) {
            mInstance = new StkAppInstaller();
        }
        setAppState(context, true, sim_id);
    }

    void unInstall(Context context, int sim_id) {
        if(null == mInstance) {
            mInstance = new StkAppInstaller();
        }
        setAppState(context, false, sim_id);
    }

    private static void setAppState(Context context, boolean install, int sim_id) {
        CatLog.d(LOGTAG, "[setAppState]+");
        if (context == null) {
            return;
        }
        PackageManager pm = context.getPackageManager();
        if (pm == null) {
            return;
        }
        // check that STK app package is known to the PackageManager
        String class_name = STK1_LAUNCHER_ACTIVITY;
        switch (sim_id)
        {
            case PhoneConstants.SIM_ID_1:
                class_name = STK1_LAUNCHER_ACTIVITY;
                break;
            case PhoneConstants.SIM_ID_2:
                class_name = STK2_LAUNCHER_ACTIVITY;
                break;
            case PhoneConstants.SIM_ID_3:
                class_name = STK3_LAUNCHER_ACTIVITY;
                break;
            case PhoneConstants.SIM_ID_4:
                class_name = STK4_LAUNCHER_ACTIVITY;
                break;
            default:
                CatLog.d(LOGTAG, "setAppState, ready to return because sim id " + sim_id +" is wrong.");
                return;
        }
        ComponentName cName = new ComponentName("com.android.stk", class_name);
        int state = install ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

        CatLog.d(LOGTAG, "setAppState - class name: " + class_name + ", curState[" + miSTKInstalled[sim_id] + "] to state[" + install + "]" );

        if (((PackageManager.COMPONENT_ENABLED_STATE_ENABLED == state) && (STK_INSTALLED == miSTKInstalled[sim_id])) || 
            ((PackageManager.COMPONENT_ENABLED_STATE_DISABLED == state) && (STK_NOT_INSTALLED == miSTKInstalled[sim_id])))
        {
            CatLog.d(LOGTAG, "Stk " + sim_id + " - Need not change app state!!");
        } else {
            CatLog.d(LOGTAG, "Stk " + sim_id + "- StkAppInstaller - Change app state[" + install + "]");

            miSTKInstalled[sim_id] = install ? STK_INSTALLED : STK_NOT_INSTALLED;

            try {
                pm.setComponentEnabledSetting(cName, state, PackageManager.DONT_KILL_APP);
            } catch (Exception e) {
                CatLog.d(LOGTAG, "Could not change STK app state");
            }
        }
        CatLog.d(LOGTAG, "[setAppState]-");
    }
    public static int getIsInstalled(int sim_id) {
        CatLog.d(LOGTAG, "getIsInstalled, sim id: " + sim_id + ", install status: " + miSTKInstalled[sim_id]);
        return miSTKInstalled[sim_id];
    }
}

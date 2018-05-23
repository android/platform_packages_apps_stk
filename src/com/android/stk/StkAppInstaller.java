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

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;
import android.os.ServiceManager;

import com.android.internal.telephony.cat.CatLog;

/**
 * Application installer for SIM Toolkit.
 *
 */
final class StkAppInstaller {
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    private static final String LOG_TAG = "StkAppInstaller";

    private StkAppInstaller() {
    }

    static void installOrUpdate(Context context, String label) {
        IPackageManager pm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
        if (pm != null) {
            ComponentName component = new ComponentName(context, StkMain.class);
            int userId = context.getUserId();
            try {
                if (label != null) {
                    pm.overrideActivityLabel(component, label, userId);
                } else {
                    pm.restoreActivityLabel(component, userId);
                }
                if (DEBUG) CatLog.d(LOG_TAG, "Set the label of the launcher activity to " + label);
                setAppState(pm, component, userId, true);
            } catch (RemoteException e) {
                CatLog.e(LOG_TAG, "Failed to enable SIM Toolkit or change the label");
            }
        }
    }

    static void uninstall(Context context) {
        IPackageManager pm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
        if (pm != null) {
            ComponentName component = new ComponentName(context, StkMain.class);
            try {
                setAppState(pm, component, context.getUserId(), false);
            } catch (RemoteException e) {
                CatLog.e(LOG_TAG, "Failed to disable SIM Toolkit");
            }
        }
    }

    static void setAppState(IPackageManager pm, ComponentName component, int userId, boolean enable)
            throws RemoteException {
        int current = pm.getComponentEnabledSetting(component, userId);
        int expected = enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        if (current != expected) {
            pm.setComponentEnabledSetting(component, expected, PackageManager.DONT_KILL_APP,
                    userId);
            if (DEBUG) CatLog.d(LOG_TAG, "SIM Toolkit is " + (enable ? "enabled" : "disabled"));
        }
    }
}

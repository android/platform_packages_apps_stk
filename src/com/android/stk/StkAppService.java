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

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.telephony.cat.AppInterface;
import com.android.internal.telephony.cat.Menu;
import com.android.internal.telephony.cat.Item;
import com.android.internal.telephony.cat.Input;
import com.android.internal.telephony.cat.ResultCode;
import com.android.internal.telephony.cat.CatCmdMessage;
import com.android.internal.telephony.cat.CatCmdMessage.BrowserSettings;
import com.android.internal.telephony.cat.CatLog;
import com.android.internal.telephony.cat.CatResponseMessage;
import com.android.internal.telephony.cat.TextMessage;
import com.android.internal.telephony.PhoneConstants;

import java.util.LinkedList;

/**
 * SIM toolkit application level service. Interacts with Telephopny messages,
 * application's launch and user input from STK UI elements.
 *
 */
public class StkAppService extends Service implements Runnable {

    // members
    protected class StkContext {
        protected CatCmdMessage mMainCmd = null;
        protected CatCmdMessage mCurrentCmd = null;
        protected CatCmdMessage mCurrentMenuCmd = null;
        protected Menu mCurrentMenu = null;
        protected String lastSelectedItem = null;
        protected boolean mMenuIsVisible = false;
        protected boolean responseNeeded = true;
        protected boolean launchBrowser = false;
        protected BrowserSettings mBrowserSettings = null;
        protected boolean mSetupMenuCalled = false; 
        protected int mAvailable = STK_AVAIL_INIT;
        protected LinkedList<DelayedCmd> mCmdsQ = null;
        protected boolean mCmdInProgress = false;
    }

    private volatile Looper mServiceLooper;
    private volatile ServiceHandler mServiceHandler;
    private Context mContext = null;
    static int STK_SIM_NUM = 4;
    private NotificationManager mNotificationManager = null;
    static StkAppService sInstance = null;
    private AppInterface[] mStkService = new AppInterface[STK_SIM_NUM];
    private StkContext[] mStkContext = new StkContext[STK_SIM_NUM];

    // Used for setting FLAG_ACTIVITY_NO_USER_ACTION when
    // creating an intent.
    private enum InitiatedByUserAction {
        yes,            // The action was started via a user initiated action
        unknown,        // Not known for sure if user initated the action
    }

    // constants
    static final String OPCODE = "op";
    static final String CMD_MSG = "cmd message";
    static final String RES_ID = "response id";
    static final String MENU_SELECTION = "menu selection";
    static final String INPUT = "input";
    static final String HELP = "help";
    static final String CONFIRMATION = "confirm";
    static final String CHOICE = "choice";
    static final String CMD_SIM_ID = "sim id";

    // operations ids for different service functionality.
    static final int OP_CMD = 1;
    static final int OP_RESPONSE = 2;
    static final int OP_LAUNCH_APP = 3;
    static final int OP_END_SESSION = 4;
    static final int OP_BOOT_COMPLETED = 5;
    private static final int OP_DELAYED_MSG = 6;

    // Response ids
    static final int RES_ID_MENU_SELECTION = 11;
    static final int RES_ID_INPUT = 12;
    static final int RES_ID_CONFIRM = 13;
    static final int RES_ID_DONE = 14;
    static final int RES_ID_CHOICE = 15;

    static final int RES_ID_TIMEOUT = 20;
    static final int RES_ID_BACKWARD = 21;
    static final int RES_ID_END_SESSION = 22;
    static final int RES_ID_EXIT = 23;

    static final int YES = 1;
    static final int NO = 0;

    private static final String PACKAGE_NAME = "com.android.stk";
    private static final String STK1_MENU_ACTIVITY_NAME = PACKAGE_NAME + ".StkMenuActivity";
    private static final String STK2_MENU_ACTIVITY_NAME = PACKAGE_NAME + ".StkMenuActivityII";
    private static final String STK3_MENU_ACTIVITY_NAME = PACKAGE_NAME + ".StkMenuActivityIII";
    private static final String STK4_MENU_ACTIVITY_NAME = PACKAGE_NAME + ".StkMenuActivityIV";
    private static final String STK1_INPUT_ACTIVITY_NAME = PACKAGE_NAME + ".StkInputActivity";
    private static final String STK2_INPUT_ACTIVITY_NAME = PACKAGE_NAME + ".StkInputActivityII";
    private static final String STK3_INPUT_ACTIVITY_NAME = PACKAGE_NAME + ".StkInputActivityIII";
    private static final String STK4_INPUT_ACTIVITY_NAME = PACKAGE_NAME + ".StkInputActivityIV";

    // Notification id used to display Idle Mode text in NotificationManager.
    private static final int STK1_NOTIFICATION_ID = 333;
    private static final int STK2_NOTIFICATION_ID = 334;
    private static final int STK3_NOTIFICATION_ID = 335;
    private static final int STK4_NOTIFICATION_ID = 336;
    private static final String LOGTAG = "Stk-SAS ";

    public static final int STK_AVAIL_INIT = -1;
    public static final int STK_AVAIL_NOT_AVAILABLE = 0;
    public static final int STK_AVAIL_AVAILABLE = 1;    
    Thread serviceThread = null;
    // Inner class used for queuing telephony messages (proactive commands,
    // session end) while the service is busy processing a previous message.
    private class DelayedCmd {
        // members
        int id;
        CatCmdMessage msg;
        int sim_id;

        DelayedCmd(int id, CatCmdMessage msg, int sim_id) {
            this.id = id;
            this.msg = msg;
            this.sim_id = sim_id;
        }
    }

    @Override
    public void onCreate() {
        CatLog.d(LOGTAG, " onCreate()+");
        // Initialize members
        int i = 0;
        int sim_id = PhoneConstants.SIM_ID_1;
        for (i = 0; i < STK_SIM_NUM; i++)
        {
            switch (i)
            {
                case 1:
                    sim_id = PhoneConstants.SIM_ID_2;
                    break;
                case 2:
                    sim_id = PhoneConstants.SIM_ID_3;
                    break;
                case 3:
                    sim_id = PhoneConstants.SIM_ID_4;
                    break;
                default:
                    break;
            }
            mStkService[i] = com.android.internal.telephony.cat.CatService.getInstance(sim_id);

            mStkContext[i] = new StkContext();
            mStkContext[i].mAvailable = STK_AVAIL_INIT;
            mStkContext[i].mCmdsQ = new LinkedList<DelayedCmd>();
        }
        
        serviceThread = new Thread(null, this, "Stk App Service");
        serviceThread.start();
        mContext = getBaseContext();
        mNotificationManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        sInstance = this;
    }

    @Override
    public void onStart(Intent intent, int startId) {
        if (intent == null) {
            CatLog.d(this, "StkAppService onStart intent is null so return");
            return;
        }

        Bundle args = intent.getExtras();
        if (args == null) {
            CatLog.d(this, "StkAppService onStart args is null so return");
            return;
        }

        int[] op = args.getIntArray(OPCODE);
        if (op == null)
        {
            CatLog.d(this, "StkAppService onStart op is null  return. args: " + args);
            return;
        }
        int sim_id = op[1];
        CatLog.d(this, "StkAppService onStart sim id: " + sim_id + ", op: " + op[0] + ", " + args);
        if ((sim_id >= 0 && sim_id < STK_SIM_NUM) && mStkService[sim_id] == null)
        {
            mStkService[sim_id] = com.android.internal.telephony.cat.CatService.getInstance(sim_id);
            if (mStkService[sim_id] == null) {
                CatLog.d(this, "StkAppService onStart mStkService is null  return, please check op code. Make sure it did not come from CatService");
                if (op[0] == OP_CMD) {
                    stopSelf();
                }
                return;
            }
        }

        waitForLooper();
        // onStart() method can be passed a null intent
        // TODO: replace onStart() with onStartCommand()
        if (intent == null) {
            return;
        }

        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = op[0];
        msg.arg2 = sim_id;
        switch(msg.arg1) {
        case OP_CMD:
            msg.obj = args.getParcelable(CMD_MSG);
            break;
        case OP_RESPONSE:
            msg.obj = args;
            /* falls through */
        case OP_LAUNCH_APP:
        case OP_END_SESSION:
        case OP_BOOT_COMPLETED:
            break;
        default:
            return;
        }
        mServiceHandler.sendMessage(msg);
    }

    @Override
    public void onDestroy() {
        waitForLooper();
        mServiceLooper.quit();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void run() {
        Looper.prepare();

        mServiceLooper = Looper.myLooper();
        mServiceHandler = new ServiceHandler();

        Looper.loop();
    }

    /*
     * Package api used by StkMenuActivity to indicate if its on the foreground.
     */
    void indicateMenuVisibility(boolean visibility, int sim_id) {
        if (sim_id >=0 && sim_id < STK_SIM_NUM) {
            mStkContext[sim_id].mMenuIsVisible = visibility;
        }
    }
    
    /*
     * Package api used by StkMenuActivity to get its Menu parameter.
     */
    Menu getMenu(int sim_id) {
        CatLog.d(LOGTAG, "StkAppService, getMenu, sim id: " + sim_id);
        if (sim_id >=0 && sim_id < STK_SIM_NUM)
            return mStkContext[sim_id].mCurrentMenu;
        else
            return null;
    }

    /*
     * Package api used by UI Activities and Dialogs to communicate directly
     * with the service to deliver state information and parameters.
     */
    static StkAppService getInstance() {
        return sInstance;
    }

    private void waitForLooper() {
        while (mServiceHandler == null) {
            synchronized (this) {
                try {
                    wait(100);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    private final class ServiceHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if(null == msg)
            {
                CatLog.d(LOGTAG, "ServiceHandler handleMessage msg is null");
                return;
            }
            int opcode = msg.arg1;
            int sim_id = msg.arg2;

            CatLog.d(LOGTAG, "handleMessage opcode[" + opcode + "], sim id[" + sim_id + "]");
            if (opcode == OP_CMD && msg.obj != null && ((CatCmdMessage)msg.obj).getCmdType() != null) {
                CatLog.d(LOGTAG, "handleMessage cmdName[" + ((CatCmdMessage)msg.obj).getCmdType().name() + "]");
            }

            switch (opcode) {
            case OP_LAUNCH_APP:
                if (mStkContext[sim_id].mMainCmd == null) {
                    // nothing todo when no SET UP MENU command didn't arrive.
                    return;
                }
                CatLog.d(LOGTAG, "handleMessage OP_LAUNCH_APP - mCmdInProgress[" + mStkContext[sim_id].mCmdInProgress + "]");
                if(mStkContext[sim_id].mCurrentMenu == mStkContext[sim_id].mMainCmd.getMenu() ||  mStkContext[sim_id].mCurrentMenu == null) {
                    launchMenuActivity(null, sim_id);
                } else {
                    launchMenuActivity(mStkContext[sim_id].mCurrentMenu, sim_id);
                }
                break;
            case OP_CMD:
                CatLog.d(LOGTAG, "[OP_CMD]");
                CatCmdMessage cmdMsg = (CatCmdMessage) msg.obj;
                // There are two types of commands:
                // 1. Interactive - user's response is required.
                // 2. Informative - display a message, no interaction with the user.
                //
                // Informative commands can be handled immediately without any delay.
                // Interactive commands can't override each other. So if a command
                // is already in progress, we need to queue the next command until
                // the user has responded or a timeout expired.
                if (!isCmdInteractive(cmdMsg)) {
                    handleCmd(cmdMsg, sim_id);
                } else {
                    if (!mStkContext[sim_id].mCmdInProgress) {
                        mStkContext[sim_id].mCmdInProgress = true;
                        handleCmd((CatCmdMessage) msg.obj, sim_id);
                    } else {
                        CatLog.d(LOGTAG, "[OP_CMD][Normal][Not DISPLAY_TEXT][Interactive][in progress]");
                        mStkContext[sim_id].mCmdsQ.addLast(new DelayedCmd(OP_CMD,
                                (CatCmdMessage) msg.obj, sim_id));
                    }
                }
                break;
            case OP_RESPONSE:
                if (mStkContext[sim_id].responseNeeded) {
                    handleCmdResponse((Bundle) msg.obj, sim_id);
                }
                // call delayed commands if needed.
                if (mStkContext[sim_id].mCmdsQ.size() != 0) {
                    callDelayedMsg(sim_id);
                } else {
                    mStkContext[sim_id].mCmdInProgress = false;
                }
                // reset response needed state var to its original value.
                mStkContext[sim_id].responseNeeded = true;
                break;
            case OP_END_SESSION:
                if (!mStkContext[sim_id].mCmdInProgress) {
                    mStkContext[sim_id].mCmdInProgress = true;
                    handleSessionEnd(sim_id);
                } else {
                    mStkContext[sim_id].mCmdsQ.addLast(new DelayedCmd(OP_END_SESSION, null, sim_id));
                }
                break;
            case OP_BOOT_COMPLETED:
                CatLog.d(LOGTAG, " OP_BOOT_COMPLETED");
//                if (mMainCmd == null) {
//                    StkAppInstaller.unInstall(mContext);
//                }
                break;
            case OP_DELAYED_MSG:
                handleDelayedCmd(sim_id);
                break;
            }
        }
    }

    private boolean isCmdInteractive(CatCmdMessage cmd) {
        switch (cmd.getCmdType()) {
        case SEND_DTMF:
        case SEND_SMS:
        case SEND_SS:
        case SEND_USSD:
        case SET_UP_IDLE_MODE_TEXT:
        case SET_UP_MENU:
        case CLOSE_CHANNEL:
        case RECEIVE_DATA:
        case SEND_DATA:
            return false;
        }

        return true;
    }

    private void handleDelayedCmd(int sim_id) {
        CatLog.d(LOGTAG, "handleDelayedCmd, sim_id: " + sim_id);
        if (mStkContext[sim_id].mCmdsQ.size() != 0) {
            DelayedCmd cmd = mStkContext[sim_id].mCmdsQ.poll();
            if (cmd != null)
            {
                CatLog.d(LOGTAG, "handleDelayedCmd - queue size: " + mStkContext[sim_id].mCmdsQ.size() + " id: " + cmd.id + "sim id: " + cmd.sim_id);
                switch (cmd.id) {
                case OP_CMD:
                    handleCmd(cmd.msg, cmd.sim_id);
                    break;
                case OP_END_SESSION:
                    handleSessionEnd(cmd.sim_id);
                    break;
                }
            }
        }
    }

    private void callDelayedMsg(int sim_id) {
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = OP_DELAYED_MSG;
        msg.arg2 = sim_id;
        mServiceHandler.sendMessage(msg);
    }

    private void handleSessionEnd(int sim_id) {
        mStkContext[sim_id].mCurrentCmd = mStkContext[sim_id].mMainCmd;
        CatLog.d(LOGTAG, "handleSessionEnd - mCurrentCmd changed to mMainCmd!");
        mStkContext[sim_id].mCurrentMenuCmd = mStkContext[sim_id].mMainCmd;
        if (mStkContext[sim_id].mMainCmd == null){
            CatLog.d(LOGTAG, "[handleSessionEnd][mMainCmd is null!]");
        }
        mStkContext[sim_id].lastSelectedItem = null;
        // In case of SET UP MENU command which removed the app, don't
        // update the current menu member.
        if (mStkContext[sim_id].mCurrentMenu != null && mStkContext[sim_id].mMainCmd != null) {
            mStkContext[sim_id].mCurrentMenu = mStkContext[sim_id].mMainCmd.getMenu();
        }
        if (mStkContext[sim_id].mMenuIsVisible) {
            if(mStkContext[sim_id].mSetupMenuCalled == true) {
                launchMenuActivity(null, sim_id);
            } else {
                // Only called when pass FTA test (154.1.1)            
                CatLog.d(LOGTAG, "[handleSessionEnd][To finish menu activity]");
                finishMenuActivity(sim_id);
            }
        }
        if (mStkContext[sim_id].mCmdsQ.size() != 0) {
            callDelayedMsg(sim_id);
        } else {
            mStkContext[sim_id].mCmdInProgress = false;
        }
        // In case a launch browser command was just confirmed, launch that url.
        if (mStkContext[sim_id].launchBrowser) {
            mStkContext[sim_id].launchBrowser = false;
            launchBrowser(mStkContext[sim_id].mBrowserSettings);
        }
    }

    private void handleCmd(CatCmdMessage cmdMsg, int sim_id) {
        StkAppInstaller appInstaller = StkAppInstaller.getInstance();
        if (cmdMsg == null) {
            return;
        }
        // save local reference for state tracking.
        mStkContext[sim_id].mCurrentCmd = cmdMsg;
        boolean waitForUsersResponse = true;

        CatLog.d(LOGTAG,"[handleCmd]" + cmdMsg.getCmdType().name());
        switch (cmdMsg.getCmdType()) {
        case DISPLAY_TEXT:
            TextMessage msg = cmdMsg.geTextMessage();
            mStkContext[sim_id].responseNeeded = msg.responseNeeded;
            waitForUsersResponse = msg.responseNeeded;
            if (mStkContext[sim_id].lastSelectedItem != null) {
                msg.title = mStkContext[sim_id].lastSelectedItem;
            } else if (mStkContext[sim_id].mMainCmd != null){
                msg.title = mStkContext[sim_id].mMainCmd.getMenu().title;
            } else {
                // TODO: get the carrier name from the SIM
                msg.title = "";
            }
            launchTextDialog(sim_id);
            break;
        case SELECT_ITEM:
            CatLog.d(LOGTAG, "SELECT_ITEM +");
            mStkContext[sim_id].mCurrentMenuCmd = mStkContext[sim_id].mCurrentCmd;
            mStkContext[sim_id].mCurrentMenu = cmdMsg.getMenu();
            launchMenuActivity(cmdMsg.getMenu(), sim_id);
            break;
        case SET_UP_MENU:
            mStkContext[sim_id].mCmdInProgress = false;            
            mStkContext[sim_id].mSetupMenuCalled = true;
            mStkContext[sim_id].mMainCmd = mStkContext[sim_id].mCurrentCmd;
            mStkContext[sim_id].mCurrentMenuCmd = mStkContext[sim_id].mCurrentCmd;
            mStkContext[sim_id].mCurrentMenu = cmdMsg.getMenu();
            CatLog.d(LOGTAG, "SET_UP_MENU [" + removeMenu(sim_id) + "]");

            boolean radio_on = true;

            if (removeMenu(sim_id)) {
                CatLog.d(LOGTAG, "removeMenu() - Uninstall App");
                mStkContext[sim_id].mCurrentMenu = null;                
                mStkContext[sim_id].mSetupMenuCalled = false;
                appInstaller.unInstall(mContext, sim_id);
                StkAvailable(sim_id, STK_AVAIL_NOT_AVAILABLE);
            } else if (!radio_on) {
                CatLog.d(LOGTAG, "uninstall App - radio_on[" + radio_on +"]");
                appInstaller.unInstall(mContext, sim_id);
                StkAvailable(sim_id, STK_AVAIL_NOT_AVAILABLE);
            } else {
                CatLog.d(LOGTAG, "install App");
                appInstaller.install(mContext, sim_id);
                StkAvailable(sim_id, STK_AVAIL_AVAILABLE);
            }
            if (mStkContext[sim_id].mMenuIsVisible) {
                launchMenuActivity(null, sim_id);
            }
            break;
        case GET_INPUT:
        case GET_INKEY:
            launchInputActivity(sim_id);
            break;
        case SET_UP_IDLE_MODE_TEXT:
            waitForUsersResponse = false;
            launchIdleText(sim_id);
            break;
        case SEND_DTMF:
        case SEND_SMS:
        case SEND_SS:
        case SEND_USSD:
            waitForUsersResponse = false;
            launchEventMessage(sim_id);
            break;
        case LAUNCH_BROWSER:
            launchConfirmationDialog(mStkContext[sim_id].mCurrentCmd.geTextMessage(), sim_id);
            break;
        case SET_UP_CALL:
            launchConfirmationDialog(mStkContext[sim_id].mCurrentCmd.getCallSettings().confirmMsg, sim_id);
            break;
        case PLAY_TONE:
            launchToneDialog(sim_id);
            break;
        case OPEN_CHANNEL:
            launchOpenChannelDialog(sim_id);
            break;
        case CLOSE_CHANNEL:
        case RECEIVE_DATA:
        case SEND_DATA:
            TextMessage m = mStkContext[sim_id].mCurrentCmd.geTextMessage();

            if ((m != null) && (m.text == null)) {
                switch(cmdMsg.getCmdType()) {
                case CLOSE_CHANNEL:
                    m.text = getResources().getString(R.string.default_close_channel_msg);
                    break;
                case RECEIVE_DATA:
                    m.text = getResources().getString(R.string.default_receive_data_msg);
                    break;
                case SEND_DATA:
                    m.text = getResources().getString(R.string.default_send_data_msg);
                    break;
                }
            }
            /*
             * Display indication in the form of a toast to the user if required.
             */
            launchEventMessage(sim_id);
            break;
        }

        if (!waitForUsersResponse) {
            if (mStkContext[sim_id].mCmdsQ.size() != 0) {
                callDelayedMsg(sim_id);
            } else {
                mStkContext[sim_id].mCmdInProgress = false;
            }
        }
    }

    private void handleCmdResponse(Bundle args, int sim_id) {
        CatLog.d(LOGTAG, "handleCmdResponse, sim id: " + sim_id);
        if (mStkContext[sim_id].mCurrentCmd == null) {
            return;
        }

        if (mStkService[sim_id] == null) {
            mStkService[sim_id] = com.android.internal.telephony.cat.CatService.getInstance(sim_id);
            if (mStkService[sim_id] == null) {
                // This should never happen (we should be responding only to a message
                // that arrived from StkService). It has to exist by this time
                CatLog.d(LOGTAG, "handleCmdResponse exception! mStkService is null when we need to send response.");
                throw new RuntimeException("mStkService is null when we need to send response");
            }
        }

        CatResponseMessage resMsg = new CatResponseMessage(mStkContext[sim_id].mCurrentCmd);

        // set result code
        boolean helpRequired = args.getBoolean(HELP, false);
        boolean confirmed    = false;

        switch(args.getInt(RES_ID)) {
        case RES_ID_MENU_SELECTION:
            CatLog.d(this, "RES_ID_MENU_SELECTION");
            int menuSelection = args.getInt(MENU_SELECTION);
            switch(mStkContext[sim_id].mCurrentMenuCmd.getCmdType()) {
            case SET_UP_MENU:
            case SELECT_ITEM:
                mStkContext[sim_id].lastSelectedItem = getItemName(menuSelection, sim_id);
                if (helpRequired) {
                    resMsg.setResultCode(ResultCode.HELP_INFO_REQUIRED);
                } else {
                    resMsg.setResultCode(ResultCode.OK);
                }
                resMsg.setMenuSelection(menuSelection);
                break;
            }
            break;
        case RES_ID_INPUT:
            CatLog.d(this, "RES_ID_INPUT");
            String input = args.getString(INPUT);
            if (input != null && (null != mStkContext[sim_id].mCurrentCmd.geInput())&&(mStkContext[sim_id].mCurrentCmd.geInput().yesNo)) {
                boolean yesNoSelection = input
                        .equals(StkInputInstance.YES_STR_RESPONSE);
                resMsg.setYesNo(yesNoSelection);
            } else {
                if (helpRequired) {
                    resMsg.setResultCode(ResultCode.HELP_INFO_REQUIRED);
                } else {
                    resMsg.setResultCode(ResultCode.OK);
                    resMsg.setInput(input);
                }
            }
            break;
        case RES_ID_CONFIRM:
            CatLog.d(this, "RES_ID_CONFIRM");
            confirmed = args.getBoolean(CONFIRMATION);
            switch (mStkContext[sim_id].mCurrentCmd.getCmdType()) {
            case DISPLAY_TEXT:
                resMsg.setResultCode(confirmed ? ResultCode.OK
                        : ResultCode.UICC_SESSION_TERM_BY_USER);
                break;
            case LAUNCH_BROWSER:
                resMsg.setResultCode(confirmed ? ResultCode.OK
                        : ResultCode.UICC_SESSION_TERM_BY_USER);
                if (confirmed) {
                    mStkContext[sim_id].launchBrowser = true;
                    mStkContext[sim_id].mBrowserSettings = mStkContext[sim_id].mCurrentCmd.getBrowserSettings();
                }
                break;
            case SET_UP_CALL:
                resMsg.setResultCode(ResultCode.OK);
                resMsg.setConfirmation(confirmed);
                if (confirmed) {
                    launchEventMessage(sim_id);
                }
                break;
            }
            break;
        case RES_ID_DONE:
            resMsg.setResultCode(ResultCode.OK);
            break;
        case RES_ID_BACKWARD:
            CatLog.d(LOGTAG, "RES_ID_BACKWARD");
            resMsg.setResultCode(ResultCode.BACKWARD_MOVE_BY_USER);
            break;
        case RES_ID_END_SESSION:
            CatLog.d(LOGTAG, "RES_ID_END_SESSION");
            resMsg.setResultCode(ResultCode.UICC_SESSION_TERM_BY_USER);
            break;
        case RES_ID_TIMEOUT:
            CatLog.d(LOGTAG, "RES_ID_TIMEOUT");
            // GCF test-case 27.22.4.1.1 Expected Sequence 1.5 (DISPLAY TEXT,
            // Clear message after delay, successful) expects result code OK.
            // If the command qualifier specifies no user response is required
            // then send OK instead of NO_RESPONSE_FROM_USER
            if ((mStkContext[sim_id].mCurrentCmd.getCmdType().value() == AppInterface.CommandType.DISPLAY_TEXT
                    .value())
                    && (mStkContext[sim_id].mCurrentCmd.geTextMessage().userClear == false)) {
                resMsg.setResultCode(ResultCode.OK);
            } else {
                resMsg.setResultCode(ResultCode.NO_RESPONSE_FROM_USER);
            }
            break;
        case RES_ID_CHOICE:
            int choice = args.getInt(CHOICE);
            CatLog.d(this, "User Choice=" + choice);
            switch (choice) {
                case YES:
                    resMsg.setResultCode(ResultCode.OK);
                    confirmed = true;
                    break;
                case NO:
                    resMsg.setResultCode(ResultCode.USER_NOT_ACCEPT);
                    break;
            }

            if (mStkContext[sim_id].mCurrentCmd.getCmdType().value() == AppInterface.CommandType.OPEN_CHANNEL
                    .value()) {
                resMsg.setConfirmation(confirmed);
            }
            break;

        default:
            CatLog.d(LOGTAG, "Unknown result id");
            return;
        }
        
        if (null != mStkContext[sim_id].mCurrentCmd && null != mStkContext[sim_id].mCurrentCmd.getCmdType()) {
            CatLog.d(LOGTAG, "handleCmdResponse- cmdName[" + mStkContext[sim_id].mCurrentCmd.getCmdType().name() + "]");
        }
        mStkService[sim_id].onCmdResponse(resMsg);
    }

    /**
     * Returns 0 or FLAG_ACTIVITY_NO_USER_ACTION, 0 means the user initiated the action.
     *
     * @param userAction If the userAction is yes then we always return 0 otherwise
     * mMenuIsVisible is used to determine what to return. If mMenuIsVisible is true
     * then we are the foreground app and we'll return 0 as from our perspective a
     * user action did cause. If it's false than we aren't the foreground app and
     * FLAG_ACTIVITY_NO_USER_ACTION is returned.
     *
     * @return 0 or FLAG_ACTIVITY_NO_USER_ACTION
     */
    private int getFlagActivityNoUserAction(InitiatedByUserAction userAction, int sim_id) {
        return ((userAction == InitiatedByUserAction.yes) | mStkContext[sim_id].mMenuIsVisible) ?
                                                    0 : Intent.FLAG_ACTIVITY_NO_USER_ACTION;
    }
    private void finishMenuActivity(int sim_id) {
        Intent newIntent = new Intent(Intent.ACTION_VIEW);
        String targetActivity = STK1_MENU_ACTIVITY_NAME;
        switch (sim_id)
        {
            case PhoneConstants.SIM_ID_1:
                break;
            case PhoneConstants.SIM_ID_2:
                targetActivity = STK2_MENU_ACTIVITY_NAME;
                break;
            case PhoneConstants.SIM_ID_3:
                targetActivity = STK3_MENU_ACTIVITY_NAME;
                break;
            case PhoneConstants.SIM_ID_4:
                targetActivity = STK4_MENU_ACTIVITY_NAME;
                break;
            default:
                break;
        }
        CatLog.d(LOGTAG, "finishMenuActivity, target: " + targetActivity); 
        newIntent.setClassName(PACKAGE_NAME, targetActivity);
        int intentFlags = Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP;

        intentFlags |= getFlagActivityNoUserAction(InitiatedByUserAction.unknown, sim_id);
        newIntent.putExtra("STATE", StkMenuInstance.STATE_END);         
        newIntent.putExtra(CMD_SIM_ID, sim_id);
        newIntent.setFlags(intentFlags);
        mContext.startActivity(newIntent);
    }
    
    private void launchMenuActivity(Menu menu, int sim_id) {
        Intent newIntent = new Intent(Intent.ACTION_VIEW);
        String targetActivity = STK1_MENU_ACTIVITY_NAME;
        switch (sim_id)
        {
            case PhoneConstants.SIM_ID_1:
                break;
            case PhoneConstants.SIM_ID_2:
                targetActivity = STK2_MENU_ACTIVITY_NAME;
                break;
            case PhoneConstants.SIM_ID_3:
                targetActivity = STK3_MENU_ACTIVITY_NAME;
                break;
            case PhoneConstants.SIM_ID_4:
                targetActivity = STK4_MENU_ACTIVITY_NAME;
                break;
            default:
                break;
        }
        CatLog.d(LOGTAG, "launchMenuActivity, target: " + targetActivity); 
        newIntent.setClassName(PACKAGE_NAME, targetActivity);
        int intentFlags = Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP;
        if (menu == null) {
            // We assume this was initiated by the user pressing the tool kit icon
            intentFlags |= getFlagActivityNoUserAction(InitiatedByUserAction.yes, sim_id);

            newIntent.putExtra("STATE", StkMenuInstance.STATE_MAIN);
        } else {
            // We don't know and we'll let getFlagActivityNoUserAction decide.
            intentFlags |= getFlagActivityNoUserAction(InitiatedByUserAction.unknown, sim_id);

            newIntent.putExtra("STATE", StkMenuInstance.STATE_SECONDARY);
        }
        newIntent.putExtra(CMD_SIM_ID, sim_id);
        newIntent.setFlags(intentFlags);
        mContext.startActivity(newIntent);
    }

    private void launchInputActivity(int sim_id) {
        Intent newIntent = new Intent(Intent.ACTION_VIEW);
        String targetActivity = STK1_INPUT_ACTIVITY_NAME;
        switch (sim_id)
        {
            case PhoneConstants.SIM_ID_1:
                break;
            case PhoneConstants.SIM_ID_2:
                targetActivity = STK2_INPUT_ACTIVITY_NAME;
                break;
            case PhoneConstants.SIM_ID_3:
                targetActivity = STK3_INPUT_ACTIVITY_NAME;
                break;
            case PhoneConstants.SIM_ID_4:
                targetActivity = STK4_INPUT_ACTIVITY_NAME;
                break;
            default:
                break;
        }
        CatLog.d(LOGTAG, "launchInputActivity, target: " + targetActivity);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | getFlagActivityNoUserAction(InitiatedByUserAction.unknown, sim_id));
        newIntent.setClassName(PACKAGE_NAME, targetActivity);
        newIntent.putExtra("INPUT", mStkContext[sim_id].mCurrentCmd.geInput());
        newIntent.putExtra(CMD_SIM_ID, sim_id);
        mContext.startActivity(newIntent);
    }

    private void launchTextDialog(int sim_id) {
        CatLog.d(LOGTAG, "launchTextDialog, sim id: " + sim_id);
        Intent newIntent = null;
        switch (sim_id)
        {
            case PhoneConstants.SIM_ID_1:
                newIntent = new Intent(this, StkDialogActivity.class);
                break;
            case PhoneConstants.SIM_ID_2:
                newIntent = new Intent(this, StkDialogActivityII.class);
                break;
            case PhoneConstants.SIM_ID_3:
                newIntent = new Intent(this, StkDialogActivityIII.class);
                break;
            case PhoneConstants.SIM_ID_4:
                newIntent = new Intent(this, StkDialogActivityIV.class);
                break;
            default:
                break;
        }
        if (newIntent != null)
        {
            newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_NO_HISTORY
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    | getFlagActivityNoUserAction(InitiatedByUserAction.unknown, sim_id));
            TextMessage msg = mStkContext[sim_id].mCurrentCmd.geTextMessage();
            newIntent.putExtra("TEXT", mStkContext[sim_id].mCurrentCmd.geTextMessage());
            newIntent.putExtra(CMD_SIM_ID, sim_id);
            startActivity(newIntent);
        }
    }

    private void launchEventMessage(int sim_id) {
        TextMessage msg = mStkContext[sim_id].mCurrentCmd.geTextMessage();
        if (msg == null || (msg.text != null && msg.text.length() == 0)) {
            CatLog.d(LOGTAG, "aaaaa [return] ");
            return;
        }

        Toast toast = new Toast(mContext.getApplicationContext());
        LayoutInflater inflate = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflate.inflate(R.layout.stk_event_msg, null);
        TextView tv = (TextView) v
                .findViewById(com.android.internal.R.id.message);
        ImageView iv = (ImageView) v
                .findViewById(com.android.internal.R.id.icon);
        if (msg.icon != null) {
            iv.setImageBitmap(msg.icon);
        } else {
            iv.setVisibility(View.GONE);
        }
        if (!msg.iconSelfExplanatory) {
            tv.setText(msg.text);
        }

        toast.setView(v);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setGravity(Gravity.BOTTOM, 0, 0);
        toast.show();
    }

    private void launchConfirmationDialog(TextMessage msg, int sim_id) {
        msg.title = mStkContext[sim_id].lastSelectedItem;
        Intent newIntent = null;
        switch (sim_id)
        {
            case PhoneConstants.SIM_ID_1:
                newIntent = new Intent(this, StkDialogActivity.class);
                break;
            case PhoneConstants.SIM_ID_2:
                newIntent = new Intent(this, StkDialogActivityII.class);
                break;
            case PhoneConstants.SIM_ID_3:
                newIntent = new Intent(this, StkDialogActivityIII.class);
                break;
            case PhoneConstants.SIM_ID_4:
                newIntent = new Intent(this, StkDialogActivityIV.class);
                break;
            default:
                break;
        }
        if (newIntent != null)
        {
            newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_NO_HISTORY
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    | getFlagActivityNoUserAction(InitiatedByUserAction.unknown, sim_id));
            newIntent.putExtra("TEXT", msg);
            newIntent.putExtra(CMD_SIM_ID, sim_id);
            startActivity(newIntent);
        }
    }

    private void launchBrowser(BrowserSettings settings) {
        if (settings == null) {
            return;
        }

        Intent intent = null;
        Uri data = null;

        if (settings.url != null) {
            CatLog.d(this, "settings.url = " + settings.url);
            if ((settings.url.startsWith("http://") || (settings.url.startsWith("https://")))) {
                data = Uri.parse(settings.url);
            } else {
                String modifiedUrl = "http://" + settings.url;
                CatLog.d(this, "modifiedUrl = " + modifiedUrl);
                data = Uri.parse(modifiedUrl);
            }
        }
        if (data != null) {
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(data);
        } else {
            // if the command did not contain a URL,
            // launch the browser to the default homepage.
            CatLog.d(this, "launch browser with default URL ");
            intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,
                    Intent.CATEGORY_APP_BROWSER);
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        switch (settings.mode) {
        case USE_EXISTING_BROWSER:
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            break;
        case LAUNCH_NEW_BROWSER:
            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            break;
        case LAUNCH_IF_NOT_ALREADY_LAUNCHED:
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            break;
        }
        // start browser activity
        startActivity(intent);
        // a small delay, let the browser start, before processing the next command.
        // this is good for scenarios where a related DISPLAY TEXT command is
        // followed immediately.
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {}
    }

    private void launchIdleText(int sim_id) {
        TextMessage msg = mStkContext[sim_id].mCurrentCmd.geTextMessage();

        CatLog.d(LOGTAG, "launchIdleText - text[" + msg.text 
                         + "] iconSelfExplanatory[" + msg.iconSelfExplanatory
                         + "] icon[" + msg.icon + "], sim id: " + sim_id);

        if (msg == null) {
            CatLog.d(this, "mCurrent.getTextMessage is NULL");
            mNotificationManager.cancel(getNotificationId(sim_id));
            return;
        }
        if (msg.text == null) {
            CatLog.d(LOGTAG, "cancel IdleMode text");
            mNotificationManager.cancel(getNotificationId(sim_id));
        } else {
            CatLog.d(LOGTAG, "Add IdleMode text");
            PendingIntent pendingIntent = PendingIntent.getService(mContext, 0,
                    new Intent(mContext, StkAppService.class), 0);

            final Notification.Builder notificationBuilder = new Notification.Builder(
                    StkAppService.this);
            notificationBuilder.setContentTitle("");
            notificationBuilder
                    .setSmallIcon(com.android.internal.R.drawable.stat_notify_sim_toolkit);
            notificationBuilder.setContentIntent(pendingIntent);
            notificationBuilder.setOngoing(true);
            // Set text and icon for the status bar and notification body.
            if (!msg.iconSelfExplanatory) {
                notificationBuilder.setContentText(msg.text);
            }
            if (msg.icon != null) {
                notificationBuilder.setLargeIcon(msg.icon);
            } else {
                Bitmap bitmapIcon = BitmapFactory.decodeResource(StkAppService.this
                    .getResources().getSystem(),
                    com.android.internal.R.drawable.stat_notify_sim_toolkit);
                notificationBuilder.setLargeIcon(bitmapIcon);
            }

            mNotificationManager.notify(getNotificationId(sim_id), notificationBuilder.build());
        }
    }

    private void launchToneDialog(int sim_id) {
        Intent newIntent = new Intent(this, ToneDialog.class);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_HISTORY
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | getFlagActivityNoUserAction(InitiatedByUserAction.unknown, sim_id));
        newIntent.putExtra("TEXT", mStkContext[sim_id].mCurrentCmd.geTextMessage());
        newIntent.putExtra("TONE", mStkContext[sim_id].mCurrentCmd.getToneSettings());
        newIntent.putExtra(CMD_SIM_ID, sim_id);
        startActivity(newIntent);
    }

    private void launchOpenChannelDialog(int sim_id) {
        TextMessage msg = mStkContext[sim_id].mCurrentCmd.geTextMessage();
        if (msg == null) {
            CatLog.d(this, "msg is null, return here");
            return;
        }

        msg.title = getResources().getString(R.string.stk_dialog_title);
        if (msg.text == null) {
            msg.text = getResources().getString(R.string.default_open_channel_msg);
        }

        final AlertDialog dialog = new AlertDialog.Builder(mContext)
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(msg.title)
                    .setMessage(msg.text)
                    .setCancelable(false)
                    .setPositiveButton(getResources().getString(R.string.stk_dialog_accept),
                                       new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Bundle args = new Bundle();
                            args.putInt(RES_ID, RES_ID_CHOICE);
                            args.putInt(CHOICE, YES);
                            Message message = mServiceHandler.obtainMessage();
                            message.arg1 = OP_RESPONSE;
                            message.obj = args;
                            mServiceHandler.sendMessage(message);
                        }
                    })
                    .setNegativeButton(getResources().getString(R.string.stk_dialog_reject),
                                       new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Bundle args = new Bundle();
                            args.putInt(RES_ID, RES_ID_CHOICE);
                            args.putInt(CHOICE, NO);
                            Message message = mServiceHandler.obtainMessage();
                            message.arg1 = OP_RESPONSE;
                            message.obj = args;
                            mServiceHandler.sendMessage(message);
                        }
                    })
                    .create();

        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        if (!mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_sf_slowBlur)) {
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        }

        dialog.show();
    }

    private void launchTransientEventMessage(int sim_id) {
        TextMessage msg = mStkContext[sim_id].mCurrentCmd.geTextMessage();
        if (msg == null) {
            CatLog.d(this, "msg is null, return here");
            return;
        }

        msg.title = getResources().getString(R.string.stk_dialog_title);

        final AlertDialog dialog = new AlertDialog.Builder(mContext)
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(msg.title)
                    .setMessage(msg.text)
                    .setCancelable(false)
                    .setPositiveButton(getResources().getString(android.R.string.ok),
                                       new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .create();

        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        if (!mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_sf_slowBlur)) {
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        }

        dialog.show();
    }

    private int getNotificationId(int sim_id)
    {
        int notify_id = STK1_NOTIFICATION_ID;
        switch (sim_id)
        {
            case PhoneConstants.SIM_ID_2:
                notify_id = STK2_NOTIFICATION_ID;
                break;
            case PhoneConstants.SIM_ID_3:
                notify_id = STK3_NOTIFICATION_ID;
                break;
            case PhoneConstants.SIM_ID_4:
                notify_id = STK4_NOTIFICATION_ID;
                break;
        }
        CatLog.d(LOGTAG, "getNotificationId, sim_id: " + sim_id + ", notify_id: " + notify_id);
        return notify_id;
    }

    private String getItemName(int itemId, int sim_id) {
        Menu menu = mStkContext[sim_id].mCurrentCmd.getMenu();
        if (menu == null) {
            return null;
        }
        for (Item item : menu.items) {
            if (item.id == itemId) {
                return item.text;
            }
        }
        return null;
    }

    private boolean removeMenu(int sim_id) {
        try {
            if (mStkContext[sim_id].mCurrentMenu.items.size() == 1 &&
                mStkContext[sim_id].mCurrentMenu.items.get(0) == null) {
                return true;
            }
        } catch (NullPointerException e) {
            CatLog.d(LOGTAG, "Unable to get Menu's items size");
            return true;
        }
        return false;
    }

    public void sendMessageToServiceHandler(int opCode, Object obj, int sim_id) {
        CatLog.d(LOGTAG, "call sendMessageToServiceHandler: " + opCode);
        if(mServiceHandler == null) {
            waitForLooper();
        }
        Message msg = mServiceHandler.obtainMessage(0, opCode, sim_id, obj);
        mServiceHandler.sendMessage(msg);
    }

    public void StkAvailable(int sim_id, int available)
    {
        if (mStkContext[sim_id] != null)
        {
            mStkContext[sim_id].mAvailable = available;
        }
        CatLog.d(LOGTAG, "sim_id: " + sim_id + ", available: " + available + ", StkAvailable: " + ((mStkContext[sim_id] != null)? mStkContext[sim_id].mAvailable : -1));
    }

    public int StkQueryAvailable(int sim_id)
    {
        int result = ((mStkContext[sim_id] != null)? mStkContext[sim_id].mAvailable : -1);
        
        CatLog.d(LOGTAG, "sim_id: " + sim_id + ", StkQueryAvailable: " + result);
        return result;
    }
}

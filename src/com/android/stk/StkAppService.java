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
import com.android.internal.telephony.RILConstants.SimCardID;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.TelephonyIntents;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.content.IntentFilter;
import android.content.res.Configuration;
import java.util.Locale;
import android.content.BroadcastReceiver;
import android.os.RemoteException;
import android.os.Bundle;
import android.os.SystemProperties;
import java.io.ByteArrayOutputStream;
import android.view.KeyEvent;

import android.app.ActivityManager;
import java.util.List;
import java.util.LinkedList;
import java.util.ArrayList;

import java.util.LinkedList;

/**
 * SIM toolkit application level service. Interacts with Telephopny messages,
 * application's launch and user input from STK UI elements.
 *
 */
public class StkAppService extends Service implements Runnable {

    // members
    private volatile Looper mServiceLooper;
    private volatile ServiceHandler mServiceHandler;
    private AppInterface mStkService;
    private Context mContext = null;
    private CatCmdMessage mMainCmd = null;
    private CatCmdMessage mCurrentCmd = null;
    private CatCmdMessage mEventListCmd = null;
    private Menu mCurrentMenu = null;
    private String lastSelectedItem = null;
    private boolean mMenuIsVisibile = false;
    private boolean responseNeeded = true;
    private boolean mCmdInProgress = false;
    private NotificationManager mNotificationManager = null;
    private LinkedList<DelayedCmd> mCmdsQ = null;
    private boolean launchBrowser = false;
    private BrowserSettings mBrowserSettings = null;
    static StkAppService sInstance = null;

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

    static final String EVENT_DOWNLOAD_ID = "event";
    static final String EVENT_ADDITIONINFO = "eventadditionalInfo";
    static final String EVENT_SOURCE_ID = "sourceId";
    static final String EVENT_DESTINATION_ID = "destinationId";

    // operations ids for different service functionality.
    static final int OP_CMD = 1;
    static final int OP_RESPONSE = 2;
    static final int OP_LAUNCH_APP = 3;
    static final int OP_END_SESSION = 4;
    static final int OP_BOOT_COMPLETED = 5;
    private static final int OP_DELAYED_MSG = 6;
    static final int OP_REMOVE_APP = 7;
    private static final int OP_STK_IDLESCREEN = 8;

    // Response ids
    static final int RES_ID_MENU_SELECTION = 11;
    static final int RES_ID_INPUT = 12;
    static final int RES_ID_CONFIRM = 13;
    static final int RES_ID_DONE = 14;
    static final int RES_ID_CHOICE = 15;
    static final int RES_ID_EVENTDOWNLOAD = 16;

    static final int RES_ID_TIMEOUT = 20;
    static final int RES_ID_BACKWARD = 21;
    static final int RES_ID_END_SESSION = 22;
    static final int RES_ID_EXIT = 23;

    static final int YES = 1;
    static final int NO = 0;

    private static final String PACKAGE_NAME = "com.android.stk";
    private static final String MENU_ACTIVITY_NAME =
                                        PACKAGE_NAME + ".StkMenuActivity";
    private static final String INPUT_ACTIVITY_NAME =
                                        PACKAGE_NAME + ".StkInputActivity";

    // Notification id used to display Idle Mode text in NotificationManager.
    private static final int STK_NOTIFICATION_ID = 333;

    static final String TOP_ACTIVE_PACKAGE_CAMERA_NAME ="com.android.camera";
    static final String TOP_ACTIVE_PACKAGE_CONTACTS_NAME ="com.android.contacts";
    static final String TOP_ACTIVE_PACKAGE_BROWSER_NAME ="com.android.browser";
    static final String TOP_ACTIVE_PACKAGE_PHONE_NAME ="com.android.phone";
    private boolean mscreenbusy = false;

    static final int SCREEN_IS_BUSY=0x01;

    static final int EVENTLIST_USER_ACTIVITY          = 0x04;
    static final int EVENTLIST_IDLE_SCREEN_AVAILABLE  = 0x05;
    static final int EVENTLIST_LANGUAGE_SELECTION     = 0x07;
    static final int EVENTLIST_DATA_AVAILABLE     = 0x09;
    static final int EVENTLIST_CHANNEL_STATUS     = 0x0A;

    static final int DI_DISPLAY = 0x02;
    static final int DI_ME = 0x82;
    static final int DI_SIM = 0x81;

    private BroadcastReceiver mLanguageEventReceiver = null;
    private BroadcastReceiver mIdleScreenReceiver = null;
    private BroadcastReceiver mUserActivityReceiver = null;
    private String mCurrentLanguage = null;
    private byte[] mEventList = null;
    private int mEvenvalueIndex = 0;
    private int mEvenvalueLen = 0;

    // Inner class used for queuing telephony messages (proactive commands,
    // session end) while the service is busy processing a previous message.
    private class DelayedCmd {
        // members
        int id;
        CatCmdMessage msg;

        DelayedCmd(int id, CatCmdMessage msg) {
            this.id = id;
            this.msg = msg;
        }
    }

    @Override
    public void onCreate() {
        // Initialize members
        // NOTE mStkService is a singleton and continues to exist even if the GSMPhone is disposed
        //   after the radio technology change from GSM to CDMA so the PHONE_TYPE_CDMA check is
        //   needed. In case of switching back from CDMA to GSM the GSMPhone constructor updates
        //   the instance. (TODO: test).

        mCmdsQ = new LinkedList<DelayedCmd>();
        Thread serviceThread = new Thread(null, this, "Stk App Service");
        serviceThread.start();
        mContext = getBaseContext();
        mNotificationManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        sInstance = this;
    }

    @Override
    public void onStart(Intent intent, int startId) {
        mStkService = com.android.internal.telephony.cat.CatService
                .getInstance();

        if (mStkService == null) {
            stopSelf();
            CatLog.d(this, " Unable to get Service handle");
            StkAppInstaller.unInstall(mContext);
            return;
        }

        waitForLooper();

        if (intent == null) {
            CatLog.d(this, " onStart intent null");
            return;
        }

        Bundle args = intent.getExtras();

        if (args == null) {
            CatLog.d(this, " onStart  args null");
            return;
        }

        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = args.getInt(OPCODE);
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
        case OP_REMOVE_APP:
            break;
        default:
            return;
        }
        mServiceHandler.sendMessage(msg);
    }

    @Override
    public void onDestroy() {
        if (mLanguageEventReceiver != null) {
            unregisterReceiver(mLanguageEventReceiver);
            mLanguageEventReceiver = null;
        }

        if (mIdleScreenReceiver != null) {
            unregisterReceiver(mIdleScreenReceiver);
            mIdleScreenReceiver = null;
        }

        if (mUserActivityReceiver != null) {
            unregisterReceiver(mUserActivityReceiver);
            mUserActivityReceiver = null;
        }

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
    void indicateMenuVisibility(boolean visibility) {
        mMenuIsVisibile = visibility;
    }

    /*
     * Package api used by StkMenuActivity to get its Menu parameter.
     */
    Menu getMenu() {
        return mCurrentMenu;
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
            int opcode = msg.arg1;
            CatLog.d(this, "handleMessage opcode: "+opcode);
            switch (opcode) {
            case OP_LAUNCH_APP:
                if (mMainCmd == null) {
                    // nothing todo when no SET UP MENU command didn't arrive.
                    return;
                }
                launchMenuActivity(null);
                break;
            case OP_CMD:
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
                    handleCmd(cmdMsg);
                } else {
                    if (!mCmdInProgress) {
                        mCmdInProgress = true;
                        handleCmd((CatCmdMessage) msg.obj);
                    } else {
                        mCmdsQ.addLast(new DelayedCmd(OP_CMD,
                                (CatCmdMessage) msg.obj));
                    }
                }
                break;
            case OP_RESPONSE:
                if (responseNeeded) {
                    handleCmdResponse((Bundle) msg.obj);
                }
                // call delayed commands if needed.
                if (mCmdsQ.size() != 0) {
                    callDelayedMsg();
                } else {
                    mCmdInProgress = false;
                }
                // reset response needed state var to its original value.
                responseNeeded = true;
                break;
            case OP_END_SESSION:
                if (!mCmdInProgress) {
                    mCmdInProgress = true;
                    handleSessionEnd();
                } else {
                    mCmdsQ.addLast(new DelayedCmd(OP_END_SESSION, null));
                }
                break;
            case OP_BOOT_COMPLETED:
                CatLog.d(this, "OP_BOOT_COMPLETED");
                if (mMainCmd == null) {
                    CatLog.d(this, "OP_BOOT_COMPLETED mMainCmd NULL unInstall");
                    StkAppInstaller.unInstall(mContext);
                }
                break;
            case OP_DELAYED_MSG:
                handleDelayedCmd();
                break;
            case OP_REMOVE_APP:
                android.util.Log.d("StkAppService", "Removing STK App");
                StkAppInstaller.unInstall(mContext);
                break;
            case OP_STK_IDLESCREEN:
                processIdleScreen();
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
        case SET_UP_EVENT_LIST:
        case REFRESH:
            return false;
        }

        return true;
    }

    private void handleDelayedCmd() {
        if (mCmdsQ.size() != 0) {
            DelayedCmd cmd = mCmdsQ.poll();
            switch (cmd.id) {
            case OP_CMD:
                handleCmd(cmd.msg);
                break;
            case OP_END_SESSION:
                handleSessionEnd();
                break;
            }
        }
    }

    private void callDelayedMsg() {
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = OP_DELAYED_MSG;
        mServiceHandler.sendMessage(msg);
    }

    private void handleSessionEnd() {
        mCurrentCmd = mMainCmd;
        lastSelectedItem = null;
        // In case of SET UP MENU command which removed the app, don't
        // update the current menu member.
        if (mCurrentMenu != null && mMainCmd != null) {
            mCurrentMenu = mMainCmd.getMenu();
        }
        if (mMenuIsVisibile) {
            launchMenuActivity(null);
        }
        if (mCmdsQ.size() != 0) {
            callDelayedMsg();
        } else {
            mCmdInProgress = false;
        }
        // In case a launch browser command was just confirmed, launch that url.
        if (launchBrowser) {
            launchBrowser = false;
            launchBrowser(mBrowserSettings);
        }
    }

    private void handleCmd(CatCmdMessage cmdMsg) {
        if (cmdMsg == null) {
            return;
        }
        // save local reference for state tracking.
        mCurrentCmd = cmdMsg;
        boolean waitForUsersResponse = true;

        CatLog.d(this, cmdMsg.getCmdType().name());
        switch (cmdMsg.getCmdType()) {
        case DISPLAY_TEXT:
            TextMessage msg = cmdMsg.geTextMessage();
            responseNeeded = msg.responseNeeded;
            waitForUsersResponse = msg.responseNeeded;
            if (lastSelectedItem != null) {
                msg.title = lastSelectedItem;
            } else if (mMainCmd != null){
                msg.title = mMainCmd.getMenu().title;
            } else {
                // TODO: get the carrier name from the SIM
                msg.title = "";
            }
            ActivityManager manager= (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
            String getTopPackageName = manager.getRunningTasks(30).get(0).topActivity.getPackageName();
            CatLog.d(this, "DISPLAY_TEXT topActivity="+getTopPackageName);

            if ((getTopPackageName.equals(TOP_ACTIVE_PACKAGE_CAMERA_NAME)) || (getTopPackageName.equals(TOP_ACTIVE_PACKAGE_PHONE_NAME)) || (getTopPackageName.equals(TOP_ACTIVE_PACKAGE_BROWSER_NAME)) || (getTopPackageName.equals(TOP_ACTIVE_PACKAGE_CONTACTS_NAME))) {
                mscreenbusy =true;
                CatLog.d(this, "Screen is busy");
            }

            if (msg.isHighPriority == false) {
                if (mscreenbusy == true) {
                    CatLog.d(this, "ME send to SIM Screen is busy");
                    CatResponseMessage resMsg = new CatResponseMessage(cmdMsg);
                    resMsg.setResultCode(ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS);
                    resMsg.setincludeAdditionalInfo(true);
                    resMsg.setAdditionalInfo(SCREEN_IS_BUSY);
                    mscreenbusy = false;
                    waitForUsersResponse = false;
                    mStkService.onCmdResponse(resMsg);
                } else {
                    CatLog.d(this, "launchTextDialog");
            launchTextDialog();
                }
            } else {
            launchTextDialog();
            }
            break;
        case SELECT_ITEM:
            mCurrentMenu = cmdMsg.getMenu();
            launchMenuActivity(cmdMsg.getMenu());
            break;
        case SET_UP_MENU:
            mMainCmd = mCurrentCmd;
            mCurrentMenu = cmdMsg.getMenu();
            if (removeMenu()) {
                CatLog.d(this, "Uninstall App");
                mCurrentMenu = null;
                StkAppInstaller.unInstall(mContext);
            } else {
                CatLog.d(this, "Install App");
                StkAppInstaller.install(mContext);
            }
            if (mMenuIsVisibile) {
                launchMenuActivity(null);
            }
            break;
        case GET_INPUT:
        case GET_INKEY:
            launchInputActivity();
            break;
        case REFRESH:
            waitForUsersResponse = false;
            CatLog.d(this, "handleCmd REFRESH");
            mNotificationManager.cancel(STK_NOTIFICATION_ID);
            break;
        case SET_UP_IDLE_MODE_TEXT:
            waitForUsersResponse = false;
            launchIdleText();
            break;
        case SET_UP_EVENT_LIST:
            CatLog.d(this, "handleCmd(), SET_UP_EVENT_LIST");
            mEventList = cmdMsg.getEventList();
            mEvenvalueIndex = cmdMsg.getEvenvalueIndex();
            mEvenvalueLen = cmdMsg.getEvenvalueLen();
            CatLog.d( this, "mEvenvalueIndex: " + mEvenvalueIndex +"mEvenvalueLen"+mEvenvalueLen );

            if (mEventList != null) {
                if(mEvenvalueLen == 0) {
                CatLog.d(this, "Disable Setup Event List");

                if (mLanguageEventReceiver != null) {
                    CatLog.d(this, "unregisterReceiver(), mLanguageEventReceiver");
                    unregisterReceiver(mLanguageEventReceiver);
                    mLanguageEventReceiver = null;
                }

                if (mUserActivityReceiver != null) {
                    CatLog.d(this, "unregisterReceiver(), mUserActivityReceiver");
                    unregisterReceiver(mUserActivityReceiver);
                    mUserActivityReceiver = null;
                }

                if (mIdleScreenReceiver != null) {
                    CatLog.d(this, "unregisterReceiver(), mIdleScreenReceiver");
                    unregisterReceiver(mIdleScreenReceiver);
                    mIdleScreenReceiver = null;
                }
                mEventListCmd = null;
            } else {
                    for (int i = mEvenvalueIndex ; i < (mEvenvalueIndex + mEvenvalueLen) ; i++) {
                        CatLog.d("ValueParser", "index="+ i +", mEventList[i]:"+ mEventList[i]);
                        int EventValue = (int) mEventList[i];
                        switch (EventValue) {
                    case EVENTLIST_USER_ACTIVITY:
                        CatLog.d(this, "EVENTLIST_USER_ACTIVITY");
                        launchUserActivityListener();
                        break;
                    case EVENTLIST_IDLE_SCREEN_AVAILABLE:
                        CatLog.d(this, "EVENTLIST_IDLE_SCREEN_AVAILABLE");
                        launchIdleScreenListener();
                        break;
                    case EVENTLIST_LANGUAGE_SELECTION:
                        CatLog.d(this, "EVENTLIST_LANGUAGE_SELECTION");
                        launchLanguageListener();
                        break;
                    default:
                            CatLog.d(this, "Don't Support EventId="+EventValue);
                        break;
                    }
                }
            }
            }
            waitForUsersResponse = false;
            mEventListCmd = cmdMsg;
            break;
        case SEND_DTMF:
        case SEND_SMS:
        case SEND_SS:
        case SEND_USSD:
            waitForUsersResponse = false;
            launchEventMessage();
            break;
        case LAUNCH_BROWSER:
            launchConfirmationDialog(mCurrentCmd.geTextMessage());
            break;
        case SET_UP_CALL:
            launchConfirmationDialog(mCurrentCmd.getCallSettings().confirmMsg);
            break;
        case PLAY_TONE:
            launchToneDialog();
            break;
        case OPEN_CHANNEL:
            //launchOpenChannelDialog();
            launchConfirmationDialog(mCurrentCmd.geTextMessage());
            break;
        case CLOSE_CHANNEL:
        case RECEIVE_DATA:
        case SEND_DATA:
            TextMessage m = mCurrentCmd.geTextMessage();

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
            launchEventMessage();
            break;
        }

        if (!waitForUsersResponse) {
            if (mCmdsQ.size() != 0) {
                callDelayedMsg();
            } else {
                mCmdInProgress = false;
            }
        }
    }

    private void handleCmdResponse(Bundle args) {
     if (args.getInt(RES_ID)== RES_ID_EVENTDOWNLOAD){
          CatResponseMessage resMsg = new CatResponseMessage(mEventListCmd);
          int eventId = args.getInt(EVENT_DOWNLOAD_ID);
          resMsg.setResultCode(ResultCode.EVENT_DOWNLOAD);
          resMsg.setEvent(eventId);
          int sourceId = args.getInt(EVENT_SOURCE_ID);
          int destinationId = args.getInt(EVENT_DESTINATION_ID);
          resMsg.setstkevetdownload (true);
          CatLog.d(this, "handleCmdResponse(), eventId="+eventId+", sourceId="+sourceId+", destinationId="+destinationId);
          resMsg.setSourceAndDestination(sourceId, destinationId);
          if ( eventId == EVENTLIST_LANGUAGE_SELECTION ) {
               byte[] eventadditionalInfo= args.getByteArray(EVENT_ADDITIONINFO);
               resMsg.setAdditionalInfo(eventadditionalInfo);
          }
          CatLog.d(this, "Send Response to StkService");
          mStkService.onCmdResponse(resMsg);
            return;
     }

        if (mCurrentCmd == null) {
            CatLog.d(this, "mCurrentCmd == null");
            return;
        }
        if (mStkService == null) {
            mStkService = com.android.internal.telephony.cat.CatService.getInstance(SimCardID.ID_ZERO);
            if (mStkService == null) {
                // This should never happen (we should be responding only to a message
                // that arrived from StkService). It has to exist by this time
                throw new RuntimeException("mStkService is null when we need to send response");
            }
        }

        CatResponseMessage resMsg = new CatResponseMessage(mCurrentCmd);

        // set result code
        boolean helpRequired = args.getBoolean(HELP, false);
        boolean confirmed    = false;

        switch(args.getInt(RES_ID)) {
        case RES_ID_MENU_SELECTION:
            CatLog.d(this, "RES_ID_MENU_SELECTION");
            int menuSelection = args.getInt(MENU_SELECTION);
            switch(mCurrentCmd.getCmdType()) {
            case SET_UP_MENU:
            case SELECT_ITEM:
                lastSelectedItem = getItemName(menuSelection);
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
            Input cmdInput = mCurrentCmd.geInput();
            if (cmdInput != null && cmdInput.yesNo) {
                boolean yesNoSelection = input
                        .equals(StkInputActivity.YES_STR_RESPONSE);
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
            switch (mCurrentCmd.getCmdType()) {
            case DISPLAY_TEXT:
                resMsg.setResultCode(confirmed ? ResultCode.OK
                        : ResultCode.UICC_SESSION_TERM_BY_USER);
                break;
            case LAUNCH_BROWSER:
                resMsg.setResultCode(confirmed ? ResultCode.OK
                        : ResultCode.UICC_SESSION_TERM_BY_USER);
                if (confirmed) {
                    launchBrowser = true;
                    mBrowserSettings = mCurrentCmd.getBrowserSettings();
                }
                break;
            case SET_UP_CALL:
                resMsg.setResultCode(ResultCode.OK);
                resMsg.setConfirmation(confirmed);
                if (confirmed) {
                    launchEventMessage(mCurrentCmd.getCallSettings().callMsg);
                }
                break;
            case OPEN_CHANNEL:
                resMsg.setResultCode(confirmed ? ResultCode.OK
                                     : ResultCode.USER_NOT_ACCEPT);
                resMsg.setConfirmation(confirmed);
                break;
            }
            break;
        case RES_ID_DONE:
            resMsg.setResultCode(ResultCode.OK);
            break;
        case RES_ID_BACKWARD:
            CatLog.d(this, "RES_ID_BACKWARD");
            resMsg.setResultCode(ResultCode.BACKWARD_MOVE_BY_USER);
            break;
        case RES_ID_END_SESSION:
            CatLog.d(this, "RES_ID_END_SESSION");
            resMsg.setResultCode(ResultCode.UICC_SESSION_TERM_BY_USER);
            break;
        case RES_ID_TIMEOUT:
            CatLog.d(this, "RES_ID_TIMEOUT");
            // GCF test-case 27.22.4.1.1 Expected Sequence 1.5 (DISPLAY TEXT,
            // Clear message after delay, successful) expects result code OK.
            // If the command qualifier specifies no user response is required
            // then send OK instead of NO_RESPONSE_FROM_USER
            if ((mCurrentCmd.getCmdType().value() == AppInterface.CommandType.DISPLAY_TEXT
                    .value())
                    && (mCurrentCmd.geTextMessage().userClear == false)) {
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

            if (mCurrentCmd.getCmdType().value() == AppInterface.CommandType.OPEN_CHANNEL
                    .value()) {
                resMsg.setConfirmation(confirmed);
            }
            break;
        default:
            CatLog.d(this, "Unknown result id");
            return;
        }
        mStkService.onCmdResponse(resMsg);
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
    private int getFlagActivityNoUserAction(InitiatedByUserAction userAction) {
        return ((userAction == InitiatedByUserAction.yes) | mMenuIsVisibile) ?
                                                    0 : Intent.FLAG_ACTIVITY_NO_USER_ACTION;
    }

    private void launchMenuActivity(Menu menu) {
        Intent newIntent = new Intent(Intent.ACTION_VIEW);
        newIntent.setClassName(PACKAGE_NAME, MENU_ACTIVITY_NAME);
        int intentFlags = Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP;
        if (menu == null) {
            // We assume this was initiated by the user pressing the tool kit icon
            intentFlags |= getFlagActivityNoUserAction(InitiatedByUserAction.yes);

            newIntent.putExtra("STATE", StkMenuActivity.STATE_MAIN);
        } else {
            // We don't know and we'll let getFlagActivityNoUserAction decide.
            intentFlags |= getFlagActivityNoUserAction(InitiatedByUserAction.unknown);

            newIntent.putExtra("STATE", StkMenuActivity.STATE_SECONDARY);
        }
        newIntent.setFlags(intentFlags);
        mContext.startActivity(newIntent);
    }

    private void launchInputActivity() {
        Intent newIntent = new Intent(Intent.ACTION_VIEW);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | getFlagActivityNoUserAction(InitiatedByUserAction.unknown));
        newIntent.setClassName(PACKAGE_NAME, INPUT_ACTIVITY_NAME);
        newIntent.putExtra("INPUT", mCurrentCmd.geInput());
        mContext.startActivity(newIntent);
    }

    private void launchTextDialog() {
        Intent newIntent = new Intent(this, StkDialogActivity.class);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NO_HISTORY
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | getFlagActivityNoUserAction(InitiatedByUserAction.unknown));
        newIntent.putExtra("TEXT", mCurrentCmd.geTextMessage());
        startActivity(newIntent);
    }

    private void launchEventMessage() {
        launchEventMessage(mCurrentCmd.geTextMessage());
    }

    private void launchEventMessage(TextMessage msg) {
        if (msg == null || msg.text == null) {
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

    private void launchConfirmationDialog(TextMessage msg) {
        msg.title = lastSelectedItem;
        Intent newIntent = new Intent(this, StkDialogActivity.class);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_HISTORY
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | getFlagActivityNoUserAction(InitiatedByUserAction.unknown));
        newIntent.putExtra("TEXT", msg);
        startActivity(newIntent);
    }

    private void launchBrowser(BrowserSettings settings) {
        if (settings == null) {
            return;
        }
        // Set browser launch mode
        Intent intent = new Intent();
        intent.setClassName("com.android.browser",
                            "com.android.browser.BrowserActivity");

        Uri data = null;
        if (settings.url != null) {
            CatLog.d(this, "settings.url = " + settings.url);
            if ((settings.url.startsWith("http://") || (settings.url.startsWith("https://")))) {
                data = Uri.parse(settings.url);
            } else {
                String modifiedUrl = "http://" + settings.url;
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

        intent.setData(data);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        switch (settings.mode) {
        case USE_EXISTING_BROWSER:
            intent.setAction(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            break;
        case LAUNCH_NEW_BROWSER:
            intent.setAction(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            break;
        case LAUNCH_IF_NOT_ALREADY_LAUNCHED:
            intent.setAction(Intent.ACTION_VIEW); //for default action, view
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

    private void launchIdleText() {
        TextMessage msg = mCurrentCmd.geTextMessage();

        if (msg == null) {
            CatLog.d(this, "mCurrent.getTextMessage is NULL");
            mNotificationManager.cancel(STK_NOTIFICATION_ID);
            return;
        }
        if (msg.text == null) {
            mNotificationManager.cancel(STK_NOTIFICATION_ID);
        } else {
            PendingIntent pendingIntent = PendingIntent.getService(mContext, 0,
                    new Intent(mContext, StkAppService.class), 0);

            final Notification.Builder notificationBuilder = new Notification.Builder(
                    StkAppService.this);
            notificationBuilder.setContentTitle("");
            notificationBuilder
                    .setSmallIcon(com.android.internal.R.drawable.stat_notify_sim_toolkit);
            notificationBuilder.setContentIntent(pendingIntent);
            notificationBuilder.setOngoing(true);

            if (msg.icon != null) {
                notificationBuilder.setLargeIcon(msg.icon);
            } else {
                Bitmap bitmapIcon = BitmapFactory.decodeResource(StkAppService.this
                    .getResources().getSystem(),
                    com.android.internal.R.drawable.stat_notify_sim_toolkit);
                notificationBuilder.setLargeIcon(bitmapIcon);
            }

            mNotificationManager.notify(STK_NOTIFICATION_ID, notificationBuilder.build());
        }
    }

    private void launchToneDialog() {
        Intent newIntent = new Intent(this, ToneDialog.class);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_HISTORY
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | getFlagActivityNoUserAction(InitiatedByUserAction.unknown));
        newIntent.putExtra("TEXT", mCurrentCmd.geTextMessage());
        newIntent.putExtra("TONE", mCurrentCmd.getToneSettings());
        startActivity(newIntent);
    }

    private void launchOpenChannelDialog() {
        TextMessage msg = mCurrentCmd.geTextMessage();
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

    private void launchTransientEventMessage() {
        TextMessage msg = mCurrentCmd.geTextMessage();
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

    private String getItemName(int itemId) {
        Menu menu = mCurrentCmd.getMenu();
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

    private boolean removeMenu() {
        try {
            if (mCurrentMenu.items.size() == 1 &&
                mCurrentMenu.items.get(0) == null) {
                return true;
            }
        } catch (NullPointerException e) {
            CatLog.d(this, "Unable to get Menu's items size");
            return true;
        }
        return false;
    }

    private void launchUserActivityListener() {

        if (mUserActivityReceiver == null) {
            mUserActivityReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();

                    int mStkUserActivity = SystemProperties.getInt(TelephonyProperties.PROPERTY_STK_USERACTIVITY, 0);
                    CatLog.d(this, "mUserActivityReceiver Action="+action+"mStkUserActivity"+mStkUserActivity);
                    if (mStkUserActivity ==1) {
                        SystemProperties.set(TelephonyProperties.PROPERTY_STK_USERACTIVITY, "0");
                        Bundle args = new Bundle();
                        args.putInt(StkAppService.OPCODE, StkAppService.OP_RESPONSE);
                        args.putInt(StkAppService.RES_ID, RES_ID_EVENTDOWNLOAD);
                        args.putBoolean(StkAppService.CONFIRMATION, true);
                        args.putInt(StkAppService.EVENT_DOWNLOAD_ID, EVENTLIST_USER_ACTIVITY);
                        args.putInt(StkAppService.EVENT_SOURCE_ID, DI_ME);
                        args.putInt(StkAppService.EVENT_DESTINATION_ID, DI_SIM);

                        Message msg = mServiceHandler.obtainMessage();
                        msg.arg1 = StkAppService.OP_RESPONSE;
                        msg.obj = args;
                        mServiceHandler.sendMessage(msg);

                        if(mUserActivityReceiver != null) {
                            unregisterReceiver(mUserActivityReceiver);
                            mUserActivityReceiver = null;
                        }
                    }
                }
            };

            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(TelephonyIntents.ACTION_STK_KEYEVENT);
            iFilter.addAction(TelephonyIntents.ACTION_STK_TOUCHEVENT);
            iFilter.addAction(Intent.ACTION_SCREEN_ON);
            registerReceiver(mUserActivityReceiver, iFilter);

            SystemProperties.set(TelephonyProperties.PROPERTY_STK_USERACTIVITY, "1");
            CatLog.d(this, "launchUserActivityListener finish");

        } else {
            CatLog.d(this, "mUserActivityReceiver has been lunched");
        }
    }

    private void launchIdleScreenListener() {

        if (mIdleScreenReceiver == null) {
            mIdleScreenReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {

                    String action = intent.getAction();
                    CatLog.d(this, "mIdleScreenReceiver Action="+action);

                    Message msg = mServiceHandler.obtainMessage();
                    msg.arg1 = OP_STK_IDLESCREEN;
                    mServiceHandler.sendMessageDelayed(msg,500);

                }
            };

            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(TelephonyIntents.ACTION_STK_KEYEVENT);
            registerReceiver(mIdleScreenReceiver, iFilter);

            SystemProperties.set(TelephonyProperties.PROPERTY_STK_IDLESCREEN, "1");
            CatLog.d(this, "registerKeyEventListener finish");
        } else {
            CatLog.d(this, "mIdleScreenReceiver has been lunched");
        }
    }

    private void processIdleScreen() {
        ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        String getTopPackageName = am.getRunningTasks(30).get(0).topActivity.getPackageName();

        int mStkIdleScreen= SystemProperties.getInt(TelephonyProperties.PROPERTY_STK_IDLESCREEN, 0);
        CatLog.d(this, "mIdleScreenReceiver topActivity="+getTopPackageName+"mStkIdleScreen="+mStkIdleScreen);
        if((getTopPackageName.equals("com.android.launcher")) && (mStkIdleScreen == 1)) {
            SystemProperties.set(TelephonyProperties.PROPERTY_STK_IDLESCREEN, "0");
            CatLog.d(this,"processIdleScreen launcher");
            Bundle args = new Bundle();
            args.putInt(StkAppService.OPCODE, StkAppService.OP_RESPONSE);
            args.putInt(StkAppService.RES_ID, RES_ID_EVENTDOWNLOAD);
            args.putBoolean(StkAppService.CONFIRMATION, true);
            args.putInt(StkAppService.EVENT_DOWNLOAD_ID, EVENTLIST_IDLE_SCREEN_AVAILABLE);
            args.putInt(StkAppService.EVENT_SOURCE_ID, DI_DISPLAY);
            args.putInt(StkAppService.EVENT_DESTINATION_ID, DI_SIM);

            Message msg = mServiceHandler.obtainMessage();
            msg.arg1 = StkAppService.OP_RESPONSE;
            msg.obj = args;
            mServiceHandler.sendMessage(msg);



            if(mIdleScreenReceiver != null) {
                unregisterReceiver(mIdleScreenReceiver);
                mIdleScreenReceiver = null;
            }
        }
    }

    private void processDisableEventList() {

        if (mLanguageEventReceiver != null) {
            CatLog.d(this, "unregisterReceiver(), mLanguageEventReceiver");
            unregisterReceiver(mLanguageEventReceiver);
            mLanguageEventReceiver = null;
        }

        if (mUserActivityReceiver != null) {
            CatLog.d(this, "unregisterReceiver(), mUserActivityReceiver");
            unregisterReceiver(mUserActivityReceiver);
            mUserActivityReceiver = null;
        }

        if (mIdleScreenReceiver != null) {
            CatLog.d(this, "unregisterReceiver(), mIdleScreenReceiver");
            unregisterReceiver(mIdleScreenReceiver);
            mIdleScreenReceiver = null;
        }
    }

    private void launchLanguageListener() {
        if (mLanguageEventReceiver == null) {
            mCurrentLanguage = null;
            try {
                IActivityManager am = ActivityManagerNative.getDefault();
                Configuration config = am.getConfiguration();
                mCurrentLanguage = config.locale.getLanguage();
            } catch (RemoteException e) {
                CatLog.d(this, "Caught Exception on");
                return;
            }

            CatLog.d(this, "new mLanguageEventReceiver, mCurrentLanguage="+mCurrentLanguage);
            mLanguageEventReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    CatLog.d(this, "mLanguageEventReceiver Action = "+action);

                    if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
                        CatLog.d(this, "Receive ACTION_CONFIGURATION_CHANGED Notify");
                        try {
                            IActivityManager am = ActivityManagerNative.getDefault();
                            Configuration config = am.getConfiguration();
                            String ConfigLanguage = config.locale.getLanguage();
                            CatLog.d(this, "ConfigLanguage="+ConfigLanguage+" ,mCurrentLanguage="+mCurrentLanguage);

                            if (ConfigLanguage != mCurrentLanguage) {

                                Bundle args = new Bundle();
                                args.putInt(StkAppService.OPCODE, StkAppService.OP_RESPONSE);
                                args.putInt(StkAppService.RES_ID, RES_ID_EVENTDOWNLOAD);
                                args.putBoolean(StkAppService.CONFIRMATION, true);
                                args.putInt(StkAppService.EVENT_DOWNLOAD_ID, EVENTLIST_LANGUAGE_SELECTION);
                                args.putInt(StkAppService.EVENT_SOURCE_ID, DI_ME);
                                args.putInt(StkAppService.EVENT_DESTINATION_ID, DI_SIM);

                                byte[] LanguageRawData = ConfigLanguage.getBytes();

                                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                                buf.write(0x2d);
                                buf.write(0x00);

                                for (int i = 0; i < LanguageRawData.length; i++) {
                                    buf.write(LanguageRawData[i]);
                                }

                                byte[] rawData = buf.toByteArray();
                                int len = rawData.length - 2; // Header(0x2d)+ Length
                                rawData[1] = (byte) len;

                                StringBuilder sb = new StringBuilder();
                                for (int j = 0; j < rawData.length; j++) {
                                    String Data = String.format("%x",rawData[j]);
                                    sb.append(Data);
                                    sb.append(" ");
                                }

                                CatLog.d(this, "ADDITIONINFO="+sb.toString());
                                args.putByteArray(StkAppService.EVENT_ADDITIONINFO,rawData);

                                Message msg = mServiceHandler.obtainMessage();
                                msg.arg1 = StkAppService.OP_RESPONSE;
                                msg.obj = args;
                                mServiceHandler.sendMessage(msg);
                                mCurrentLanguage = ConfigLanguage;
                            }
                        } catch (RemoteException e) {
                            CatLog.d(this, "Caught Exception");
                            return;
                        }
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
            registerReceiver(mLanguageEventReceiver, iFilter);
            CatLog.d(this, "registerLanguageListener finish");
        } else {
            CatLog.d(this, "mLanguageEventReceiver has been lunched !!!");
        }
    }
}

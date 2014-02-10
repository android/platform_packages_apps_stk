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

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.internal.telephony.cat.Item;
import com.android.internal.telephony.cat.Menu;
import com.android.internal.telephony.cat.CatLog;

/**
 * ListActivity used for displaying STK menus. These can be SET UP MENU and
 * SELECT ITEM menus. This activity is started multiple times with different
 * menu content.
 *
 */
public class StkMenuActivity extends ListActivity {
    private StkMenuInstance mMenuInstance = new StkMenuInstance();
    private TextView mTitleTextView = null;
    private ImageView mTitleIconView = null;
    private ProgressBar mProgressView = null;
    private static final String LOGTAG = "Stk-MA ";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        CatLog.d(LOGTAG, "onCreate+");
        mMenuInstance.parent = this;       
        // Remove the default title, customized one is used.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // Set the layout for this activity.
        setContentView(R.layout.stk_menu_list);

        mTitleTextView = (TextView) findViewById(R.id.title_text);
        mTitleIconView = (ImageView) findViewById(R.id.title_icon);
        mProgressView = (ProgressBar) findViewById(R.id.progress_bar);
        mMenuInstance.handleOnCreate(getBaseContext(), getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        CatLog.d(LOGTAG, "onNewIntent");
        mMenuInstance.handleNewIntent(intent);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        mMenuInstance.handleListItemClick(position, mProgressView);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean result = mMenuInstance.handleKeyDown(keyCode, event);
        if (result)
            return result;
        else
            return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onResume() {
        super.onResume();

        CatLog.d(LOGTAG, "onResume, sim id: " + mMenuInstance.mSimId);
        int res = mMenuInstance.handleResume(mTitleIconView, mTitleTextView, this, mProgressView);
        switch(res)
        {
            case StkMenuInstance.FINISH_CAUSE_NULL_MENU:
                mMenuInstance.showTextToast(getApplicationContext(), getString(R.string.main_menu_not_initialized));
                finish();
                break;
            case StkMenuInstance.FINISH_CAUSE_NULL_SERVICE:
                CatLog.d(LOGTAG, "app service is null");                
                finish();
                break;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        CatLog.d(LOGTAG, "onPause, sim id: " + mMenuInstance.mSimId);
        mMenuInstance.handlePause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        CatLog.d(LOGTAG, "onDestroy");
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, StkApp.MENU_ID_END_SESSION, 1, R.string.menu_end_session);
        menu.add(0, StkApp.MENU_ID_HELP, 2, R.string.help);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        super.onPrepareOptionsMenu(menu);
        
        return mMenuInstance.handlePrepareOptionMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean result = mMenuInstance.handleOptionItemSelected(item, mProgressView);
        CatLog.d(LOGTAG, "onOptionsItemSelected, result: " + result);
        if (result)
            return result;
        else
            return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("STATE", mMenuInstance.mState);
        outState.putParcelable("MENU", mMenuInstance.mStkMenu);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        mMenuInstance.mState = savedInstanceState.getInt("STATE");
        mMenuInstance.mStkMenu = savedInstanceState.getParcelable("MENU");
    }
}

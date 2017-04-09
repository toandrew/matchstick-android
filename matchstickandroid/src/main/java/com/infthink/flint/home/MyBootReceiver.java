/**
 * Copyright (C) 2013-2015, Infthink (Beijing) Technology Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS-IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.infthink.flint.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.util.Log;

/**
 * Boot receiver.
 */
public class MyBootReceiver extends BroadcastReceiver {
    private static final String TAG = "FlintReceiver";

    private final String ACTION_BOOT = "android.intent.action.BOOT_COMPLETED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_BOOT.equals(intent.getAction())) {
            Log.e(TAG, "System reboot completed,start fling server.......");
            if (!FlintServerService.isRunning()) {
                Intent flingIntent = new Intent(context,
                        FlintServerService.class);
                context.startService(flingIntent);
            }
        } else {
            if (Globals.getContext() == null) {
                Context c = context.getApplicationContext();
                if (context == null)
                    throw new NullPointerException("Null context!?!?!?");
                Globals.setContext(c);
            }

            int network = Globals.checkNet();
            if (network == ConnectivityManager.TYPE_ETHERNET) {
                if (!FlintServerService.isRunning()) {
                    Intent flingIntent = new Intent(context,
                            FlintServerService.class);
                    context.startService(flingIntent);
                }
                return;
            }

            WifiManager wifiMgr = (WifiManager) context
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifiMgr.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
                if (!FlintServerService.isRunning()) {
                    Intent flingIntent = new Intent(context,
                            FlintServerService.class);
                    context.startService(flingIntent);
                }
            }
        }
    }
}
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

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

import org.apache.http.conn.util.InetAddressUtils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

public class Globals {
    private static Context context;

    protected static String settingsName = "FlingServer";

    protected static int settingsMode = Context.MODE_WORLD_WRITEABLE|Context.MODE_WORLD_READABLE;

    public static Context getContext() {
        return context;
    }

    public static void setContext(Context context) {
        if (context != null) {
            Globals.context = context;
        }
    }

    public static void sleepIgnoreInterupt(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
        }
    }

    public static byte byteOfInt(int value, int which) {
        int shift = which * 8;
        return (byte) (value >> shift);
    }

    public static InetAddress intToInet(int value) {
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            bytes[i] = byteOfInt(value, i);
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            // This only happens if the byte array has a bad length
            return null;
        }
    }

    public static int checkNet() {
        // TODO Auto-generated method stub
        if (context == null) {
            return -1;
        }

        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mobNetInfoActivity = connectivityManager
                .getActiveNetworkInfo();

        int netFlag = -1;

        if (mobNetInfoActivity != null) {
            switch (mobNetInfoActivity.getType()) {
            case ConnectivityManager.TYPE_ETHERNET:
                netFlag = ConnectivityManager.TYPE_ETHERNET;
                break;
            case ConnectivityManager.TYPE_WIFI:
                netFlag = ConnectivityManager.TYPE_WIFI;
                break;
            /*
             * case ConnectivityManager.TYPE_MOBILE: netFlag =
             * ConnectivityManager.TYPE_MOBILE; break;
             */
            default:
                break;
            }
        }

        return netFlag;
    }

    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf
                        .getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()
                            && InetAddressUtils.isIPv4Address(inetAddress
                                    .getHostAddress())) {
                        return inetAddress.getHostAddress().toString();
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e("Fling", "IpAddress:" + ex.toString());
        }

        return null;
    }

    public static String getSettingsName() {
        return settingsName;
    }

    public static void setSettingsName(String settingsName) {
        Globals.settingsName = settingsName;
    }

    public static int getSettingsMode() {
        return settingsMode;
    }

    /**
     * Gets the IP address of the wifi connection.
     * 
     * @return The integer IP address if wifi enabled, or null if not.
     */
    public static InetAddress getWifiIp() {
        Context myContext = Globals.getContext();
        if (myContext == null) {
            throw new NullPointerException("Global context is null");
        }
        WifiManager wifiMgr = (WifiManager) myContext
                .getSystemService(Context.WIFI_SERVICE);
        if (isWifiEnabled()) {
            int ipAsInt = wifiMgr.getConnectionInfo().getIpAddress();
            if (ipAsInt == 0) {
                return null;
            } else {
                return Globals.intToInet(ipAsInt);
            }
        } else {
            return null;
        }
    }

    /**
     * Whether WIfi is enabled!
     * 
     * @return
     */
    public static boolean isWifiEnabled() {
        Context myContext = Globals.getContext();
        if (myContext == null) {
            throw new NullPointerException("Global context is null");
        }
        WifiManager wifiMgr = (WifiManager) myContext
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiMgr.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
            return true;
        } else {
            return false;
        }
    }
}

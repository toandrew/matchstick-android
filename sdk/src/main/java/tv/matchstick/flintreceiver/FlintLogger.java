/*
 * Copyright (C) 2013-2015, The OpenFlint Open Source Project
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

package tv.matchstick.flintreceiver;

import android.util.Log;

/**
 * Log utils
 * 
 * @author jim
 *
 */
public class FlintLogger {
    private static boolean DEBUG = true;

    private final String TAG;

    public FlintLogger(String tag, boolean enable) {
        TAG = tag;
        DEBUG = enable;
    }

    public FlintLogger(String tag) {
        this(tag, DEBUG);
    }

    /**
     * Log.v
     * 
     * @param message
     */
    public void v(String message) {
        if (!FlintReceiverManager.isLogEnabled()) {
            return;
        }

        Log.v(TAG, message);
    }

    /**
     * Log.d
     * 
     * @param message
     */
    public void d(String message) {
        if (!FlintReceiverManager.isLogEnabled() || !DEBUG) {
            return;
        }

        Log.d(TAG, message);
    }

    /**
     * Log.i
     * 
     * @param message
     */
    public void i(String message) {
        if (!FlintReceiverManager.isLogEnabled() || !DEBUG) {
            return;
        }

        Log.i(TAG, message);
    }

    /**
     * Log.w
     * 
     * @param message
     */
    public void w(String message) {
        if (!FlintReceiverManager.isLogEnabled() || !DEBUG) {
            return;
        }

        Log.w(TAG, message);
    }

    /**
     * Log.e
     * 
     * @param message
     */
    public void e(String message) {
        if (!FlintReceiverManager.isLogEnabled() || !DEBUG) {
            return;
        }

        Log.e(TAG, message);
    }
}

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

import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Interact messages with Fling daemon and Sender Apps
 * 
 * @author jim
 *
 */
abstract class MessageChannel implements FlintWebSocketListener {
    private static final String TAG = "MessageChannel";

    private FlintLogger log = new FlintLogger(TAG);

    public static final String NAMESPACE = "namespace";

    private boolean mIsOpened = false;

    private FlintWebSocket mWebSocket = null;

    protected HashMap<String, MessageBus> mMessageBusMap = new HashMap<String, MessageBus>();

    private String mName;

    private String mUrl;

    public MessageChannel(String name, String url) {
        mName = name;
        mUrl = url;
    }

    /**
     * Whether the message channel is opened.
     * 
     * @return
     */
    public boolean isOpened() {
        return mIsOpened;
    }

    /**
     * Get the message channel's name
     * 
     * @return
     */
    public String getName() {
        return mName;
    }

    /**
     * Open the message channel.
     * 
     * @return
     */
    public boolean open() {
        return open(null);
    }

    /**
     * Open the message channel.
     * 
     * @param url
     * @return
     */
    public boolean open(final String url) {
        if (url != null) {
            mUrl = url;
        }

        log.e("open: url[" + mUrl + "]");

        mWebSocket = new FlintWebSocket(this, URI.create(mUrl));
        mWebSocket.connect();

        mIsOpened = true;

        return true;
    }

    /**
     * Close the message channel.
     * 
     * @return
     */
    public boolean close() {
        mIsOpened = false;

        if (mWebSocket != null) {
            mWebSocket.close();
        }

        return true;
    }

    /**
     * Send messages
     * 
     * @param data
     */
    public void send(final String data) {
        if (!mIsOpened) {
            log.e("MessageChannel is not opened, cannot sent:" + data);
            return;
        }

        if (data == null) {
            return;
        }

        if (mWebSocket.isOpen()) {
            mWebSocket.send(data);
        } else if (mWebSocket.isConnecting()) {
            Timer timer = new Timer();
            TimerTask task = new TimerTask() {

                @Override
                public void run() {
                    // TODO Auto-generated method stub
                    send(data);
                }

            };

            timer.schedule(task, 50);
        } else {
            log.e("MessageChannel send failed, channel readyState is:"
                    + mWebSocket.getReadyState());
        }
    }

    /**
     * Get Senders
     * 
     * @return
     */
    public HashMap<String, String> getSenders() {
        return null;
    }

    /**
     * Register MessageBus
     * 
     * @param messageBus
     * @param namespace
     */
    public void registerMessageBus(MessageBus messageBus, final String namespace) {
        if (messageBus != null && namespace != null) {
            mMessageBusMap.put(namespace, messageBus);
        }
    }

    /**
     * Unregister MessageBus
     * 
     * @param namespace
     */
    public void unRegisterMessageBus(final String namespace) {
        if (namespace != null) {
            mMessageBusMap.remove(namespace);
        }
    }

    /**
     * Notify all MessageBus obj about open events
     */
    public void onOpen(final String data) {
        Iterator<Entry<String, MessageBus>> iter = mMessageBusMap.entrySet()
                .iterator();
        while (iter.hasNext()) {
            Map.Entry<String, MessageBus> entry = (Map.Entry<String, MessageBus>) iter
                    .next();
            MessageBus bus = (MessageBus) entry.getValue();
            bus.onOpen(data);
        }
    }

    /**
     * Notify all MessageBus obj about close event
     */
    public void onClose(final String data) {
        Iterator<Entry<String, MessageBus>> iter = mMessageBusMap.entrySet()
                .iterator();
        while (iter.hasNext()) {
            Map.Entry<String, MessageBus> entry = (Map.Entry<String, MessageBus>) iter
                    .next();
            MessageBus bus = (MessageBus) entry.getValue();
            bus.onClose(data);
        }
    }

    /**
     * Notify all MessageBus obj about error event
     */
    public void onError(final String data) {
        Iterator<Entry<String, MessageBus>> iter = mMessageBusMap.entrySet()
                .iterator();
        while (iter.hasNext()) {
            Map.Entry<String, MessageBus> entry = (Map.Entry<String, MessageBus>) iter
                    .next();
            MessageBus bus = (MessageBus) entry.getValue();
            bus.onError(data);
        }
    }

    /**
     * Notify all MessageBus obj about message received event
     */
    public void onMessage(final String data) {
        log.d("onMessage:" + data);
    }
}

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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONObject;

/**
 * Receiver's message channel.
 * 
 * @author jim
 *
 */
public class ReceiverMessageChannel extends MessageChannel {
    private static final String TAG = "ReceiverMessageChannel";

    private FlintLogger log = new FlintLogger(TAG);

    private HashMap<String, String> mSenders = new HashMap<String, String>();

    private static final String MSG_DATA = "data";
    private static final String MSG_DATA_TYPE = "type";

    private static final String MSG_DATA_SENDERCONNECTED = "senderConnected";
    private static final String MSG_DATA_SENDERDISCONNECTED = "senderDisconnected";

    private static final String MSG_DATA_MESSAGE = "message";
    private static final String MSG_DATA_ERROR = "error";
    private static final String MSG_DATA_DATA = "data";
    private static final String MSG_DATA_SENDERID = "senderId";
    private static final String MSG_DATA_NAMESPACE = "namespace";

    public ReceiverMessageChannel(String name, String url) {
        super(name, url);
        // TODO Auto-generated constructor stub

        mSenders.clear();
    }

    @Override
    public void send(final String data) {
        super.send(data);
    }

    @Override
    public HashMap<String, String> getSenders() {
        // TODO Auto-generated method stub

        return mSenders;
    }

    @Override
    public void onOpen(final String data) {
        // TODO Auto-generated method stub

        super.onOpen(data);
    }

    @Override
    public void onClose(final String data) {
        // TODO Auto-generated method stub

        super.onClose(data);
    }

    @Override
    public void onError(final String data) {
        // TODO Auto-generated method stub

        super.onError(data);
    }

    @Override
    public void onMessage(final String data) {
        // TODO Auto-generated method stub

        super.onMessage(data);

        try {
            JSONObject d = new JSONObject(data);

            // JSONObject d = message.getJSONObject(MSG_DATA);

            String type = d.getString(MSG_DATA_TYPE);
            if (type.equals(MSG_DATA_SENDERCONNECTED)) {
                mSenders.put(MSG_DATA_SENDERID, d.getString(MSG_DATA_SENDERID));

                String senderId = d.getString(MSG_DATA_SENDERID);
                onSenderConnected(senderId);
            } else if (type.equals(MSG_DATA_SENDERDISCONNECTED)) {
                mSenders.remove(d.getString(MSG_DATA_SENDERID));

                String senderId = d.getString(MSG_DATA_SENDERID);
                onSenderDisConnected(senderId);
            } else if (type.equals(MSG_DATA_MESSAGE)) {
                String m = d.getString(MSG_DATA_DATA);

                String senderId = d.getString(MSG_DATA_SENDERID);
                onMessageReceived(m, senderId);
            } else if (type.equals(MSG_DATA_ERROR)) {
                String ex = d.getString(MSG_DATA_MESSAGE);
                onErrorHappened(ex);
            } else {
                log.e("ReceiverMessageChannel unknow data.type:" + type);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Called when Sender App connected.
     * 
     * @param senderId
     */
    public void onSenderConnected(final String senderId) {
        try {
            Iterator<Entry<String, MessageBus>> iter = mMessageBusMap
                    .entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, MessageBus> entry = (Map.Entry<String, MessageBus>) iter
                        .next();
                MessageBus bus = (MessageBus) entry.getValue();
                bus.onSenderConnected(senderId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Called when Sender App disconnected.
     * 
     * @param senderId
     */
    public void onSenderDisConnected(final String senderId) {
        try {
            Iterator<Entry<String, MessageBus>> iter = mMessageBusMap
                    .entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, MessageBus> entry = (Map.Entry<String, MessageBus>) iter
                        .next();
                MessageBus bus = (MessageBus) entry.getValue();
                bus.onSenderDisconnected(senderId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Called when message received
     * 
     * @param data
     * @param senderId
     */
    public void onMessageReceived(final String data, final String senderId) {
        try {
            JSONObject json = new JSONObject(data);
            String namespace = json.getString(NAMESPACE);

            Iterator<Entry<String, MessageBus>> iter = mMessageBusMap
                    .entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, MessageBus> entry = (Map.Entry<String, MessageBus>) iter
                        .next();

                String ns = (String) entry.getKey();
                if (ns.equals(namespace)) {
                    MessageBus bus = (MessageBus) entry.getValue();
                    bus.onMessageReceived(data, senderId);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Called when error happened.
     * 
     * @param ex
     */
    public void onErrorHappened(final String ex) {
        try {
            Iterator<Entry<String, MessageBus>> iter = mMessageBusMap
                    .entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, MessageBus> entry = (Map.Entry<String, MessageBus>) iter
                        .next();

                MessageBus bus = (MessageBus) entry.getValue();
                bus.onErrorHappened(ex);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

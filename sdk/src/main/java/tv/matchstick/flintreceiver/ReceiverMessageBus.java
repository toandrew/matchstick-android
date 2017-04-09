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

import org.json.JSONObject;

/**
 * This is used to transfer messages between sender and receiver Apps.
 * 
 * @author jim
 *
 */
public abstract class ReceiverMessageBus extends MessageBus {
    private static final String TAG = "ReceiverMessageBus";

    private FlintLogger log = new FlintLogger(TAG);

    private static final String PAYLOAD = "payload";
    private static final String NAMESPACE = "namespace";
    private static final String DATA = "data";
    private static final String SENDERID = "senderId";

    private static final String BROADCAST_SENDER_ID = "*:*";

    HashMap<String, String> mSenders = new HashMap<String, String>();

    protected ReceiverMessageBus(String namespace) {
        super(namespace);
        // TODO Auto-generated constructor stub
    }

    @Override
    public void init() {
        // TODO Auto-generated method stub

    }

    @Override
    public void send(final String data, final String senderId) {
        // TODO Auto-generated method stub

        if (data == null) {
            log.e("data is null!ignore send!");
            return;
        }

        try {
            JSONObject message = new JSONObject();
            message.put(NAMESPACE, mNamespace);
            message.put(PAYLOAD, data);

            JSONObject obj = new JSONObject();

            if (senderId == null) {
                obj.put(SENDERID, BROADCAST_SENDER_ID); // all
            } else {
                obj.put(SENDERID, senderId);
            }
            obj.put(DATA, message.toString());

            log.e("send[" + obj.toString() + "]");

            mMessageChannel.send(obj.toString());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public HashMap<String, String> getSenders() {
        // TODO Auto-generated method stub

        return mSenders;
    }

    @Override
    public void onSenderConnected(final String senderId) {
        // TODO Auto-generated method stub

        mSenders.put(senderId, senderId);
    }

    @Override
    public void onSenderDisconnected(final String senderId) {
        // TODO Auto-generated method stub

        mSenders.remove(senderId);
    }

    @Override
    public void onMessageReceived(final String data, final String senderId) {
        // TODO Auto-generated method stub

        try {
            JSONObject json = new JSONObject(data);
            String payload = json.getString(PAYLOAD);

            onPayloadMessage(payload, senderId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onErrorHappened(final String ex) {
        // TODO Auto-generated method stub

    }

    /**
     * Received valid payload message
     * 
     * @param payload
     * @param senderId
     */
    public abstract void onPayloadMessage(final String payload,
            final String senderId);
}

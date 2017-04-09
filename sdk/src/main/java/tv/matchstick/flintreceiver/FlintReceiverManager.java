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
 * Flint Receiver Manager
 * 
 * This is used by java Receiver Apps to interact with Flint daemon and Sender
 * Apps
 * 
 * @author jim
 *
 */
public class FlintReceiverManager {
    private static final String TAG = "FlintReceiverManager";

    private FlintLogger log = new FlintLogger(TAG);
    
    public static String DEFAULT_CHANNEL_NAME = "channelBaseUrl";
    public static String DEFAULT_NAMESPACE = "urn:flint:org.openflint.default";

    // log flag
    private static boolean mLogEnabled = true;

    private static final String IPC_CHANNEL_NAME = "ipc";

    private static final String IPC_MESSAGE_TYPE = "type";

    private static final String IPC_MESSAGE_STARTHEARTBEAT = "startHeartbeat";
    private static final String IPC_MESSAGE_REGISTEROK = "registerok";
    private static final String IPC_MESSAGE_HEARTBEAT = "heartbeat";
    private static final String IPC_MESSAGE_SENDERCONNECTED = "senderconnected";
    private static final String IPC_MESSAGE_SENDERDISCONNECTED = "senderdisconnected";
    private static final String IPC_MESSAGE_APPID = "appid";

    private static final String IPC_MESSAGE_DATA = "data";

    private static final String IPC_MESSAGE_DATA_TOKEN = "token";
    private static final String IPC_MESSAGE_DATA_HEARTBEAT = "heartbeat";
    private static final String IPC_MESSAGE_DATA_HEARTBEAT_PING = "ping";
    private static final String IPC_MESSAGE_DATA_HEARTBEAT_PONG = "pong";

    private static final String IPC_MESSAGE_DATA_CHANNELBASEURL = "channelBaseUrl";
    private static final String IPC_MESSAGE_DATA_SERVICE_INFO = "service_info";
    private static final String IPC_MESSAGE_DATA_SERVICE_INFO_IP = "ip";
    private static final String IPC_MESSAGE_DATA_ADDITIONALDATA = "additionaldata";
    private static final String IPC_MESSAGE_DATA_REGISTER = "register";
    private static final String IPC_MESSAGE_DATA_UNREGISTER = "unregister";

    private static final String IPC_MESSAGE_TYPE_HEARTBEAT = "heartbeat";
    private static final String IPC_MESSAGE_TYPE_ADDITIONALDATA = "additionaldata";

    private String mAppId;

    private MessageChannel mIpcChannel;

    private MessageChannel mMessageChannel;

    private String mIpcAddress;

    private String mFlintServerIp = "127.0.0.1";

    private HashMap<String, MessageBus> mMessageBusMap = new HashMap<String, MessageBus>();

    private String mCustAdditionalData;

    public FlintReceiverManager(String appId) {
        mAppId = appId;

        mIpcAddress = "ws://127.0.0.1:9431/receiver/" + appId;
    }

    /**
     * Ready to start receive messages from Flint daemon and Sender Apps
     * 
     * @return the result
     */
    public boolean open() {
        if (isOpened()) {
            log.e("FlintReceiverManager is already opened!");
            return true;
        }

        mIpcChannel = new MessageChannel(IPC_CHANNEL_NAME, mIpcAddress) {

            @Override
            public void onOpen(String data) {
                // TODO Auto-generated method stub

                log.e("ipcChannel opened!!!");

                // send register message
                JSONObject register = new JSONObject();
                try {
                    register.put(IPC_MESSAGE_TYPE, IPC_MESSAGE_DATA_REGISTER);

                    ipcSend(register);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(String data) {
                // TODO Auto-generated method stub

                log.e("ipcChannel closed!!!");
            }

            @Override
            public void onError(String data) {
                // TODO Auto-generated method stub

                log.e("ipcChannel error!!!");
            }

            @Override
            public void onMessage(String data) {
                // TODO Auto-generated method stub

                log.e("ipcChannel received message: [" + data + "]");

                try {
                    JSONObject json = new JSONObject(data);

                    onIpcMessage(json);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        mIpcChannel.open();

        if (mMessageChannel != null) {
            mMessageChannel.open();
        }

        return true;
    }

    /**
     * Stop to receive messages from Flint Daemon and Sender Apps.
     * 
     * @return the result
     */
    public boolean close() {
        if (isOpened()) {
            JSONObject unregister = new JSONObject();
            try {
                unregister.put(IPC_MESSAGE_TYPE, IPC_MESSAGE_DATA_UNREGISTER);
                ipcSend(unregister);
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (mMessageChannel != null) {
                mMessageChannel.close();
            }

            if (mMessageBusMap != null) {
                try {
                    Iterator<Entry<String, MessageBus>> iter = mMessageBusMap
                            .entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry<String, MessageBus> entry = (Map.Entry<String, MessageBus>) iter
                                .next();
                        MessageBus bus = (MessageBus) entry.getValue();
                        bus.unSetMessageChannel();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                mMessageBusMap = null;
            }

            if (mIpcChannel != null) {
                mIpcChannel.close();
            }

            return true;
        }

        log.e("FlintReceiverManager is not started, cannot close!!!");

        return false;
    }

    /**
     * Used to set message bus objects.
     * 
     * @param namespace
     *            the message bus's name
     * @param bus
     *            Message bus
     * @return
     */
    public MessageBus setMessageBus(String namespace, ReceiverMessageBus bus) {
        String ns = namespace;

        if (isOpened()) {
            log.e("cannot create MessageBus: FlintReceiverManager is already opened!");
            return null;
        }

        if (namespace == null) {
            ns = DEFAULT_NAMESPACE;
        }

        if (mMessageChannel == null) {
            mMessageChannel = createMessageChannel(DEFAULT_CHANNEL_NAME);
        }

        bus.setMessageChannel(mMessageChannel);

        mMessageBusMap.put(ns, bus);

        return bus;
    }

    /**
     * Set additional data.
     * 
     * @param data
     */
    public void setAdditionalData(String data) {
        log.e("set custom additionaldata: " + data);

        mCustAdditionalData = data;

        sendAdditionalData();
    }

    /**
     * Enables or disables verbose logging for this Fling session.
     */
    public static void setLogEnabled(boolean enable) {
        mLogEnabled = enable;
    }

    /**
     * Get whether log flag
     * 
     * @return
     */
    public static boolean isLogEnabled() {
        return mLogEnabled;
    }

    /**
     * Whether the receiver manager is already opened.
     * 
     * @return
     */
    private boolean isOpened() {
        if (mIpcChannel != null && mIpcChannel.isOpened()) {
            return true;
        }

        return false;
    }

    /**
     * Send IPC related message
     */
    private void ipcSend(JSONObject data) {
        JSONObject json = data;
        try {
            json.put(IPC_MESSAGE_APPID, mAppId);

            if (mIpcChannel != null) {
                log.e("ipcSend:[" + json.toString() + "]");

                mIpcChannel.send(json.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Process received IPC message
     * 
     * @param data
     */
    private void onIpcMessage(JSONObject data) {
        try {
            String type = data.getString(IPC_MESSAGE_TYPE);

            if (type.equals(IPC_MESSAGE_STARTHEARTBEAT)) {
                log.e("receiver ready to start heartbeat!!!");
            } else if (type.equals(IPC_MESSAGE_REGISTEROK)) {

                mFlintServerIp = data
                        .getJSONObject(IPC_MESSAGE_DATA_SERVICE_INFO)
                        .getJSONArray(IPC_MESSAGE_DATA_SERVICE_INFO_IP)
                        .getString(0);

                log.e("receiver register done!!![" + mFlintServerIp + "]");

                sendAdditionalData();
            } else if (type.equals(IPC_MESSAGE_HEARTBEAT)) {
                String t = data.getString(IPC_MESSAGE_DATA_HEARTBEAT);
                if (t.equals(IPC_MESSAGE_DATA_HEARTBEAT_PING)) {
                    JSONObject pong = new JSONObject();
                    try {
                        pong.put(IPC_MESSAGE_TYPE, IPC_MESSAGE_DATA_HEARTBEAT);
                        pong.put(IPC_MESSAGE_TYPE_HEARTBEAT,
                                IPC_MESSAGE_DATA_HEARTBEAT_PONG);
                        ipcSend(pong);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else if (t.equals(IPC_MESSAGE_DATA_HEARTBEAT_PONG)) {
                    JSONObject pong = new JSONObject();
                    try {
                        pong.put(IPC_MESSAGE_TYPE, IPC_MESSAGE_DATA_HEARTBEAT);
                        pong.put(IPC_MESSAGE_TYPE_HEARTBEAT,
                                IPC_MESSAGE_DATA_HEARTBEAT_PING);
                        ipcSend(pong);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    log.e("unknow heartbeat message:" + t);
                }
            } else if (type.equals(IPC_MESSAGE_SENDERCONNECTED)) {
                log.e("IPC senderconnected: "
                        + data.getString(IPC_MESSAGE_DATA_TOKEN));
            } else if (type.equals(IPC_MESSAGE_SENDERDISCONNECTED)) {
                log.e("IPC senderdisconnected: "
                        + data.getString(IPC_MESSAGE_DATA_TOKEN));
            } else {
                log.e("IPC unknow type:" + type);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Send additional data
     */
    private void sendAdditionalData() {
        JSONObject additionalData = joinAdditionalData();

        if (additionalData != null) {
            JSONObject data = new JSONObject();
            try {
                data.put(IPC_MESSAGE_TYPE, IPC_MESSAGE_DATA_ADDITIONALDATA);
                data.put(IPC_MESSAGE_TYPE_ADDITIONALDATA, additionalData);
                ipcSend(data);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            log.e("no additionaldata need to send");
        }
    }

    /**
     * Get current additional data
     */
    private JSONObject joinAdditionalData() {
        JSONObject additionalData = new JSONObject();

        try {
            if (mMessageChannel != null) {
                additionalData.put(IPC_MESSAGE_DATA_CHANNELBASEURL,
                        "ws://" + mFlintServerIp + ":9439/channels/"
                                + mMessageChannel.getName());
            }

            if (mCustAdditionalData != null) {
                additionalData.put("customData", mCustAdditionalData);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (additionalData.length() > 0) {
            return additionalData;
        }

        return null;
    }

    /**
     * Create one MessageChannel object
     * 
     * @param channelName
     * @return
     */
    private MessageChannel createMessageChannel(String channelName) {
        String url = "ws://127.0.0.1:9439/channels/" + channelName;

        MessageChannel channel = new ReceiverMessageChannel(channelName, url) {
            @Override
            public void onOpen(String data) {
                // TODO Auto-generated method stub

                log.e("Receiver default message channel open!!! " + data);
            }

            @Override
            public void onClose(String data) {
                // TODO Auto-generated method stub

                log.e("Receiver default message channel close!!! " + data);
            }

            @Override
            public void onError(String data) {
                // TODO Auto-generated method stub

                log.e("Receiver default message channel error!!! " + data);
            }
        };

        return channel;
    }
}

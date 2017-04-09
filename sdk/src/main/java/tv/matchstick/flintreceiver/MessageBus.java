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

/**
 * This is used to transfer messages between sender and receiver Apps.
 * 
 * @author jim
 *
 */
abstract class MessageBus implements FlintWebSocketListener {
    MessageChannel mMessageChannel;

    String mNamespace;

    public MessageBus(String namespace) {
        mNamespace = namespace;

        init();
    }

    /**
     * Set the relationship with the message channel.
     * 
     * @param channel
     */
    public void setMessageChannel(MessageChannel channel) {
        mMessageChannel = channel;
        mMessageChannel.registerMessageBus(this, mNamespace);
    }

    /**
     * Do some clean work
     */
    public void unSetMessageChannel() {
        if (mMessageChannel != null) {
            mMessageChannel.unRegisterMessageBus(mNamespace);
        }
    }

    abstract public void init();

    abstract public void send(final String data, final String senderId);

    abstract public HashMap<String, String> getSenders();

    /**
     * Called when Sender App connected.
     * 
     * @param senderId
     */
    public abstract void onSenderConnected(final String senderId);

    /**
     * Called when Sender App disconnected.
     * 
     * @param senderId
     */
    public abstract void onSenderDisconnected(final String senderId);

    /**
     * Called when raw message received
     * 
     * @param data
     * @param senderId
     */
    public abstract void onMessageReceived(final String data, final String senderId);

    /**
     * Called when error happened.
     * 
     * @param ex
     */
    public abstract void onErrorHappened(final String ex);

    @Override
    public void onOpen(final String data) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onClose(final String data) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onError(final String data) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onMessage(final String data) {
        // TODO Auto-generated method stub

    }
}

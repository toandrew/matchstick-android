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

package tv.matchstick.flintreceiver.media;

import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONArray;
import org.json.JSONObject;

import tv.matchstick.flintreceiver.FlintLogger;
import tv.matchstick.flintreceiver.FlintReceiverManager;
import tv.matchstick.flintreceiver.ReceiverMessageBus;

/**
 * Flint default media player which can be used to communicate media events with
 * Media Sender Apps.
 * 
 * @author jim
 *
 */
public class FlintMediaPlayer {
    private static final String TAG = "FlintMediaPlayer";

    private FlintLogger log = new FlintLogger(TAG);
    
    public static String MEDIA_NAMESPACE = "urn:flint:org.openflint.fling.media";

    public static final String PLAYER_STATE_IDLE = "IDLE";
    public static final String PLAYER_STATE_PLAYING = "PLAYING";
    public static final String PLAYER_STATE_PAUSED = "PAUSED";
    public static final String PLAYER_STATE_BUFFERING = "BUFFERING";
    public static final String PLAYER_STATE_LOADDING = "LOADDING";
    public static final String PLAYER_STATE_READY = "READY";

    private static final String PAYLOAD = "payload";

    private static final String DATA_REQUESTID = "requestId";
    private static final String DATA_TYPE = "type";

    private static final String DATA_TYPE_LOAD = "LOAD";
    private static final String DATA_TYPE_PAUSE = "PAUSE";
    private static final String DATA_TYPE_PLAY = "PLAY";
    private static final String DATA_TYPE_SET_VOLUME = "SET_VOLUME";
    private static final String DATA_TYPE_SEEK = "SEEK";
    private static final String DATA_TYPE_PING = "PING";
    private static final String DATA_TYPE_GET_STATUS = "GET_STATUS";
    private static final String DATA_TYPE_STOP = "STOP";

    private static final String DATA_MEDIA = "media";
    private static final String DATA_MEDIA_CONTENTID = "contentId";
    private static final String DATA_MEDIA_CONTENTTYPE = "contentType";
    private static final String DATA_MEDIA_METADATA = "metadata";
    private static final String DATA_MEDIA_METADATA_TITLE = "title";
    private static final String DATA_MEDIA_METADATA_SUBTITLE = "subtitle";

    private static final String DATA_MEDIA_STREAMTYPE = "streamType";
    private static final String DATA_MEDIA_IMAGES = "images";
    private static final String DATA_MEDIA_METADATATYPE = "metadataType";

    private static final String DATA_VOLUME = "volume";
    private static final String DATA_VOLUME_LEVEL = "level";
    private static final String DATA_VOLUME_MUTED = "muted";
    
    private static final String DATA_CURRENTTIME = "currentTime";

    private static final String DATA_CUSTOMDATA = "customData";

    private String mStatus = PLAYER_STATE_IDLE;

    private String mPlayerState = PLAYER_STATE_IDLE;

    private String mTitle;

    private String mSubtitle;

    private String mUrl;

    private JSONObject mMediaMetadata;

    private double mVideoVolume;

    private int mRequestId = 0;

    private int mRequestIdLoad = 0;

    private int mRequestIdPause = 0;

    private int mRequestIdPlay = 0;

    private int mRequestIdSetVolume = 0;

    private int mRequestIdSeek = 0;

    private int mRequestIdGetStatus = 0;

    private int mRequestIdStop = 0;
    
    private double mPreVolumeLevel = 0.5;

    private static final String BROADCAST_SENDER_ID = "*:*";

    /**
     * Flint receiver manager
     */
    private FlintReceiverManager mReceiverManager;

    /**
     * Message reporter.
     */
    private MessageReport mMessageReport;

    /**
     * Flint video element.
     */
    private final FlintVideo mFlintVideo;

    /**
     * Media Message bus
     */
    private ReceiverMessageBus mMessageBus;

    /**
     * Flint Media Player
     * 
     * @param manager
     * @param video
     */
    public FlintMediaPlayer(FlintReceiverManager manager, FlintVideo video) {
        mReceiverManager = manager;

        mFlintVideo = video;

        mMessageReport = new MessageReport();

        mMessageBus = new ReceiverMessageBus(MEDIA_NAMESPACE) {

            @Override
            public void onSenderConnected(String senderId) {
                // TODO Auto-generated method stub

                log.e("MediaPlayer received sender connected:" + senderId);
            }

            @Override
            public void onSenderDisconnected(String senderId) {
                // TODO Auto-generated method stub

                log.e("MediaPlayer received sender disconnected:"
                        + senderId);
            }

            @Override
            public void onPayloadMessage(String payload, String senderId) {
                // TODO Auto-generated method stub

                log.d("Received payload[" + payload + "]senderId["
                        + senderId + "]");

                // process messages on user part.
                boolean consumed = onMediaMessage(payload);
                if (consumed) {
                    log.w("Consumed this message![" + payload + "]");
                    return;
                }

                // process media messages.
                try {
                    JSONObject messageData = new JSONObject(payload);
                    mRequestId = messageData.getInt(DATA_REQUESTID);

                    String type = messageData.getString(DATA_TYPE);
                    if (type.endsWith(DATA_TYPE_LOAD)) {
                        mRequestIdLoad = mRequestId;

                        JSONObject mediaObj = messageData
                                .getJSONObject(DATA_MEDIA);
                        String contentId = mediaObj
                                .getString(DATA_MEDIA_CONTENTID);
                        String contentType = mediaObj
                                .getString(DATA_MEDIA_CONTENTTYPE);
                        JSONObject metaData = mediaObj
                                .getJSONObject(DATA_MEDIA_METADATA);
                        String title = metaData.optString(
                                DATA_MEDIA_METADATA_TITLE, "");
                        String subtitle = metaData.optString(
                                DATA_MEDIA_METADATA_SUBTITLE, "");

                        load(contentId, contentType, title, subtitle,
                                messageData);

                        return;
                    }

                    if (type.endsWith(DATA_TYPE_PAUSE)) {
                        mRequestIdPause = mRequestId;

                        pause();

                        return;
                    }

                    if (type.endsWith(DATA_TYPE_PLAY)) {
                        mRequestIdPlay = mRequestId;

                        play();

                        return;
                    }

                    if (type.endsWith(DATA_TYPE_SET_VOLUME)) {
                        mRequestIdSetVolume = mRequestId;

                        JSONObject volumeObj = messageData
                                .getJSONObject(DATA_VOLUME);
                        boolean muted = volumeObj.optBoolean(DATA_VOLUME_MUTED,false);
                        double level = volumeObj.optDouble(DATA_VOLUME_LEVEL, mPreVolumeLevel);
                        if (muted) {
                            changeVolume(0);
                        } else {
                            mPreVolumeLevel = level;
                            
                            changeVolume(level);
                        }

                        return;
                    }

                    if (type.endsWith(DATA_TYPE_SEEK)) {
                        mRequestIdSeek = mRequestId;

                        int currentTime = messageData.getInt(DATA_CURRENTTIME);

                        seek(currentTime * 1000); // convert from "s" to "ms".

                        return;
                    }

                    if (type.endsWith(DATA_TYPE_PING)) {
                        return;
                    }

                    if (type.endsWith(DATA_TYPE_GET_STATUS)) {
                        mRequestIdGetStatus = mRequestId;

                        log.e("Received GET_STATUS Request!");
                        mMessageReport.syncPlayerState("");

                        return;
                    }

                    if (type.endsWith(DATA_TYPE_STOP)) {
                        mRequestIdStop = mRequestId;

                        JSONObject customData = null;
                        try {
                            customData = messageData
                                    .getJSONObject(DATA_CUSTOMDATA);
                        } catch (Exception e) {
                            //e.printStackTrace();
                        }

                        stop(customData);

                        return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        mReceiverManager.setMessageBus(MEDIA_NAMESPACE,
                mMessageBus);

        init();
    }

    private void init() {
        mFlintVideo.addEventListener(FlintVideo.EMPTIED,
                new FlintVideo.Callback() {

                    @Override
                    public void process(String data) {
                        // TODO Auto-generated method stub

                        mMessageReport.idle(FlintVideo.IDLE_REASON_NONE);
                    }

                });

        mFlintVideo.addEventListener(FlintVideo.LOADEDMETADATA,
                new FlintVideo.Callback() {

                    @Override
                    public void process(String data) {
                        // TODO Auto-generated method stub

                        mStatus = "READY";

                        mMessageReport.loadmetadata();
                    }

                });

        mFlintVideo.addEventListener(FlintVideo.PLAY,
                new FlintVideo.Callback() {

                    @Override
                    public void process(String data) {
                        // TODO Auto-generated method stub

                        mMessageReport.playing();
                    }

                });

        mFlintVideo.addEventListener(FlintVideo.PLAYING,
                new FlintVideo.Callback() {

                    @Override
                    public void process(String data) {
                        // TODO Auto-generated method stub

                        mMessageReport.playing();
                    }

                });

        mFlintVideo.addEventListener(FlintVideo.WAITING,
                new FlintVideo.Callback() {

                    @Override
                    public void process(String data) {
                        // TODO Auto-generated method stub

                        mMessageReport.buffering();
                    }

                });

        mFlintVideo.addEventListener(FlintVideo.PAUSE,
                new FlintVideo.Callback() {

                    @Override
                    public void process(String data) {
                        // TODO Auto-generated method stub

                        mMessageReport.paused();
                    }

                });

        mFlintVideo.addEventListener(FlintVideo.ENDED,
                new FlintVideo.Callback() {

                    @Override
                    public void process(String data) {
                        // TODO Auto-generated method stub

                        mMessageReport.idle(FlintVideo.IDLE_REASON_FINISHED);
                    }

                });

        mFlintVideo.addEventListener(FlintVideo.VOLUMECHANGE,
                new FlintVideo.Callback() {

                    @Override
                    public void process(String data) {
                        // TODO Auto-generated method stub

                        mVideoVolume = mFlintVideo.getVolume();

                        mMessageReport
                                .syncPlayerState(MessageReport.SYNC_TYPE_VOLUMECHANGE);
                    }

                });

        mFlintVideo.addEventListener(FlintVideo.SEEKED,
                new FlintVideo.Callback() {

                    @Override
                    public void process(String data) {
                        // TODO Auto-generated method stub

                        mMessageReport
                                .syncPlayerState(MessageReport.SYNC_TYPE_SEEKED);
                    }

                });

        mFlintVideo.addEventListener(FlintVideo.CANPLAY,
                new FlintVideo.Callback() {

                    @Override
                    public void process(String data) {
                        // TODO Auto-generated method stub

                        mMessageReport
                                .syncPlayerState(MessageReport.SYNC_TYPE_OTHERS);
                    }

                });

        mFlintVideo.addEventListener(FlintVideo.ERROR,
                new FlintVideo.Callback() {

                    @Override
                    public void process(String data) {
                        // TODO Auto-generated method stub

                        mMessageReport.idle(FlintVideo.IDLE_REASON_ERROR);
                    }

                });

        mFlintVideo.addEventListener(FlintVideo.ABORT,
                new FlintVideo.Callback() {

                    @Override
                    public void process(String data) {
                        // TODO Auto-generated method stub

                        mMessageReport.idle(FlintVideo.IDLE_REASON_INTERRUPTED);
                    }

                });
    }

    /**
     * Process "LOAD" command
     * 
     * @param url
     * @param videoType
     * @param title
     * @param subtitle
     * @param mediaMetadata
     */
    public void load(String url, String videoType, String title,
            String subtitle, JSONObject mediaMetadata) {
        mMediaMetadata = mediaMetadata;

        log.e("mMediaMetadata: " + mMediaMetadata.toString());

        mTitle = title;

        mSubtitle = subtitle;

        mUrl = url;

        mFlintVideo.setUrl(mUrl);
        
        mFlintVideo.setTitle(mTitle);

        mFlintVideo.setAutoPlay(true);

        mFlintVideo.load();
    }

    /**
     * Process PAUSE
     */
    public void pause() {
        syncExecute(new FlintVideo.Callback() {

            @Override
            public void process(String data) {
                // TODO Auto-generated method stub

                mFlintVideo.pause();
            }

        });
    }

    /**
     * Process PLAY
     */
    public void play() {
        syncExecute(new FlintVideo.Callback() {

            @Override
            public void process(String data) {
                // TODO Auto-generated method stub

                mFlintVideo.play();
            }

        });
    }

    /**
     * Process SEEK
     */
    public void seek(final double time) {
        syncExecute(new FlintVideo.Callback() {

            @Override
            public void process(String data) {
                // TODO Auto-generated method stub

                mFlintVideo.seek(time);
            }

        });
    }

    /**
     * Process change volume
     */
    public void changeVolume(final double volume) {
        syncExecute(new FlintVideo.Callback() {

            @Override
            public void process(String data) {
                // TODO Auto-generated method stub

                mFlintVideo.setVolume(volume);
            }
        });

        // TODO
    }

    /**
     * Process STOP
     * 
     * @param custData
     */
    public void stop(final JSONObject custData) {
        syncExecute(new FlintVideo.Callback() {

            @Override
            public void process(String data) {
                // TODO Auto-generated method stub

                mFlintVideo.stop(custData);
            }
        });

        // TODO

        mMessageReport.idle(FlintVideo.IDLE_REASON_CANCELLED);
    }

    /**
     * Sync exec method.
     */
    private void syncExecute(final FlintVideo.Callback readyCallBack) {
        if (mStatus.equals(PLAYER_STATE_READY)) {
            readyCallBack.process("");
            return;
        }

        if (mStatus.equals(PLAYER_STATE_IDLE)) {
            return;
        }

        if (mStatus.equals(PLAYER_STATE_LOADDING)) {
            Timer timer = new Timer();
            TimerTask task = new TimerTask() {

                @Override
                public void run() {
                    // TODO Auto-generated method stub
                    syncExecute(readyCallBack);
                }

            };

            timer.schedule(task, 50);

            return;
        }
    }

    private class MessageReport {
        /**
         * Fired when volume changed
         */
        public static final String SYNC_TYPE_VOLUMECHANGE = "volumechange";

        /**
         * Fired when media seeked.
         */
        public static final String SYNC_TYPE_SEEKED = "seeked";

        /**
         * Fired when sync other media events
         */
        public static final String SYNC_TYPE_OTHERS = "others";

        /**
         * Process IDLE
         */
        public void idle(String reason) {
            mPlayerState = PLAYER_STATE_IDLE;

            String messageData = loadData(PLAYER_STATE_IDLE, reason, null, null);

            mMessageBus.send(messageData, BROADCAST_SENDER_ID);
        }

        /**
         * Process LOAD META DATA.
         */
        public void loadmetadata() {
            mPlayerState = PLAYER_STATE_PLAYING;

            String messageData = loadData(PLAYER_STATE_PLAYING, null,
                    mRequestIdLoad + "", mMediaMetadata);

            mMessageBus.send(messageData, BROADCAST_SENDER_ID);
        }

        /**
         * Process PLAYING
         */
        public void playing() {
            mPlayerState = PLAYER_STATE_PLAYING;

            String messageData = loadData(PLAYER_STATE_PLAYING, null,
                    mRequestIdPlay + "", null);

            mMessageBus.send(messageData, BROADCAST_SENDER_ID);
        }

        /**
         * Process PAUSED.
         */
        public void paused() {
            mPlayerState = PLAYER_STATE_PAUSED;

            String messageData = loadData(PLAYER_STATE_PAUSED, null,
                    mRequestIdPause + "", null);

            mMessageBus.send(messageData, BROADCAST_SENDER_ID);
        }

        /**
         * Process BUFFERING
         * 
         * @return
         */
        public void buffering() {
            mPlayerState = PLAYER_STATE_BUFFERING;

            String messageData = loadData(PLAYER_STATE_BUFFERING, null, null,
                    null);

            mMessageBus.send(messageData, BROADCAST_SENDER_ID);
        }

        /**
         * Process "SYNC PLAYER STATE"
         * 
         * @return
         */
        public void syncPlayerState(String type) {
            String messageData = null;

            if (type.equals(FlintVideo.SEEKED)) {
                messageData = loadData(mPlayerState, null, mRequestIdSeek + "",
                        mMediaMetadata);
            } else if (type.equals(FlintVideo.VOLUMECHANGE)) {
                messageData = loadData(mPlayerState, null, mRequestIdSetVolume
                        + "", mMediaMetadata);
            } else {
                messageData = loadData(mPlayerState, null, null, mMediaMetadata);
            }

            try {
                log.e("messageData:" + messageData + "]["
                        + (new JSONObject(messageData)).toString());

                mMessageBus.send(messageData, BROADCAST_SENDER_ID);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * Load data
         * 
         * @return
         */
        private String loadData(String playerState, String idleReason,
                String requestId, JSONObject metadata) {
            String data = "{\"type\":\"MEDIA_STATUS\"," + "\"status\":[" + "{"
                    + "\"mediaSessionId\":1," + "\"playbackRate\":"
                    + mFlintVideo.getPlaybackRate()
                    + ","
                    + "\"currentTime\":"
                    + mFlintVideo.getCurrentTime() / 1000 // convert to "s"?!
                    + ","
                    + "\"duration\":"
                    + mFlintVideo.getDuration() / 1000 // convert to "s"!
                    + ","
                    + "\"supportedMediaCommands\":15,"
                    + "\"volume\":{"
                    + "\"level\":"
                    + mFlintVideo.getVolume()
                    + ","
                    + "\"muted\":"
                    + mFlintVideo.isMuted()
                    + "}"
                    + (playerState != null ? "," + "\"playerState\":\""
                            + playerState + "\"" : "")
                    + (idleReason != null ? "," + "\"idleReason\":\""
                            + idleReason + "\"" : "")
                    + (metadata != null ? "," + getMediaData(metadata) : "")
                    + "}"
                    + "],"
                    + "\"requestId\":"
                    + (requestId != null ? requestId : "0") + "}";

            return data;
        }

        private String getMediaData(JSONObject mediaMetadata) {
            try {
                JSONObject mediObj = mediaMetadata.getJSONObject(DATA_MEDIA);
                String streamType = mediObj.getString(DATA_MEDIA_STREAMTYPE);
                String contentType = mediObj.getString(DATA_MEDIA_CONTENTTYPE);
                String contentId = mediObj.getString(DATA_MEDIA_CONTENTID);
                JSONObject metaData = mediObj
                        .getJSONObject(DATA_MEDIA_METADATA);
                String title = metaData
                        .optString(DATA_MEDIA_METADATA_TITLE, "");
                String subtitle = metaData.optString(
                        DATA_MEDIA_METADATA_SUBTITLE, "");
                JSONArray images = null;
                try {
                    images = metaData.getJSONArray(DATA_MEDIA_IMAGES);
                } catch (Exception e) {
                    images = new JSONArray();
                }

                String metadataType = metaData
                        .getString(DATA_MEDIA_METADATATYPE);

                String data = "\"media\":{" + "\"streamType\":\""
                        + streamType
                        + "\","
                        + "\"duration\":"
                        + mFlintVideo.getDuration() / 1000 // conver to "s"!
                        + "," + "\"contentType\":\"" + contentType + "\","
                        + "\"contentId\":\"" + contentId + "\","
                        + "\"metadata\":{" + "\"title\":\"" + title + "\","
                        + "\"subtitle\":\"" + subtitle + "\"," + "\"images\":"
                        + images.toString() + "," + "\"metadataType\":\""
                        + metadataType + "\"}}";
                return data;
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }
    }

    /**
     * Provide user a change to process media payload message.
     * 
     * @param payload
     * @return whether this message is consumed.
     */
    public boolean onMediaMessage(final String payload) {
        log.e("onMediaMessages:" + payload);

        return false;
    }
}

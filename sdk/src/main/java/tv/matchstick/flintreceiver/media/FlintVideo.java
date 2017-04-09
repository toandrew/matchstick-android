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

import java.util.HashMap;

import org.json.JSONObject;

/**
 * Flint Video element
 * 
 * Use this class to interact with Video related.
 * 
 * @author jim
 *
 */
public abstract class FlintVideo {

    // video events
    public static final String EMPTIED = "emptied";

    public static final String LOADEDMETADATA = "loadedmetadata";

    public static final String PLAY = "play";

    public static final String PLAYING = "playing";

    public static final String WAITING = "waiting";

    public static final String PAUSE = "pause";

    public static final String STOP = "stop";

    public static final String ENDED = "ended";

    public static final String VOLUMECHANGE = "volumechange";

    public static final String SEEKED = "seeked";

    public static final String CANPLAY = "canplay";

    public static final String ERROR = "error";

    public static final String ABORT = "abort";

    // IDLE REASONEs
    public static final String IDLE_REASON_NONE = "NONE";

    public static final String IDLE_REASON_FINISHED = "FINISHED";

    public static final String IDLE_REASON_ERROR = "ERROR";

    public static final String IDLE_REASON_INTERRUPTED = "INTERRUPTED";

    public static final String IDLE_REASON_CANCELLED = "CANCELLED";

    HashMap<String, Callback> mListeners;

    private double mDuration = 0;

    private double mPlaybackRate = 0;

    private String mUrl;
    
    private String mTitle;

    private boolean mAutoPlay = true;

    public FlintVideo() {
        mListeners = new HashMap<String, Callback>();
    }

    /**
     * Add media event listener
     * 
     * @param event
     * @param callback
     */
    public void addEventListener(String event, Callback callback) {
        mListeners.put(event, callback);
    }

    /**
     * Delete media event listener.
     * 
     * @param event
     * @param callback
     */
    public void removeEventListener(String event, Callback callback) {
        mListeners.remove(event);
    }

    /**
     * Set current volume
     * 
     * @param volume
     * @return
     */
    abstract public void setVolume(double volume);

    /**
     * Get current media volume
     * 
     * @return
     */
    abstract public double getVolume();

    /**
     * Get video's duration
     * 
     * @param duration
     *            the duration in milliseconds
     */
    public double getDuration() {
        return mDuration;
    }

    /**
     * Set video's duration
     * 
     * @param duration
     *            the duration in milliseconds
     */
    public void setDuration(double duration) {
        mDuration = duration;
    }

    /**
     * Set video's playback rate
     */
    public void setPlaybackRate(double rate) {
        mPlaybackRate = rate;
    }

    /**
     * Get video's playback rate.
     * 
     * @return
     */
    public double getPlaybackRate() {
        return mPlaybackRate;
    }

    /**
     * Set current video's play position
     * 
     * @param time
     *            the current time in milliseconds
     */
    abstract public void setCurrentTime(double time);

    /**
     * Get current Time
     * 
     * @return the current time in milliseconds
     */
    abstract public double getCurrentTime();

    /**
     * Whether video is muted
     * 
     * @return
     */
    abstract public boolean isMuted();

    /**
     * Set play url
     * 
     * @param url
     */
    public void setUrl(String url) {
        mUrl = url;
    }

    /**
     * Get play url
     * 
     * @return
     */
    public String getUrl() {
        return mUrl;
    }
    
    /**
     * Set play title
     * 
     * @param title
     */
    public void setTitle(String title) {
        mTitle = title;
    }

    /**
     * Get play title
     * 
     * @return
     */
    public String getTitle() {
        return mTitle;
    }

    /**
     * Ready to load video.
     */
    abstract public void load();

    /**
     * Set whether autoplay
     * 
     * @param autoplay
     */
    public void setAutoPlay(boolean autoplay) {
        mAutoPlay = autoplay;
    }

    /**
     * Whether autoplay is enabled.
     * 
     * @return
     */
    public boolean isAutoPlay() {
        return mAutoPlay;
    }

    /**
     * Pause video.
     */
    abstract public void pause();

    /**
     * Play video.
     */
    abstract public void play();

    /**
     * Seek video
     * 
     * @param time
     *            the seeked time in milliseconds
     */
    abstract public void seek(double time);

    /**
     * Stop video.
     * 
     * @param custData
     */
    abstract public void stop(JSONObject custData);

    /**
     * Callback functions
     *
     */
    static abstract class Callback {
        public abstract void process(String data);
    }

    /**
     * Notify Sender apps that something(media is loaded, playing, paused, etc)
     * is happened, those events is very important for Sender Apps.
     * 
     * @param type
     * @param data
     */
    public void notifyEvents(String type, String data) {
        Callback callback = mListeners.get(type);

        if (callback != null) {
            callback.process(data);
        }
    }
}

package nz.co.iswe.android.airplay.network.airplay;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;

import nz.co.iswe.android.airplay.AirPlayServer;
import nz.co.iswe.android.airplay.image.ImageActivity;
import nz.co.iswe.android.airplay.player.MediaModel;
import nz.co.iswe.android.airplay.video.VideoActivity;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.json.JSONObject;

import android.content.Intent;
import android.util.Log;

import com.dd.plist.BinaryPropertyListWriter;
import com.dd.plist.NSArray;
import com.dd.plist.NSDate;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSObject;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListParser;
import com.example.airplay.util.DlnaMediaModelFactory;
import com.example.airplay.util.Globals;

public class AirplayServerChannelHandler extends SimpleChannelHandler implements
        AirplayVideoInfoListener {
    private static final String TAG = "AirplayServerChannelHandler";

    private static final String AIRPLAY_REVERSE_REQUEST = "/reverse";
    private static final String AIRPLAY_SCRUB_REQUEST = "/scrub";
    private static final String AIRPLAY_VOLUME_REQUEST = "/volume";
    private static final String AIRPLAY_PLAY_REQUEST = "/play";
    private static final String AIRPLAY_RATE_REQUEST = "/rate";
    private static final String AIRPLAY_STOP_REQUEST = "/stop";
    private static final String AIRPLAY_PHOTO_REQUEST = "/photo";
    private static final String AIRPLAY_PLAYBACK_INFO_REQUEST = "/playback-info";
    private static final String AIRPLAY_SERVER_INFO_REQUEST = "/server-info";

    private static final String AIRPLAY_GET_PROPERTY_REQUEST = "/getProperty";
    private static final String AIRPLAY_GET_PROPERTY_REQUEST_PLAYBACK_ACCESS_LOG = "playbackAccessLog";
    private static final String AIRPLAY_GET_PROPERTY_REQUEST_PLAYBACK_ERROR_LOG = "playbackErrorLog";

    private static final String AIRPLAY_SET_PROPERTY_REQUEST = "/setProperty";

    private boolean debug = true;

    private static final String PLAY_CONTENT_LOCATION = "Content-Location";
    private static final String PLAY_START_POSITION = "Start-Position";
    private static final String PLAY_CONTENT_TYPE_TEXT = "text/parameters";
    private static final String PLAY_CONTENT_TYPE_BINARY = "application/x-apple-binary-plist";

    private static final String PLAY_INFO_DURATION = "duration";
    private static final String PLAY_INFO_POSITION = "position";
    private static final String PLAY_INFO_RATE = "rate";
    private static final String PLAY_INFO_READYTOPLAY = "readyToPlay";
    private static final String PLAY_INFO_LOADEDTIMERANGES = "loadedTimeRanges";
    private static final String PLAY_INFO_START = "start";
    private static final String PLAY_INFO_PLAYBACKBUFFEREMPTY = "playbackBufferEmpty";
    private static final String PLAY_INFO_PLAYBACKBUFFERFULL = "playbackBufferFull";
    private static final String PLAY_INFO_PLAYBACKLIKELYTOKEEPUP = "playbackLikelyToKeepUp";
    private static final String PLAY_INFO_SEEKABLETIMERANGES = "seekableTimeRanges";

    private static final String SERVER_INFO_DEVICEID = "deviceid";
    private static final String SERVER_INFO_FEATURES = "features";
    private static final String SERVER_INFO_MODEL = "model";
    private static final String SERVER_INFO_PROTOVERS = "protovers";
    private static final String SERVER_INFO_SRCVERS = "srcvers";

    private static final int RATE_PLAY = 1;
    private static final int RATE_PAUSE = 0;
    private static final int RATE_REWIND = -2;
    private static final int REATE_FASTFORWARD = 2;

    private float mDuration = 0;
    private float mPosition = 0;
    private int mRate = 0;
    private boolean mReadyToPlay = true;

    private static final String AIRPLAY_PHOTO_HEADER_ASSETKEY = "X-Apple-AssetKey";
    private static final String AIRPLAY_PHOTO_HEADER_ASSERTACTION = "X-Apple-AssetAction";
    private static final String AIRPLAY_PHOTO_HEADER_ASSERTACTION_DISPLAYCACHED = "displayCached";
    private static final String AIRPLAY_PHOTO_HEADER_ASSERTACTION_CACHEONLY = "cacheOnly";
    private static final String AIRPLAY_PHOTO_HEADER_TRANSITION = "X-Apple-Transition";
    private static final String AIRPLAY_PHOTO_HEADER_TRANSITION_DISSOLVE = "Dissolve";

    private static final int MAX_AIRPLAY_PHOTO_CACHED = 4;

    private HashMap<String, byte[]> mCachedPhoto = new HashMap<String, byte[]>();
    private List<String> mPhotoCachedKeys = new LinkedList<String>();

    private HashMap<String, Integer> mCachedSessionTypes = new HashMap<String, Integer>();

    private static final String X_Apple_Session_ID = "X-Apple-Session-ID";

    private static final int SESSION_TYPE_IMAGE = 1;
    private static final int SESSION_TYPE_MUSIC = 2;
    private static final int SESSION_TYPE_VIDEO = 2;

    private static final int MAX_CACHED_SESSION_TYPE = 10;

    @Override
    public void messageReceived(final ChannelHandlerContext ctx,
            final MessageEvent evt) throws Exception {

        Channel ch = evt.getChannel();
        Object msg = evt.getMessage();

        if (debug) {
            Log.e(TAG, "---------------");
            Log.e(TAG, "message: " + msg.getClass());
        }

        if (msg instanceof HttpRequest) {
            processHttpRequest(ch, (HttpRequest) msg);
        } else if (msg instanceof HttpResponse) {
            // TODO
            Log.e(TAG, "response message: " + msg.toString());
        }

        super.messageReceived(ctx, evt);
    }

    /**
     * Process AirPlay (video/photo) related request
     * 
     * @param channel
     * @param request
     */
    private void processHttpRequest(Channel channel, HttpRequest request) {
        String contentType = "";
        int contentLen = 0;

        boolean cachePhoto = false;
        boolean dispCachedPhoto = false;
        boolean isDissolve = false;

        String assetKey = null;

        String sessionId = null;

        List<Entry<String, String>> headers = request.getHeaders();
        if (true) {
            for (Map.Entry<String, String> i : headers) {
                Log.e(TAG, "header  " + i.getKey() + ":" + i.getValue());
                if (i.getKey().equals("Content-Type")) {
                    contentType = i.getValue();
                }

                if (i.getKey().equals("Content-Length")) {
                    contentLen = Integer.valueOf(i.getValue());
                }

                if (i.getKey().equals(AIRPLAY_PHOTO_HEADER_ASSERTACTION)) {
                    String action = i.getValue();
                    if (action
                            .equals(AIRPLAY_PHOTO_HEADER_ASSERTACTION_DISPLAYCACHED)) {
                        dispCachedPhoto = true;
                    } else if (action
                            .equals(AIRPLAY_PHOTO_HEADER_ASSERTACTION_CACHEONLY)) {
                        cachePhoto = true;
                    }
                }

                if (i.getKey().equals(AIRPLAY_PHOTO_HEADER_ASSETKEY)) {
                    assetKey = i.getValue();
                }

                if (i.getKey().equals(AIRPLAY_PHOTO_HEADER_TRANSITION)) {
                    String dissolveTransition = i.getValue();
                    if (dissolveTransition
                            .equals(AIRPLAY_PHOTO_HEADER_TRANSITION_DISSOLVE)) {
                        isDissolve = true;
                    }
                }

                if (i.getKey().equals(X_Apple_Session_ID)) {
                    sessionId = i.getValue();
                }
            }

            Log.e(TAG, "method[" + request.getMethod() + "]");
            Log.e(TAG, "uri[" + request.getUri() + "]");
        }

        String uri = request.getUri();

        if (AIRPLAY_REVERSE_REQUEST.equals(uri)) {
            Log.e(TAG, "do REVERSE!");

            doReverse(channel);

            return;
        }

        if (uri.startsWith(AIRPLAY_SCRUB_REQUEST)) {
            Log.e(TAG, "do SCRUB![" + request.getMethod() + "]["
                    + request.getMethod().toString().equals("POST") + "]");

            if (request.getMethod().toString().equals("POST")) {
                float position = 0;
                int index = uri.indexOf("=");
                if (index >= 0) {
                    String pos = uri.substring(index + 1, uri.length());
                    position = Float.valueOf(pos);
                }

                doPostScrub(channel, position);
            } else {
                doGetScrub(channel);
            }

            return;
        }

        if (uri.equals(AIRPLAY_VOLUME_REQUEST)) {
            Log.e(TAG, "do VOLUME!");

            double value = 1;

            int index = uri.indexOf("=");
            if (index >= 0) {
                String v = uri.substring(index + 1, uri.length());

                value = Double.valueOf(v);
            }

            doVolume(channel, value);

            return;
        }

        if (AIRPLAY_PLAY_REQUEST.equals(uri)) {
            Log.e(TAG, "do PLAY!");

            if (sessionId != null) {
                mCachedSessionTypes.put(sessionId, SESSION_TYPE_VIDEO);
            }

            doPlay(channel, request.getContent(), contentType, contentLen, sessionId);

            return;
        }

        if (uri.startsWith(AIRPLAY_RATE_REQUEST)) {
            Log.e(TAG, "do RATE!");
            int value = 1;

            int index = uri.indexOf("=");
            if (index >= 0) {
                String v = uri.substring(index + 1, uri.length());

                value = Integer.parseInt(new java.text.DecimalFormat("0")
                        .format(Double.valueOf(v)));
            }

            doRate(channel, value);

            return;
        }

        if (AIRPLAY_STOP_REQUEST.equals(uri)) {
            Log.e(TAG, "do STOP!");

            doStop(channel, sessionId);

            return;
        }

        if (AIRPLAY_PHOTO_REQUEST.equals(uri)) {
            Log.e(TAG, "do PHOTO!");

            doPhoto(channel, request.getContent(), contentType, contentLen,
                    dispCachedPhoto, cachePhoto, assetKey, isDissolve);

            return;
        }

        if (AIRPLAY_PLAYBACK_INFO_REQUEST.equals(uri)) {
            Log.e(TAG, "do PLAYBACK_INFO!");

            doPlaybackInfo(channel, sessionId);

            return;
        }

        if (AIRPLAY_SERVER_INFO_REQUEST.equals(uri)) {
            Log.e(TAG, "do SERVER_INFO!");

            doServerInfo(channel);

            return;
        }

        // if (uri.startsWith(AIRPLAY_GET_PROPERTY_REQUEST)) {
        // boolean accessLog = uri
        // .contains(AIRPLAY_GET_PROPERTY_REQUEST_PLAYBACK_ACCESS_LOG);
        // boolean errorLog = uri
        // .contains(AIRPLAY_GET_PROPERTY_REQUEST_PLAYBACK_ERROR_LOG);
        //
        // doGetProperty(channel, accessLog, errorLog, contentType);
        // return;
        // }
        //
        // if (uri.startsWith(AIRPLAY_SET_PROPERTY_REQUEST)) {
        //
        // doSetProperty(channel);
        // return;
        // }

        Log.e(TAG, "unknown: request:" + uri);

        // sent default response?
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = dateFormat.format(calendar.getTime());

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.NOT_IMPLEMENTED);
        response.setHeader("Date", date);
        response.setHeader("Content-Length", response.getContent()
                .writerIndex());

        channel.write(response);
    }

    private void doReverse(Channel channel) {
        
        final AirPlayServer airPlayServer = AirPlayServer.getIstance(Globals
                .getContext());
        airPlayServer.setReverseInfo(channel.getRemoteAddress().toString());
        
        Log.e(TAG, "reverseInfo [" + airPlayServer.getReverseInfo() + "][" + AirplayServerChannelHandler.this + "]");
        
        // sent response
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = dateFormat.format(calendar.getTime());

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.SWITCHING_PROTOCOLS);
        response.setHeader("Date", date);
        response.setHeader("Upgrade", "PTTH/1.0");
        response.setHeader("Connection", "Upgrade");

        channel.write(response);
    }

    private void doPostScrub(Channel channel, float position) {
        Log.e(TAG, "doPostScrub!position[" + position + "]");

        final AirPlayServer airPlayServer = AirPlayServer.getIstance(Globals
                .getContext());
        AirplayMediaController controller = airPlayServer
                .getAirplayVideoController();

        if (controller != null) {
            controller.onSeekCommand(position * 1000); // change to ms?
        }

        // sent response
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = dateFormat.format(calendar.getTime());

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK);
        response.setHeader("Date", date);
        response.setHeader("Content-Length", response.getContent()
                .writerIndex());

        channel.write(response);
    }

    private void doGetScrub(Channel channel) {
        Log.e(TAG, "doGetScrub!");

        String info = "duration: " + getDuration() + '\n' + "position: "
                + getPosition() + "\n";

        // sent response
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = dateFormat.format(calendar.getTime());

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK);
        response.setHeader("Date", date);

        response.setContent(ChannelBuffers.copiedBuffer(info.toString()
                .getBytes()));

        response.setHeader("Content-Length", response.getContent()
                .writerIndex());

        channel.write(response);
    }

    private void doPlay(Channel channel, ChannelBuffer channelBuffer,
            String contentType, int contentLen, String x_session_id) {

        final AirPlayServer airPlayServer = AirPlayServer.getIstance(Globals
                .getContext());

        Iterator<Channel> iter = airPlayServer.getChannelGroup().iterator();
        while (iter.hasNext()) {
            Channel c = iter.next();
            Log.e(TAG,
                    "c[" + c + "]channel[" + channel + "]c.isConnected["
                            + c.isConnected() + "][" + c.getRemoteAddress() + "][" + airPlayServer.getReverseInfo() + "][" + AirplayServerChannelHandler.this + "]");
            if (c != channel && c.isConnected() && airPlayServer.getReverseInfo() != null && airPlayServer.getReverseInfo().equals(c.getRemoteAddress().toString())) {
                Log.e(TAG, "post event to channel[" + c + "]");
                sendVideoStatusEvents(c, "playing", x_session_id);
                break;
            }
        }

        byte[] data = new byte[contentLen];
        System.arraycopy(channelBuffer.array(), 0, data, 0, contentLen);

        String content = new String(data);

        Log.e(TAG, "doPlay![" + new String(content) + "][" + contentType + "]["
                + contentLen + "][" + data.length + "]");

        // process request
        if (contentType.equals(PLAY_CONTENT_TYPE_BINARY)) {
            String url = null;
            String rate = null;
            String pos = null;
            try {
                NSDictionary rootDict = (NSDictionary) PropertyListParser
                        .parse(data);

                for (int i = 0; i < rootDict.allKeys().length; i++) {
                    Log.e(TAG, "root key[" + rootDict.allKeys()[i] + "]["
                            + rootDict.values().toArray()[i] + "]");
                }
                Log.e(TAG,
                        "rootDict[" + rootDict.allKeys() + "]["
                                + rootDict.values() + "]");

                try {
                    url = rootDict.objectForKey("Content-Location").toString();
                } catch (Exception e) {
                    String path = null;
                    String host = null;
                    for (int i = 0; i < rootDict.allKeys().length; i++) {
                        Log.e(TAG, "root key[" + rootDict.allKeys()[i] + "]["
                                + rootDict.values().toArray()[i] + "]");
                        if (rootDict.allKeys()[i].equals("path")) {
                            path = rootDict.values().toArray()[i].toString();
                        }

                        if (rootDict.allKeys()[i].equals("host")) {
                            host = rootDict.values().toArray()[i].toString();
                        }
                    }

                    if (host != null && path != null) {
                        url = "http://" + host + path;
                    }
                }

                pos = "0f";
                NSObject p = rootDict.objectForKey("Start-Position");
                if (p != null) {
                    pos = p.toString();
                }

                rate = rootDict.objectForKey("rate").toString();
            } catch (Exception e) {
                e.printStackTrace();
            }
            Log.e(TAG, "url[" + url + "]pos[" + pos + "]rate[" + rate + "]");

            JSONObject playInfo = new JSONObject();
            try {
                playInfo.put(PLAY_CONTENT_LOCATION, url);
                playInfo.put(PLAY_START_POSITION, pos);
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (url == null) {
                // error
                HttpResponse response = new DefaultHttpResponse(
                        HttpVersion.HTTP_1_1,
                        HttpResponseStatus.EXPECTATION_FAILED);
                channel.write(response);
                channel.close().awaitUninterruptibly();
                return;
            }

            Log.e(TAG, "playInfo[" + playInfo.toString() + "]");

            // ready to start video activity!
            try {
                startPlayVideo(playInfo.getString(PLAY_CONTENT_LOCATION),
                        playInfo.getString(PLAY_START_POSITION));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            int index = content.indexOf(PLAY_CONTENT_LOCATION);
            int s_index = content.indexOf(PLAY_START_POSITION);

            String url = content.substring(
                    index + PLAY_CONTENT_LOCATION.length() + 1, s_index - 1);
            String position = content.substring(
                    s_index + PLAY_START_POSITION.length() + 1,
                    content.length() - 1);

            startPlayVideo(url.trim(), position.trim());
        }

        // sent response
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = dateFormat.format(calendar.getTime());

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK);
        response.setHeader("Date", date);
        response.setHeader("Content-Length", response.getContent()
                .writerIndex());

        channel.write(response);
    }

    private void doVolume(Channel channel, double volume) {
        final AirPlayServer airPlayServer = AirPlayServer.getIstance(Globals
                .getContext());
        AirplayMediaController controller = airPlayServer
                .getAirplayVideoController();

        if (controller != null) {
            controller.onChangeVolumeCommand(volume);
        }

        // sent response
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = dateFormat.format(calendar.getTime());

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK);
        response.setHeader("Date", date);
        response.setHeader("Content-Length", response.getContent()
                .writerIndex());

        channel.write(response);
    }

    private void doRate(Channel channel, int value) {
        Log.e(TAG, "doRate![" + value + "]");

        final AirPlayServer airPlayServer = AirPlayServer.getIstance(Globals
                .getContext());
        AirplayMediaController controller = airPlayServer
                .getAirplayVideoController();

        switch (value) {
        case RATE_PAUSE:
            if (controller != null) {
                controller.onPauseCommand();
            }
            break;
        case RATE_PLAY:
            if (controller != null) {
                controller.onPlayCommand();
            }
            break;
        case RATE_REWIND:
            if (controller != null) {
                controller.onRewindCommand();
            }
            break;
        case REATE_FASTFORWARD:
            if (controller != null) {
                controller.onFastForwardCommand();
            }
            break;
        }

        // sent response
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = dateFormat.format(calendar.getTime());

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK);
        response.setHeader("Date", date);
        response.setHeader("Content-Length", response.getContent()
                .writerIndex());

        channel.write(response);
    }

    private void doStop(Channel channel, String sessionId) {
        boolean shouldStopVideo = false;

        if (sessionId != null) {
            Integer sessionType = mCachedSessionTypes.get(sessionId);
            if (sessionType != null
                    && sessionType.intValue() == SESSION_TYPE_VIDEO) {
                shouldStopVideo = true;
            }
        }
        final AirPlayServer airPlayServer = AirPlayServer.getIstance(Globals
                .getContext());
        AirplayMediaController controller = airPlayServer
                .getAirplayVideoController();

        if (controller != null && shouldStopVideo) {
            controller.onStopCommand();

            mCachedSessionTypes.remove(sessionId);

            //
            // Intent intent = new Intent();
            // intent.setAction("fling.action.stop_receiver");
            // if (Globals.getContext() != null) {
            // Globals.getContext().sendBroadcast(intent);
            // }
        } else if (shouldStopVideo) {
            Log.e(TAG, "controller is empty???!");

            Intent intent = new Intent();
            intent.setAction("fling.action.stop_receiver");
            if (Globals.getContext() != null) {
                Globals.getContext().sendBroadcast(intent);
            }
        } else {
            Log.e(TAG, "ignore stop video command?!");
        }

        AirplayImageController imageController = airPlayServer
                .getAirplayImageController();

        if (imageController != null) {
            imageController.onStopImage();
        }

        // clear photo cache
        mCachedPhoto.clear();
        mPhotoCachedKeys.clear();

        // sent response
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = dateFormat.format(calendar.getTime());

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK);
        response.setHeader("Date", date);
        response.setHeader("Content-Length", response.getContent()
                .writerIndex());

        channel.write(response);
    }

    private void doPhoto(Channel channel, ChannelBuffer channelBuffer,
            String contentType, int contentLen, boolean dispCachedPhoto,
            boolean cachePhoto, String assetKey, boolean isDissolve) {
        Log.e(TAG, "doPhoto[" + new String(channelBuffer.array()) + "]");

        if (channelBuffer.array().length > 0) {
            byte[] data = new byte[contentLen];
            System.arraycopy(channelBuffer.array(), 0, data, 0, contentLen);

            if (assetKey != null) {
                while ((mCachedPhoto.size() > MAX_AIRPLAY_PHOTO_CACHED)) {
                    Log.e(TAG,
                            "doPhoto: clear photo size[" + mCachedPhoto.size()
                                    + "]");

                    String key = mPhotoCachedKeys
                            .get(mPhotoCachedKeys.size() - 1);
                    mCachedPhoto.remove(key); // remove last photo cache?
                    mPhotoCachedKeys.remove(mPhotoCachedKeys.size() - 1);
                }

                mPhotoCachedKeys.add(0, assetKey);
                mCachedPhoto.put(assetKey, data);
            }

            if (!cachePhoto) {
                startShowImage(mCachedPhoto.get(assetKey));
            }
        } else if (dispCachedPhoto) {
            startShowImage(mCachedPhoto.get(assetKey));
        }

        // sent response
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = dateFormat.format(calendar.getTime());

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK);
        response.setHeader("Date", date);
        response.setHeader("Content-Length", response.getContent()
                .writerIndex());

        channel.write(response);
    }

    private void doPlaybackInfo(Channel channel, String x_session_id) {
        Log.e(TAG, "doPlaybackInfo!rate[" + getRate() + "]duration["
                + getDuration() + "]position[" + getPosition() + "]ready["
                + isReadyToPlay() + "]");

        PListBuilder builder = new PListBuilder();

        if (isReadyToPlay()) {
            builder.putReal(PLAY_INFO_DURATION, getDuration());
            builder.putReal(PLAY_INFO_POSITION, getPosition());
            builder.putReal(PLAY_INFO_RATE, getRate());
            builder.putBoolean(PLAY_INFO_READYTOPLAY, isReadyToPlay());

            JSONObject timeReange = new JSONObject();
            try {
                timeReange.put(PLAY_INFO_DURATION, getDuration());
                timeReange.put(PLAY_INFO_START, getPosition());
            } catch (Exception e) {
            }

            builder.putArrayReals(PLAY_INFO_LOADEDTIMERANGES, timeReange);

            boolean playBackBufferEmpty = true;
            boolean playBackBufferFull = false;
            boolean playbackLikelyToKeepUp = true;

            builder.putBoolean(PLAY_INFO_PLAYBACKBUFFEREMPTY,
                    playBackBufferEmpty);
            builder.putBoolean(PLAY_INFO_PLAYBACKBUFFERFULL, playBackBufferFull);
            builder.putBoolean(PLAY_INFO_PLAYBACKLIKELYTOKEEPUP,
                    playbackLikelyToKeepUp);

            JSONObject seekableTimeRanges = new JSONObject();
            try {
                seekableTimeRanges.put(PLAY_INFO_DURATION, getDuration());
                seekableTimeRanges.put(PLAY_INFO_START, getPosition());
            } catch (Exception e) {
            }

            builder.putArrayReals(PLAY_INFO_SEEKABLETIMERANGES,
                    seekableTimeRanges);
        } else {
            builder.putBoolean(PLAY_INFO_READYTOPLAY, isReadyToPlay());
        }

        // sent response
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = dateFormat.format(calendar.getTime());

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK);
        response.setHeader("Date", date);
        response.setContent(ChannelBuffers.copiedBuffer(builder.toString()
                .getBytes()));
        response.setHeader("Content-Length", response.getContent()
                .writerIndex());

        channel.write(response);
    }

    private void doServerInfo(Channel channel) {
        Log.e(TAG, "doServerInfo!");

        PListBuilder builder = new PListBuilder();

        builder.putString(SERVER_INFO_DEVICEID, AirplayUtil.getInstance()
                .getDeviceId());
        // builder.putString(SERVER_INFO_FEATURES, AirplayUtil.getInstance()
        // .getSupportedFeatures());
        builder.putInteger(SERVER_INFO_FEATURES, 0x2FF7);
        builder.putString(SERVER_INFO_MODEL, AirplayUtil.getInstance()
                .getModelName());
        builder.putString(SERVER_INFO_PROTOVERS, AirplayUtil.getInstance()
                .getProtoVers());
        builder.putString(SERVER_INFO_SRCVERS, AirplayUtil.getInstance()
                .getSrcVers());

        // sent response
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = dateFormat.format(calendar.getTime());

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK);
        response.setHeader("Date", date);
        response.setContent(ChannelBuffers.copiedBuffer(builder.toString()
                .getBytes()));
        response.setHeader("Content-Length", response.getContent()
                .writerIndex());

        channel.write(response);
    }

    private void doGetProperty(Channel channel, boolean accessLog,
            boolean errorLog, String contentType) {
        Log.e(TAG, "doGetProperty![" + accessLog + "][" + errorLog + "]");

        if (accessLog) {
            NSDictionary root = new NSDictionary();
            NSNumber errorCode = new NSNumber("0");
            root.put("errorCode", errorCode);
            // Log.e(TAG, "root[" + root.toASCIIPropertyList() + "][" +
            // root.toString() + "][" + root.toXMLPropertyList() + "]");

            NSArray value = new NSArray(1);

            NSDictionary info = new NSDictionary();
            // NSNumber bytes = new NSNumber("0"); // file length
            // info.put("bytes", bytes);

            NSNumber c_duration_downloaded = new NSNumber("0");
            info.put("c-duration-downloaded", c_duration_downloaded);

            NSNumber c_duration_watched = new NSNumber("1");
            info.put("c-duration-watched", c_duration_watched);

            NSNumber c_frames_dropped = new NSNumber("0");
            info.put("c-frames-dropped", c_frames_dropped);

            NSNumber c_observed_bitrate = new NSNumber("14598047.302367469");
            info.put("c-observed-bitrate", c_observed_bitrate);

            NSNumber c_overdue = new NSNumber("0");
            info.put("c-overdue", c_overdue);

            NSNumber c_stalls = new NSNumber("0");
            info.put("c-stalls", c_stalls);

            NSNumber c_start_time = new NSNumber("0");
            info.put("c-start-time", c_start_time);

            NSNumber c_startup_time = new NSNumber("0");
            info.put("c-startup-time", c_startup_time);

            NSString cs_guid = new NSString(
                    "B475F105-78FD-4200-96BC-148BAB6DAC11");
            info.put("cs-guid", cs_guid);

            NSDate c_date = null;
            Date da = new Date(System.currentTimeMillis());
            try {
                c_date = new NSDate(da);
            } catch (Exception e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
            info.put("date", c_date);

            // NSString s_ip = new NSString("213.152.6.89");
            // info.put("s-ip", s_ip);
            //
            // NSNumber s_ip_changes = new NSNumber("0");
            // info.put("s-ip-changes", s_ip_changes);

            NSNumber sc_count = new NSNumber("0");
            info.put("sc-count", sc_count);

            NSString uri = new NSString(mCurrentUrl);
            info.put("uri", uri);

            value.setValue(0, info);

            root.put("value", value);

            Log.e(TAG, "[" + root.toXMLPropertyList() + "]");

            byte[] bin = null;
            try {
                bin = BinaryPropertyListWriter.writeToArray(root);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // sent response
            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat dateFormat = new SimpleDateFormat(
                    "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            String date = dateFormat.format(calendar.getTime());

            HttpResponse response = new DefaultHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.setHeader("Date", date);
            response.setContent(ChannelBuffers.copiedBuffer(bin));
            response.setHeader("Content-Type",
                    "application/x-apple-binary-plist");
            response.setHeader("Content-Length", response.getContent()
                    .writerIndex());

            channel.write(response);
        } else {
            // sent response
            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat dateFormat = new SimpleDateFormat(
                    "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            String date = dateFormat.format(calendar.getTime());

            HttpResponse response = new DefaultHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_IMPLEMENTED);
            response.setHeader("Date", date);
            response.setHeader("Content-Type",
                    "application/x-apple-binary-plist");
            response.setHeader("Content-Length", response.getContent()
                    .writerIndex());

            channel.write(response);
        }
    }

    private void doSetProperty(Channel channel) {

        NSDictionary root = new NSDictionary();
        NSNumber errorCode = new NSNumber("0");
        root.put("errorCode", errorCode);

        byte[] bin = null;
        try {
            bin = BinaryPropertyListWriter.writeToArray(root);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // sent response
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = dateFormat.format(calendar.getTime());

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.NOT_IMPLEMENTED);
        response.setHeader("Date", date);
        response.setContent(ChannelBuffers.copiedBuffer(bin));
        response.setHeader("Content-Type", "application/x-apple-binary-plist");
        response.setHeader("Content-Length", response.getContent()
                .writerIndex());

        channel.write(response);
    }

    private void sendReverseResponse(String event) {

    }

    private float getDuration() {
        final AirPlayServer airPlayServer = AirPlayServer.getIstance(Globals
                .getContext());
        AirplayMediaController controller = airPlayServer
                .getAirplayVideoController();

        if (controller != null) {
            mDuration = controller.getDuration();
        } else {
            mDuration = 0;
        }

        Log.e(TAG, "getDuration: " + mDuration);

        return mDuration;
    }

    private float getPosition() {
        final AirPlayServer airPlayServer = AirPlayServer.getIstance(Globals
                .getContext());
        AirplayMediaController controller = airPlayServer
                .getAirplayVideoController();

        if (controller != null) {
            mPosition = controller.getPosition();
        }

        Log.e(TAG, "getPosition: " + mPosition);

        return mPosition;
    }

    private int getRate() {
        final AirPlayServer airPlayServer = AirPlayServer.getIstance(Globals
                .getContext());
        AirplayMediaController controller = airPlayServer
                .getAirplayVideoController();

        if (controller != null) {
            boolean isPlaying = controller.isPlaying();
            if (isPlaying) {
                mRate = RATE_PLAY;
            } else {
                mRate = RATE_PAUSE;
            }
        }

        return mRate;
    }

    private boolean isReadyToPlay() {
        return mReadyToPlay;
    }

    private String mCurrentUrl = null;

    private void startPlayVideo(String url, String position) {
        Log.d(TAG, "startPlayVideo[" + Globals.getContext() + "]");

        // stop image or audio?
        final AirPlayServer airPlayServer = AirPlayServer.getIstance(Globals
                .getContext());
        AirplayImageController controller = airPlayServer
                .getAirplayImageController();

        if (controller != null) {
            controller.onStopImage();
        }

        AirplayMediaController audioController = airPlayServer
                .getAirplayMusicController();
        if (audioController != null) {
            audioController.onStopCommand();
        }

        MediaModel mediaInfo = new MediaModel();
        mediaInfo.setUrl(url);

        mCurrentUrl = url;

        Intent intent = new Intent();
        intent.setClass(Globals.getContext(), VideoActivity.class);
        DlnaMediaModelFactory.pushMediaModelToIntent(intent, mediaInfo);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Globals.getContext().startActivity(intent);
    }

    private void startShowImage(byte[] data) {
        if (data == null) {
            Log.d(TAG, "Sorry!startShowImage: data is null");
            return;
        }
        Log.d(TAG, "startShowImage[" + data.length + "]");

        // stop image or audio?
        final AirPlayServer airPlayServer = AirPlayServer.getIstance(Globals
                .getContext());

        if (airPlayServer != null) {
            airPlayServer.setCurrentImageData(data);
        }

        // MediaModel mediaInfo = new MediaModel();
        // mediaInfo.setUrl(url);

        Intent intent = new Intent();
        intent.setClass(Globals.getContext(), ImageActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Globals.getContext().startActivity(intent);
    }

    @Override
    public void onSeek(int time) {
        // TODO Auto-generated method stub
        Log.e(TAG, "onSeek![" + time + "]");

        mPosition = time;
    }

    @Override
    public void onPlay() {
        // TODO Auto-generated method stub

        mRate = RATE_PLAY;

        Log.e(TAG, "after onPlay![" + mRate + "]this[" + this + "]");
    }

    @Override
    public void onPause() {
        // TODO Auto-generated method stub

        mRate = RATE_PAUSE;

        Log.e(TAG, "after onPause![" + mRate + "]this[" + this + "]");
    }

    @Override
    public void onStop() {
        // TODO Auto-generated method stub
        Log.e(TAG, "onStop!");

        mRate = RATE_PAUSE;
    }

    @Override
    public void onPrepare() {
        // TODO Auto-generated method stub
        Log.e(TAG, "onPrepare!");

        mRate = RATE_PAUSE;
    }

    @Override
    public void onDuration(int duration) {
        // TODO Auto-generated method stub

        Log.e(TAG, "onDuration![" + duration + "]");

        mDuration = duration;
    }

    private void sendVideoStatusEvents(Channel channel, String event,
            String x_session) {

        // ClientBootstrap client = new ClientBootstrap(
        // new NioClientSocketChannelFactory(
        // Executors.newCachedThreadPool(),
        // Executors.newCachedThreadPool()));
        //
        // client.setPipelineFactory(new ClientPipelineFactory());
        // InetSocketAddress address = (InetSocketAddress) (mchannel
        // .getRemoteAddress());
        // Channel channel = client
        // .connect(
        // new InetSocketAddress(address.getAddress(), address
        // .getPort())).awaitUninterruptibly()
        // .getChannel();

        PListBuilder builder = new PListBuilder();

        builder.putString("category", "video");
        builder.putString("state", "loading");

        Log.e(TAG, "sendVideoStatusEvents[" + builder.toString() + "]");
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.POST, "/event");
        request.setHeader(HttpHeaders.Names.CONTENT_TYPE,
                "text/x-apple-plist+xml");
        request.setContent(ChannelBuffers.copiedBuffer(builder.toString()
                .getBytes()));
        request.setHeader("Content-Length", request.getContent().writerIndex());
        request.setHeader("X-Apple-Session-ID", x_session);

        channel.write(request);
    }
}

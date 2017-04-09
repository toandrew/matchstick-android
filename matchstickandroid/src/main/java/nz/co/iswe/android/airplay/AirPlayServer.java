package nz.co.iswe.android.airplay;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import nz.co.iswe.android.airplay.network.NetworkUtils;
import nz.co.iswe.android.airplay.network.airplay.AirplayImageController;
import nz.co.iswe.android.airplay.network.airplay.AirplayMediaController;
import nz.co.iswe.android.airplay.network.airplay.AirplayPipelineFactory;
import nz.co.iswe.android.airplay.network.airplay.AirplayUtil;
import nz.co.iswe.android.airplay.network.airplay.AirplayVideoInfoListener;
import nz.co.iswe.android.airplay.network.raop.RaopRtspPipelineFactory;

import org.apache.http.conn.util.InetAddressUtils;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.example.airplay.util.Constants;
import com.example.airplay.util.Globals;
import com.example.airplay.util.LogManager;

/**
 * Android AirPlay Server Implementation
 * 
 * @author Rafael Almeida
 *
 */
public class AirPlayServer implements Runnable {

    // private static final Logger LOG =
    // Logger.getLogger(AirPlayServer.class.getName());

    /**
     * The DACP service type
     */
    // static final String AIR_REMOTE_CONTROL_SERVICE_TYPE =
    // "_dacp._tcp.local.";
    // static final Map<String, String> AIR_DACP_SERVICE_PROPERTIES = map(
    // "txtvers", "1",
    // "Ver", "131075",
    // "DbId", "63B5E5C0C201542E",
    // "OSsi", "0x1F5"
    // );

    /**
     * The AirTunes/RAOP service type
     */
    static final String AIR_TUNES_SERVICE_TYPE = "_raop._tcp.local.";

    /**
     * The AirTunes/RAOP M-DNS service properties (TXT record)
     */
//    static final Map<String, String> AIRTUNES_SERVICE_PROPERTIES = map(
//            "txtvers", "1", "tp", "UDP", "ch", "2", "ss", "16", "sr", "44100",
//            "pw", "false", "sm", "false", "sv", "false", "ek", "1", "et",
//            "0,1", "cn", "0,1", "vn", "3", "md", "0,1,2", "am", "MatchStick","ft","0x39f7");

//     static final Map<String, String> AIRTUNES_SERVICE_PROPERTIES =
//     map("sf","0x4", "am", "AppleTV2,1", "vn","65537", "vv","1","rhd","4.1.3",
//     "vs","150.33","md","0,1,2","ft","0x3933","da","true", "txtvers","1",
//     "vn","3","pw","false", "sr", "44100", "ss", "16", "ch","2","cn","0,1",
//     "et","0,1", "ek","1","sv","false", "sm","false","tp", "UDP");
    
//    static final Map<String, String> AIRTUNES_SERVICE_PROPERTIES =
//            map("vs", "130.14", "am", "AppleTV1,1", "md", "0,1,2", "da", "true", "vn", "3", "pw", "false", "sr", "44100", "ss", "16", "sm", "false", "tp", "UDP", "sv", "false", "et", "0,1", "ek", "1", "ch", "2", "cn", "0,1", "txtvers", "1");
//    
//    static final Map<String, String> AIRTUNES_SERVICE_PROPERTIES =
//            map("txtvers", "1", "md", "0,1,2", "vs", "130.14", "da", "true", "vn", "3", "pw", "false", "sr", "44100", "ss", "16", "sm", "false", "tp", "UDP", "sv", "false", "et", "0,1", "ek", "1", "ch", "2", "cn", "0,1", "ft", "0x22f7", "am", "AppleTV2,1");
//    
    
  static final Map<String, String> AIRTUNES_SERVICE_PROPERTIES =
  map(
          "txtvers", "1",
          "am", "AppleTV3,1",
          "ch", "2",
          "cn", "0,1,2,3",
          "da", "true",
          "et", "0,1,3",
          "md", "0,1,2",
          "pw", "false",
          "rhd", "AppleTV3,1",
          "rmodel", "AppleTV3,1",
          "sf", "0x4",
          "sr", "44100",
          "ss", "16",
          "sv", "false",
          "tp", "UDP",
          "vn", "65537",
          "vs", "150.33",
          "vv", "1"
);

   
//  static final Map<String, String> AIRTUNES_SERVICE_PROPERTIES =
//  map("sf","0x4", "am", "AppleTV2,1", "vn","65537", "vv","1","rhd","4.1.3",
//  "vs","150.33","md","0,1,2","ft","0x3933","da","true", "txtvers","1",
//  "vn","3","pw","false", "sr", "44100", "ss", "16", "ch","2","cn","0,1",
//  "et","0,1", "ek","1","sv","false", "sm","false","tp", "UDP");

    // static final Map<String, String> AIRTUNES_SERVICE_PROPERTIES =
    // map("sf","0x4", "am", "AppleTV3,1", "vn","3", "vv","1","rhd","4.1.3",
    // "vs","130.14","md","0,1,2","ft","0x3","da","true", "txtvers","1",
    // "vn","3","pw","false", "sr", "44100", "ss", "16", "ch","2","cn","0,1",
    // "et","0,1", "ek","1","sv","false", "sm","false","tp", "UDP");
    //
    Context context;
    private static AirPlayServer instance = null;

    public static AirPlayServer getIstance(Context context) {
        if (instance == null) {
            instance = new AirPlayServer(context);
        }
        return instance;
    }

    /**
     * Global executor service. Used e.g. to initialize the various netty
     * channel factories
     */
    protected ExecutorService executorService;

    /**
     * Channel execution handler. Spreads channel message handling over multiple
     * threads
     */
    protected ExecutionHandler channelExecutionHandler;

    /**
     * All open RTSP channels. Used to close all open challens during shutdown.
     */
    protected ChannelGroup channelGroup;

    /**
     * JmDNS instances (one per IP address). Used to unregister the mDNS
     * services during shutdown.
     */
    protected List<JmDNS> jmDNSInstances;

    /**
     * The AirTunes/RAOP RTSP port
     */
    private int rtspPort = 5010;//6000;//5010; // default value

    // airplay http port
    private int airplayPort = 46670;//7000;//46670; // default value

    /**
     * The Airplay service type
     */
    static final String AIRPLAY_SERVICE_TYPE = "_airplay._tcp.local.";

    /**
     * The Airplay M-DNS service properties (TXT record)
     */
    // static final Map<String, String> AIRPLAY_SERVICE_PROPERTIES = map(
    // "features", "0x31f7", "deviceid", "00:50:43:BB:08:0B", "model",
    // "AndroidTV2,1", "srcvers", "130.14");

    // 0x2a7f->0x39f7

    // private static final String AIRPLAY_NAME =
    // "MatchStick-Android-xxxxxx-AirPlay";

    private AirplayVideoInfoListener mAirplayVideoInfoListener;

    private AirplayMediaController mAirplayVideoController;

    private AirplayImageController mAirplayImageController;

    private AirplayMediaController mAirplayMusicController;

    private byte[] mImageData;

    private String mDeviceName = "MS-AIRPLAY";

    private AirPlayServer(Context context) {

        // Set the application-wide context global, if not already set
        Globals.setContext(context.getApplicationContext());

        this.context = context;

        // create executor service
        executorService = Executors.newCachedThreadPool();

        // create channel execution handler
        channelExecutionHandler = new ExecutionHandler(
                new OrderedMemoryAwareThreadPoolExecutor(4, 0, 0));

        // channel group
        channelGroup = new DefaultChannelGroup();

        // list of mDNS services
        jmDNSInstances = new java.util.LinkedList<JmDNS>();
    }

    public int getRtspPort() {
        return rtspPort;
    }

    public void setRtspPort(int rtspPort) {
        this.rtspPort = rtspPort;
    }

    public int getAirplayPort() {
        return airplayPort;
    }

    public void setAirplayPort(int airplayPort) {
        this.airplayPort = airplayPort;
    }

    public void run() {
        LogManager.e("start to service loading.... ");
        long start = System.currentTimeMillis();
        startService();
        LogManager.e("start to service success.... ="
                + (System.currentTimeMillis() - start));
    }

    @SuppressLint("NewApi")
    private void startService() {
        /* Make sure AirPlay Server shuts down gracefully */
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                onShutdown();
            }
        }));

        LogManager.i("VM Shutdown Hook added sucessfully!");

        /* Create AirTunes RTSP server */
        final ServerBootstrap airTunesRtspBootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(executorService,
                        executorService));
        airTunesRtspBootstrap.setPipelineFactory(new RaopRtspPipelineFactory(
                context));

        airTunesRtspBootstrap.setOption("reuseAddress", true);
        airTunesRtspBootstrap.setOption("child.tcpNoDelay", true);
        airTunesRtspBootstrap.setOption("child.keepAlive", true);

        try {
            // Caused by: java.net.BindException: bind failed: EADDRINUSE
            // (Address already in use)
            channelGroup.add(airTunesRtspBootstrap.bind(new InetSocketAddress(
                    Inet4Address.getByName("0.0.0.0"), getRtspPort())));
        } catch (UnknownHostException e) {
            LogManager.e("Failed to bind RTSP Bootstrap on port: "
                    + getRtspPort() + "||" + Log.getStackTraceString(e));
        }

        LogManager.i("Launched RTSP service on port " + getRtspPort());

        // create airplay server for video/photo
        final ServerBootstrap airplayBootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(executorService,
                        executorService));
        airplayBootstrap
                .setPipelineFactory(new AirplayPipelineFactory(context));

        airplayBootstrap.setOption("reuseAddress", true);
        airplayBootstrap.setOption("child.tcpNoDelay", true);
        airplayBootstrap.setOption("child.keepAlive", true);

        try {
            // Caused by: java.net.BindException: bind failed: EADDRINUSE
            // (Address already in use)
            channelGroup.add(airplayBootstrap.bind(new InetSocketAddress(
                    Inet4Address.getByName("0.0.0.0"), getAirplayPort())));
        } catch (UnknownHostException e) {
            LogManager.e("Failed to bind Airplay Bootstrap on port: "
                    + getAirplayPort() + "||" + Log.getStackTraceString(e));
        }

        LogManager.i("Launched Airplay service on port " + getAirplayPort());

        // get Network details
        NetworkUtils networkUtils = NetworkUtils.getInstance();

        // String hostName = networkUtils.getHostUtils();
        String hostName = Constants.IKEY_AIRPLAY_SERVER_NMAE;
        LogManager.i("servName : " + hostName);

        String hardwareAddressString = networkUtils.getHardwareAddressString();

        try {
            /* Create mDNS responders. */
            synchronized (jmDNSInstances) {
                ConnectivityManager manager = (ConnectivityManager) context
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo info = manager.getActiveNetworkInfo();
                if (info == null) {
                    LogManager
                            .e("Sorry, no active network, ignore add mdns devices!");
                    return;
                }
                if (info.getType() == ConnectivityManager.TYPE_ETHERNET
                        || info.getType() == ConnectivityManager.TYPE_WIFI) {
                    for (final NetworkInterface iface : Collections
                            .list(NetworkInterface.getNetworkInterfaces())) {
                        if (iface.isLoopback() || iface.isPointToPoint()
                                || !iface.isUp()) {
                            continue;
                        }

                        ArrayList<InetAddress> inets = Collections.list(iface
                                .getInetAddresses());

                        for (final InetAddress addr : inets) {
                            if (!(addr instanceof Inet4Address)
                                    && !(addr instanceof Inet6Address)) {
                                continue;
                            }
                            if (!(addr instanceof Inet4Address)) {
                                continue;
                            }

                            LogManager.i("binded ip:"
                                    + addr.getHostAddress().toString());

                            try {
                                /* Create mDNS responder for address */
                                final JmDNS jmDNS = JmDNS.create(addr, hostName
                                        + "-jmdns");
                                jmDNSInstances.add(jmDNS);

                                /* Publish RAOP service */
                                final ServiceInfo airTunesServiceInfo = ServiceInfo
                                        .create(AIR_TUNES_SERVICE_TYPE,
                                                // hardwareAddressString + "@" +
                                                // hostName + " (" +
                                                // iface.getName()
                                                // + ")",
                                                hardwareAddressString + "@"
                                                        + getDeviceName(),
                                                getRtspPort(), 0 /* weight */,
                                                0 /* priority */,
                                                AIRTUNES_SERVICE_PROPERTIES);
                                jmDNS.registerService(airTunesServiceInfo);
                                LogManager.w("Registered AirTunes service '"
                                        + airTunesServiceInfo.getName()
                                        + "' on " + addr);

                                Map<String, String> AIRPLAY_SERVICE_PROPERTIES = new HashMap<String, String>();
                                AIRPLAY_SERVICE_PROPERTIES.put(
                                        AirplayUtil.SERVER_INFO_FEATURES,
                                        String.valueOf(AirplayUtil
                                                .getInstance()
                                                .getSupportedFeatures()));
                                AIRPLAY_SERVICE_PROPERTIES
                                        .put(AirplayUtil.SERVER_INFO_DEVICEID,
                                                AirplayUtil.getInstance()
                                                        .getDeviceId());
                                AIRPLAY_SERVICE_PROPERTIES.put(
                                        AirplayUtil.SERVER_INFO_MODEL,
                                        AirplayUtil.getInstance()
                                                .getModelName());
                                AIRPLAY_SERVICE_PROPERTIES.put(
                                        AirplayUtil.SERVER_INFO_SRCVERS,
                                        AirplayUtil.getInstance().getSrcVers());

                                AIRPLAY_SERVICE_PROPERTIES.put(
                                        "vv",
                                        "1");
                                AIRPLAY_SERVICE_PROPERTIES.put(
                                        "pw",
                                        "0");
                                AIRPLAY_SERVICE_PROPERTIES.put(
                                        "rhd",
                                        "AppleTV3,1");   
                                
                                /* Publish AIRPLAY service */
                                final ServiceInfo airplayServiceInfo = ServiceInfo
                                        .create(AIRPLAY_SERVICE_TYPE,
                                                getDeviceName(),
                                                getAirplayPort(),
                                                0 /* weight */,
                                                0 /* priority */,
                                                AIRPLAY_SERVICE_PROPERTIES);
                                jmDNS.registerService(airplayServiceInfo);
                                LogManager.w("Registered Airplay service '"
                                        + airplayServiceInfo.getName()
                                        + "' on " + addr);
                                
                            } catch (final Throwable e) {
                                LogManager.e("Failed to publish service on "
                                        + addr + "||"
                                        + Log.getStackTraceString(e));
                            }
                        }
                    }
                }
            }
        } catch (SocketException e) {
            LogManager.e("Failed register mDNS services" + "||"
                    + Log.getStackTraceString(e));
        }
    }

    public InetAddress getInetAddress() {
        try {
            Enumeration<NetworkInterface> networks;
            Enumeration<InetAddress> inets;
            NetworkInterface network;
            InetAddress inetAddress;

            for (networks = NetworkInterface.getNetworkInterfaces(); networks
                    .hasMoreElements();) {
                network = networks.nextElement();
                for (inets = network.getInetAddresses(); inets
                        .hasMoreElements();) {
                    inetAddress = inets.nextElement();
                    if (!inetAddress.isLoopbackAddress()
                            && InetAddressUtils.isIPv4Address(inetAddress
                                    .getHostAddress())) {

                        return inetAddress;
                    }
                }

            }
        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    // When the app is shutdown
    protected void onShutdown() {
        /* Close channels */
        LogManager.e("BEGIN onShutdown!");
        final ChannelGroupFuture allChannelsClosed = channelGroup.close();

        /* Stop all mDNS responders */
        synchronized (jmDNSInstances) {
            for (final JmDNS jmDNS : jmDNSInstances) {
                try {
                    jmDNS.unregisterAllServices();
                    LogManager.i("Unregistered all services on "
                            + jmDNS.getInterface());
                } catch (final IOException e) {
                    LogManager
                            .e("Level.WARNING Failed to unregister some services "
                                    + Log.getStackTraceString(e));

                }
            }
        }

        /* Wait for all channels to finish closing */
        allChannelsClosed.awaitUninterruptibly();

        /* Stop the ExecutorService */
        executorService.shutdown();

        /* Release the OrderedMemoryAwareThreadPoolExecutor */
        channelExecutionHandler.releaseExternalResources();
        
        LogManager.e("END onShutdown!");
    }

    /**
     * Map factory. Creates a Map from a list of keys and values
     * 
     * @param keys_values
     *            key1, value1, key2, value2, ...
     * @return a map mapping key1 to value1, key2 to value2, ...
     */
    private static Map<String, String> map(final String... keys_values) {
        assert keys_values.length % 2 == 0;
        final Map<String, String> map = new java.util.HashMap<String, String>(
                keys_values.length / 2);
        for (int i = 0; i < keys_values.length; i += 2)
            map.put(keys_values[i], keys_values[i + 1]);
        return Collections.unmodifiableMap(map);
    }

    public ChannelHandler getChannelExecutionHandler() {
        return channelExecutionHandler;
    }

    public ChannelGroup getChannelGroup() {
        return channelGroup;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setAirplayVideoInfoListener(AirplayVideoInfoListener listener) {
        mAirplayVideoInfoListener = listener;
    }

    public AirplayVideoInfoListener getAirplayVideoInfoListener() {
        return mAirplayVideoInfoListener;
    }

    public void setAirplayVideoController(AirplayMediaController listener) {
        mAirplayVideoController = listener;
    }

    public AirplayMediaController getAirplayVideoController() {
        return mAirplayVideoController;
    }

    public void setAirplayImageController(AirplayImageController listener) {
        mAirplayImageController = listener;
    }

    public AirplayImageController getAirplayImageController() {
        return mAirplayImageController;
    }

    public byte[] getCurrentImageData() {
        return mImageData;
    }

    public void setCurrentImageData(byte[] data) {
        if (data == null) {
            mImageData = null;
            return;
        }

        if (mImageData != null) {
            mImageData = null;
        }

        mImageData = new byte[data.length];
        System.arraycopy(data, 0, mImageData, 0, data.length);
    }

    public void setAirplayMusicController(AirplayMediaController listener) {
        mAirplayMusicController = listener;
    }

    public AirplayMediaController getAirplayMusicController() {
        return mAirplayMusicController;
    }

    public void setDeviceName(String deviceName) {
        mDeviceName = deviceName;
    }

    public String getDeviceName() {
        return mDeviceName;
    }

    public void stopService() {
        onShutdown();
    }

    private String mReverseInfo = null;
    
    public void setReverseInfo(String info) {
        mReverseInfo = info;
    }
    
    public String getReverseInfo() {
        return mReverseInfo;
    }
}

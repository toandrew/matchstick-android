package nz.co.iswe.android.airplay.network.airplay;

import nz.co.iswe.android.airplay.network.NetworkUtils;

public class AirplayUtil {
    public static final String SERVER_INFO_DEVICEID = "deviceid";
    public static final String SERVER_INFO_FEATURES = "features";
    public static final String SERVER_INFO_MODEL = "model";
    public static final String SERVER_INFO_PROTOVERS = "protovers";
    public static final String SERVER_INFO_SRCVERS = "srcvers";
    
    private String mDeviceId = null;
    private String mSupportedFeatures = "0x100029ff";//"0x22f7";//"0x100029ff";//0x3937;//0x3;//0x39f7;//0x3933;// 0x39f7;
    private String mModelName = "AppleTV3,1";
    private String mProtoVers = "1.0";
    private String mSrcVers = "150.33";//"130.14";

    private static AirplayUtil sAirplayUtil;

    public static final AirplayUtil getInstance() {
        if (sAirplayUtil == null) {
            sAirplayUtil = new AirplayUtil();
        }

        return sAirplayUtil;
    }

    public String getDeviceId() {
        if (mDeviceId == null) {
            NetworkUtils networkUtils = NetworkUtils.getInstance();
            String deviceId = networkUtils.getHardwareAddressString();
            if (deviceId.length() == 12) {
                mDeviceId = deviceId.substring(0,2) + ":" + deviceId.substring(2,4) + ":" + deviceId.substring(4,6) + ":"
                         + deviceId.substring(6,8) + ":" + deviceId.substring(8,10) + ":" + deviceId.substring(10,12);
            }
        }

        return mDeviceId;
    }

    public String getSupportedFeatures() {
        return mSupportedFeatures;
    }

    public void setModelName(String name) {
        mModelName = name;
    }

    public String getModelName() {
         //return mModelName;
        
        return "AppleTV3,1";
    }

    public String getProtoVers() {
        return mProtoVers;
    }

    public String getSrcVers() {
        return mSrcVers;
    }
}

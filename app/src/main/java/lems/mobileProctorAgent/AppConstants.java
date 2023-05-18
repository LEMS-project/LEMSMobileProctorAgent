package lems.mobileProctorAgent;

import android.Manifest;

public interface AppConstants {
    int REQUEST_CODE_PERMISSIONS = 10;
    String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA, Manifest.permission.INTERNET};
    int REQUEST_CODE_BT_PERMISSIONS = 11;
    String[] BT_PERMISSIONS = new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN};

    int PICTURE_INTERVAL_MS = 1000;
    int EXPECTED_PICTURE_WIDTH = 416;
    int EXPECTED_PICTURE_HEIGHT = 416;

    String BT_GATT_SERVICE = "19B10000-E8F2-537E-4F6C-D104768A1214";

    boolean useWebSocketTestInfo = true;
    String testWebSocketEndpointDebug = "http://192.168.2.24:5555/mobile"; // "http://10.0.2.2:5000/mobile";
    String testWebSocketJWT = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6IlJcdTAwZTltaSIsImV4cCI6MTY0NDM5ODUzMX0.vv39ZmcBE-uWxCySYvpZmyFsaeNl5MvLcKdV5ETFWAE";


}
package lems.mobileProctorAgent;

import android.Manifest;

public interface AppConstants {
    int REQUEST_CODE_PERMISSIONS = 10;
    String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA, Manifest.permission.INTERNET};

    int PICTURE_INTERVAL_MS = 1000;
    int EXPECTED_PICTURE_WIDTH = 416;
    int EXPECTED_PICTURE_HEIGHT = 416;

    boolean useWebSocketTestInfo = false;
    String testWebSocketEndpointDebug = "http://10.0.2.2:5000/mobile";
    String testWebSocketJWT = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6IlJcdTAwZTltaSIsImV4cCI6MTY0NDM5ODUzMX0.vv39ZmcBE-uWxCySYvpZmyFsaeNl5MvLcKdV5ETFWAE";


}
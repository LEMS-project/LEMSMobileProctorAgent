package lems.mobileProctorAgent;

import android.Manifest;

public interface AppConstants {
    int REQUEST_CODE_PERMISSIONS = 10;
    String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA, Manifest.permission.INTERNET};

    int EXPECTED_PICTURE_WIDTH = 800;
    int EXPECTED_PICTURE_HEIGHT = 1066;

    boolean useWebSocketTestInfo = false;
    String testWebSocketEndpointDebug = "http://10.0.2.2:5000/mobile";
    String testWebSocketJWT = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6IlJcdTAwZTltaSIsImV4cCI6MTY0NDM5ODUzMX0.vv39ZmcBE-uWxCySYvpZmyFsaeNl5MvLcKdV5ETFWAE";


}
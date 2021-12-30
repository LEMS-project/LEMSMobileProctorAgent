package lems.mobileProctorAgent;

import android.Manifest;

public interface AppConstants {
    int REQUEST_CODE_PERMISSIONS = 10;
    String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA, Manifest.permission.INTERNET};

    boolean useWebSocketTestInfo = false;
    String testWebSocketEndpointDebug = "http://10.0.2.2:5000/mobile";
    String testWebSocketJWT = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6InRpdGkiLCJleHAiOjE2NDA4MzU3NjF9.MpvOXXNrdwjp7N4YGsTz5u0pKosy3_mNW_rcR7_vNkk";
}

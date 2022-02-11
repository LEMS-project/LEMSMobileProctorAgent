package lems.mobileProctorAgent;

import android.app.Application;
import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class LEMSMobileProcotorAgentApplication extends Application {
    private final static String LOG_TAG = LEMSMobileProcotorAgentApplication.class.getName();
    private final ScheduledExecutorService executorService;
    private final WebSocketManager wsMgr;
    private final CameraManager camMgr;

    public LEMSMobileProcotorAgentApplication() {
        super();
        this.executorService = Executors.newScheduledThreadPool(2);
        this.wsMgr = new WebSocketManager();
        this.camMgr = new CameraManager(this.executorService, (pictureSnapshot) -> {
            this.wsMgr.sendPictureSnapshot(pictureSnapshot);
        }, AppConstants.PICTURE_INTERVAL_MS, AppConstants.EXPECTED_PICTURE_WIDTH, AppConstants.EXPECTED_PICTURE_HEIGHT);
    }

    public ScheduledExecutorService getExecutorService() {
        return this.executorService;
    }

    public WebSocketManager getWebSocketManager() {
        return this.wsMgr;
    }

    public CameraManager getCameraManager() {
        return this.camMgr;
    }

    public void closeEverything() {
        Log.i(LOG_TAG, "CLOSE Websocket AND Camera Manager");
        try {
            this.wsMgr.close();
        } catch (Exception ex) {
            Log.i(LOG_TAG, "Error while closing websocket manager: " + ex.getMessage());
        }
        try {
            this.camMgr.close();
        } catch (Exception ex) {
            Log.i(LOG_TAG, "Error while closing camera manager: " + ex.getMessage());
        }
    }

    public void closeAndQuit() {
        this.closeEverything();
        Log.i(LOG_TAG, "QUIT Application");
        System.exit(0);
    }

}

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
        });
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

    @Override
    public void onTerminate() {
        Log.i(LOG_TAG, "Terminating app");
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
        super.onTerminate();
        System.exit(0);
    }
}

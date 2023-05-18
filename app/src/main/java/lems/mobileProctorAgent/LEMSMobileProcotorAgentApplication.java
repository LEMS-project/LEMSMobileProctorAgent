package lems.mobileProctorAgent;

import android.app.Application;
import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import lems.mobileProctorAgent.bluetooth.BluetoothManager;
import lems.mobileProctorAgent.camera.CameraManager;
import lems.mobileProctorAgent.websocket.WebsocketManager;

public class LEMSMobileProcotorAgentApplication extends Application {
    private final static String LOG_TAG = LEMSMobileProcotorAgentApplication.class.getName();
    private final ScheduledExecutorService executorService;
    private final WebsocketManager wsMgr;
    private final CameraManager camMgr;
    private final BluetoothManager bluetoothManager;

    public LEMSMobileProcotorAgentApplication() {
        super();
        this.executorService = Executors.newScheduledThreadPool(3);
        this.wsMgr = new WebsocketManager();
        this.camMgr = new CameraManager(this.executorService, (pictureSnapshot) -> {
            this.wsMgr.sendPictureSnapshot(pictureSnapshot);
        }, AppConstants.PICTURE_INTERVAL_MS, AppConstants.EXPECTED_PICTURE_WIDTH, AppConstants.EXPECTED_PICTURE_HEIGHT);
        this.bluetoothManager = new BluetoothManager(this.executorService);
        this.bluetoothManager.setWebsocketManager(this.wsMgr);
    }

    public ScheduledExecutorService getExecutorService() {
        return this.executorService;
    }

    public WebsocketManager getWebSocketManager() {
        return this.wsMgr;
    }

    public CameraManager getCameraManager() {
        return this.camMgr;
    }

    public BluetoothManager getBluetoothCtrlMgr() {
        return this.bluetoothManager;
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
        try {
            this.bluetoothManager.close();
        } catch (Exception ex) {
            Log.i(LOG_TAG, "Error while closing bluetooth controller manager: " + ex.getMessage());
        }
    }

    public void closeAndQuit() {
        this.closeEverything();
        Log.i(LOG_TAG, "QUIT Application");
        System.exit(0);
    }

}

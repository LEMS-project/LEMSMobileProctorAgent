package lems.mobileProctorAgent;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Locale;

import lems.mobileProctorAgent.bluetooth.BTLEControllerContract;
import lems.mobileProctorAgent.bluetooth.BluetoothManagerListener;
import lems.mobileProctorAgent.model.ControlOrder;
import lems.mobileProctorAgent.model.PictureSnapshot;
import lems.mobileProctorAgent.model.WebSocketEventTypes;
import lems.mobileProctorAgent.qrcodeReader.ReadQrCodeContract;
import lems.mobileProctorAgent.websocket.DeviceControlListener;
import lems.mobileProctorAgent.websocket.WebsocketListener;

public class MainActivity extends AppCompatActivity implements BluetoothManagerListener, WebsocketListener {
    private final static String LOG_TAG = MainActivity.class.getName();

    private boolean cameraAllowed;
    private boolean onError;
    private String debugMessages;
    private MainActivity.WSDataSentInfo dataSentInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.dataSentInfo = new MainActivity.WSDataSentInfo();
        // Init main components
        this.findViewById(R.id.qrCodeControl).setOnClickListener((v) -> {
            try {
                this.qrCodeReaderlauncher.launch(null);
            } catch (ActivityNotFoundException ex) {
                Log.e(LOG_TAG, "Unable to start reading QR code: " + ex.getMessage());
            }
        });
        this.findViewById(R.id.connectControl).setOnClickListener((v) -> {
            this.startWatching();
        });
        this.findViewById(R.id.btSelectControl).setOnClickListener((v) -> {
            try {
                this.btControllerSelectionLauncher.launch(null);
            } catch (ActivityNotFoundException ex) {
                Log.e(LOG_TAG, "Unable to start selecting BT controller: " + ex.getMessage());
            }
        });
        // Attempt to retrieve socket info from intent
        Intent intent = this.getIntent();
        Uri data = intent.getData();
        try {
            this.setSocketInfoFromUri(data);
            Log.d(LOG_TAG, "Launch Data found.");
        } catch (IllegalArgumentException ex) {
            Log.i(LOG_TAG, ex.getMessage());
        }
        // Check permission if not set
        if (!this.cameraAllowed) {
            this.checkPermissions();
        }
        // Render from state
        this.renderFromState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Set itself as a listener of websocket and device control listener
        LEMSMobileProcotorAgentApplication app = (LEMSMobileProcotorAgentApplication) getApplication();
        app.getWebSocketManager().addWebsocketListener(this);
        app.getBluetoothCtrlMgr().addManagerListener(this);
        // Check permission if not set
        if (!this.cameraAllowed) {
            this.checkPermissions();
        }
        // Render from state
        this.renderFromState();
    }



    @Override
    protected void onStop() {
        super.onStop();
        //LEMSMobileProcotorAgentApplication app = (LEMSMobileProcotorAgentApplication) getApplication();
        //app.closeAndQuit();
    }

    private void renderFromState() {
        //From the state compute different UI
        //UI components: icon, txtMainInfo (content), txtDebug (content and visibility), button enable
        final LEMSMobileProcotorAgentApplication app = (LEMSMobileProcotorAgentApplication) getApplication();
        this.runOnUiThread(() -> {
            // Updated UI components
            final ImageView iconEntrypoint = (ImageView) this.findViewById(R.id.iconControl);
            final TextView txtMainInfo = (TextView) this.findViewById(R.id.infoControl);
            final TextView txtBtDevice = (TextView) this.findViewById(R.id.btDeviceControl);
            final TextView txtDebugInfo = (TextView) this.findViewById(R.id.debugInfoControl);
            final Button btnSelectBTController = (Button) this.findViewById(R.id.btSelectControl);
            final Button btnTakeQrCode = (Button) this.findViewById(R.id.qrCodeControl);
            final Button btnConnect = (Button) this.findViewById(R.id.connectControl);

            // Service states
            final boolean controlled = app.getBluetoothCtrlMgr().isControlled();
            final boolean websocketConnected = app.getWebSocketManager().isOpened();
            final boolean websocketConnecting = app.getWebSocketManager().isConnecting();

            // Icon and text info
            int icon = R.drawable.ic_starting;
            String mainInfo = this.getString(R.string.ic_starting_desc);
            if (this.onError) {
                icon = R.drawable.ic_error;
                mainInfo = this.getString(R.string.error_connection);
            } else if (!this.cameraAllowed) {
                icon = R.drawable.ic_error;
                mainInfo = this.getString(R.string.error_permission_required);
            }  else if (websocketConnecting) {
                icon = R.drawable.ic_connecting;
                mainInfo = this.getString(R.string.ic_connecting_desc) + app.getWebSocketManager().getEndpoint();
            } else if (websocketConnected) {
                icon = R.drawable.ic_connecting;
                mainInfo = this.getString(R.string.ic_connected_desc) + app.getWebSocketManager().getEndpoint();
            } else if (controlled) {
                icon = R.drawable.ic_control;
                mainInfo = this.getString(R.string.ic_controlled_desc);
            } else if (app.getCameraManager().isOpened()) {
                icon = R.drawable.ic_taking_picture;
                mainInfo = this.getString(R.string.ic_taking_picture_desc);
            }
            iconEntrypoint.setImageDrawable(AppCompatResources.getDrawable(this.getApplicationContext(), icon));
            iconEntrypoint.setContentDescription(mainInfo);
            txtMainInfo.setText(mainInfo);

            // Bt device control info
            if (app.getBluetoothCtrlMgr().isOpened()) {
                txtBtDevice.setText(this.getString(R.string.btinfo_prefix, app.getBluetoothCtrlMgr().getBluetoothDeviceName()));
                txtBtDevice.setVisibility(TextView.VISIBLE);
            } else {
                txtBtDevice.setVisibility(TextView.INVISIBLE);
            }

            // Buttons enabling
            btnSelectBTController.setVisibility(!app.getCameraManager().isOpened() && !controlled ? Button.VISIBLE : Button.GONE);
            btnTakeQrCode.setVisibility(!websocketConnected && !websocketConnecting ? Button.VISIBLE : Button.GONE);
            btnConnect.setVisibility(websocketConnected && !app.getCameraManager().isOpened() && !controlled ? Button.VISIBLE : Button.GONE);

            // Debug Text
            if (this.debugMessages == null || this.debugMessages.isEmpty()) {
                txtDebugInfo.setText("");
                txtDebugInfo.setVisibility(TextView.INVISIBLE);
            } else {
                txtDebugInfo.setText(this.debugMessages);
                txtDebugInfo.setVisibility(TextView.INVISIBLE);
            }

            // Screen off prevention : wen cameraManager opened or controlled
            if (app.getCameraManager().isOpened() || controlled) {
                this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }

    private void startWatching() {
        this.dataSentInfo = new MainActivity.WSDataSentInfo();
        LEMSMobileProcotorAgentApplication app = (LEMSMobileProcotorAgentApplication) getApplication();
        // Precondition: websocket is connected, permission ok
        if (!this.cameraAllowed || !app.getWebSocketManager().isOpened()) {
            Log.i(LOG_TAG, "Cannot start watching, either camera not allowed or ws not connected");
        }
        app.getCameraManager().setContext(this);
        try {
            this.onError = false;
            app.getCameraManager().open();
            this.setDebugMessages();
        } catch (Exception ex) {
            this.onError = true;
            Log.e(LOG_TAG, "Error taking picture: " + ex.getMessage());
            this.setDebugMessages("Error starting taking picture: " + ex.getMessage());
        }
        this.renderFromState();
    }

    private void stopWatching() {
        LEMSMobileProcotorAgentApplication app = (LEMSMobileProcotorAgentApplication) getApplication();
        try {
            app.getCameraManager().close();
            this.setDebugMessages();
        } catch (Exception ex) {
            this.onError = true;
            Log.e(LOG_TAG, "Error Starting controlling: " + ex.getMessage() + " (" + ex.getClass().getName() + ")");
            this.setDebugMessages("Error starting taking picture: " + ex.getMessage());
        }
        this.renderFromState();
    }

    private void setDebugMessages(Object... messages) {
        this.debugMessages = null;
        final StringBuilder strBuilder = new StringBuilder();
        for (Object message : messages) {
            if (message != null) {
                strBuilder.append(message.toString()).append(System.lineSeparator());
            }
        }
        this.debugMessages = strBuilder.toString().trim();
    }

    private final ActivityResultLauncher<Void> qrCodeReaderlauncher = registerForActivityResult(new ReadQrCodeContract(), (uri) -> {
        Log.i(LOG_TAG, "1st activity : received uri! : " + uri);
        if (uri != null) {
            this.setSocketInfoFromUri(uri);
            this.renderFromState();
        }
    });

    private final ActivityResultLauncher<Void> btControllerSelectionLauncher = registerForActivityResult(new BTLEControllerContract(), (controllerAddrMac) -> {
        Log.i(LOG_TAG, "Received Controller Mac : " + controllerAddrMac);
        if (controllerAddrMac != null) {
            this.renderFromState();
            // Init bluetooth management
            LEMSMobileProcotorAgentApplication app = (LEMSMobileProcotorAgentApplication) getApplication();
            app.getBluetoothCtrlMgr().open(this, controllerAddrMac);
        }
    });

    private void setSocketInfoFromUri(Uri uri) {
        if (uri == null) {
            throw new IllegalArgumentException("Missing uri");
        }
        String urlEncodedEndpoint = uri.getQueryParameter("endpoint");
        final String websocketJWT = uri.getQueryParameter("jwt");
        final String websocketPath = uri.getQueryParameter("path");
        if (urlEncodedEndpoint == null || websocketJWT == null) {
            throw new IllegalArgumentException("Missing launch data endpoint or jwt");
        }
        final String websocketEndoint = Uri.decode(urlEncodedEndpoint);
        // Init websocket connection
        if (websocketEndoint != null && websocketJWT != null) {
            LEMSMobileProcotorAgentApplication app = (LEMSMobileProcotorAgentApplication) getApplication();
            app.getWebSocketManager().open(websocketEndoint, websocketJWT, websocketPath);
        }
    }

    private void checkPermissions() {
        if (!this.allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, AppConstants.REQUIRED_PERMISSIONS, AppConstants.REQUEST_CODE_PERMISSIONS);
        }
    }

    private boolean allPermissionsGranted() {
        for (String p : AppConstants.REQUIRED_PERMISSIONS) {
            Log.i(LOG_TAG, "PERM: " + p);
            if (ContextCompat.checkSelfPermission(getBaseContext(), p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        Log.i(LOG_TAG, "All permission allowed.");
        this.cameraAllowed = true;
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == AppConstants.REQUEST_CODE_PERMISSIONS) {
            if (! this.allPermissionsGranted()) {
                this.cameraAllowed = false;
            } else {
                this.cameraAllowed = true;
            }
            this.renderFromState();
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onDeviceReady(String deviceName) {
        this.renderFromState();
    }

    @Override
    public void onControllChanged(boolean controlled) {
        /*LEMSMobileProcotorAgentApplication app = (LEMSMobileProcotorAgentApplication) getApplication();
        if (app.getBluetoothCtrlMgr().isControlled() && app.getCameraManager().isOpened()) {
            this.stopWatching();
        } else if (!app.getBluetoothCtrlMgr().isControlled() && !app.getCameraManager().isOpened()) {
            this.startWatching();
        }*/
        this.renderFromState();
    }

    @Override
    public void onCommandReceived(ControlOrder order) {

    }

    @Override
    public void onConnect(Object[] info) {
        this.renderFromState();
    }

    @Override
    public void onDisconnect(Object[] info) {
        String disconnectionMsg = "unknown";
        if (info.length > 0 && info[0] != null) {
            if (info[0] instanceof Exception) {
                disconnectionMsg = ((Exception) info[0]).getMessage();
            } else {
                disconnectionMsg = info[0].toString();
            }
        }
        Log.w(LOG_TAG, "Disconnected from websocket: " + disconnectionMsg);
        this.stopWatching();
        this.setDebugMessages("Websocket disconnected:", disconnectionMsg);
        this.renderFromState();
    }

    @Override
    public void onConnectError(Exception ex) {
        final String errorMsg = ex == null ? "unknown" : ex.getMessage();
        Log.e(LOG_TAG, "Disconnected from websocket: " + errorMsg);
        this.onError = true;
        this.stopWatching();
        this.setDebugMessages("Websocket error:", errorMsg);
        this.renderFromState();
    }

    @Override
    public void onDataSent(String eventType, Object data) {
        if (WebSocketEventTypes.PICTURE_SNAPSHOT_EVENT_TYPE.equals(eventType)) {
            PictureSnapshot ps = (PictureSnapshot) data;
            if (ps.getSource().equals(PictureSnapshot.CameraType.FRONT)) {
                this.dataSentInfo.nbFrontPictureSnapshotsSent++;
                this.dataSentInfo.lastFrontDataSize = ps.getData().length / 1000F;
            } else if (ps.getSource().equals(PictureSnapshot.CameraType.BACK)) {
                this.dataSentInfo.nbBackPictureSnapshotsSent++;
                this.dataSentInfo.lastBackDataSize = ps.getData().length / 1000F;
            } else {
                this.dataSentInfo.nbOtherDataSent++;
            }
        } else {
            this.dataSentInfo.nbOtherDataSent++;
        }
        this.setDebugMessages("Information on data sent",
                this.dataSentInfo.lastFrontDataSize == null ?
                        String.format(Locale.US, "- Front pictures: %d",
                                this.dataSentInfo.nbFrontPictureSnapshotsSent) :
                        String.format(Locale.US, "- Front pictures: %d (last size: %.2f kB)",
                                this.dataSentInfo.nbFrontPictureSnapshotsSent, this.dataSentInfo.lastFrontDataSize),
                this.dataSentInfo.lastFrontDataSize == null ?
                        String.format(Locale.US, "- Back pictures: %d",
                                this.dataSentInfo.nbBackPictureSnapshotsSent) :
                        String.format(Locale.US, "- Back pictures: %d (last size: %.2f kB)",
                                this.dataSentInfo.nbBackPictureSnapshotsSent, this.dataSentInfo.lastBackDataSize),
                String.format(Locale.US, "- Other data: %d", this.dataSentInfo.nbOtherDataSent));
        this.renderFromState();
    }

    private static class WSDataSentInfo {
        public int nbFrontPictureSnapshotsSent = 0;
        public int nbBackPictureSnapshotsSent = 0;
        public int nbOtherDataSent = 0;
        public Float lastFrontDataSize = null;
        public Float lastBackDataSize = null;
    }
}
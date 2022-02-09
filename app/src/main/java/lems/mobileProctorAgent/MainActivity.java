package lems.mobileProctorAgent;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;
import java.util.Objects;

import lems.mobileProctorAgent.model.PictureSnapshot;

public class MainActivity extends AppCompatActivity {
    private final static String LOG_TAG = MainActivity.class.getName();

    private enum MainActivityState {
        LAUNCHING, CONNECTING, RUNNING, LAUNCH_ERROR_DATA, LAUNCH_ERROR_PERMISSION, CONNECTION_ERROR, RUNNING_ERROR
    }

    private MainActivityState currentState;
    private String webSocketEndpoint;
    private String webSocketJwt;
    private WSDataSentInfo dataSentInfo;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.entrypoint);
        this.setState(MainActivityState.LAUNCHING);
        this.findViewById(R.id.btnTakeQRCode).setOnClickListener((v) -> {
            this.takeQRCodePicture();
        });
        this.findViewById(R.id.btnRetryConnection).setOnClickListener((v) -> {
            this.retryConnection();
        });
        if (!this.checkAndSetLemsUri()) {
            this.setState(MainActivityState.LAUNCH_ERROR_DATA);
        } else {
            if (this.allPermissionsGranted()) {
                this.startWatching();
            } else {
                ActivityCompat.requestPermissions(this, AppConstants.REQUIRED_PERMISSIONS, AppConstants.REQUEST_CODE_PERMISSIONS);
            }
        }
    }

    private void setState(MainActivityState newState) {
        final ImageView iconEntrypoint = (ImageView) this.findViewById(R.id.iconEntrypoint);
        final TextView txtMainInfo = (TextView) this.findViewById(R.id.txtMainInfo);
        final TextView txtDebugInfo = (TextView) this.findViewById(R.id.txtDebugInfo);
        final Button btnTakeQrCode = (Button) this.findViewById(R.id.btnTakeQRCode);
        final Button btnRetryConnection = (Button) this.findViewById(R.id.btnRetryConnection);

        try {
            if (newState == MainActivityState.LAUNCHING) {
                if (this.currentState != null) {
                    throw new IllegalStateException(String.format("Unable to switch to state %s from state %s.", newState, this.currentState));
                }
                this.runOnUiThread(() -> {
                    iconEntrypoint.setImageDrawable(AppCompatResources.getDrawable(this.getApplicationContext(), R.drawable.ic_starting));
                    iconEntrypoint.setContentDescription(this.getString(R.string.ic_starting_desc));
                    txtMainInfo.setText(this.getString(R.string.ic_starting_desc));
                    txtDebugInfo.setVisibility(TextView.INVISIBLE);
                    btnTakeQrCode.setVisibility(Button.GONE);
                    btnRetryConnection.setVisibility(Button.GONE);
                    // Do not prevent screen off
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                });
                this.currentState = MainActivityState.LAUNCHING;
                return;
            }
            if (newState == MainActivityState.LAUNCH_ERROR_DATA) {
                if (this.currentState != MainActivityState.LAUNCHING) {
                    throw new IllegalStateException(String.format("Unable to switch to state %s from state %s.", newState, this.currentState));
                }
                this.runOnUiThread(() -> {
                    iconEntrypoint.setImageDrawable(AppCompatResources.getDrawable(this.getApplicationContext(), R.drawable.ic_error));
                    iconEntrypoint.setContentDescription(this.getString(R.string.ic_error_desc));
                    txtMainInfo.setText(this.getString(R.string.error_link_required));
                    txtDebugInfo.setVisibility(TextView.INVISIBLE);
                    btnTakeQrCode.setVisibility(Button.VISIBLE);
                    btnRetryConnection.setVisibility(Button.GONE);
                    // Do not prevent screen off
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                });
                this.currentState = MainActivityState.LAUNCH_ERROR_DATA;
                return;
            }
            if (newState == MainActivityState.LAUNCH_ERROR_PERMISSION) {
                if (this.currentState != MainActivityState.LAUNCHING) {
                    throw new IllegalStateException(String.format("Unable to switch to state %s from state %s.", newState, this.currentState));
                }
                this.runOnUiThread(() -> {
                    iconEntrypoint.setImageDrawable(AppCompatResources.getDrawable(this.getApplicationContext(), R.drawable.ic_error));
                    iconEntrypoint.setContentDescription(this.getString(R.string.ic_error_desc));
                    txtMainInfo.setText(this.getString(R.string.error_permission_required));
                    txtDebugInfo.setVisibility(TextView.INVISIBLE);
                    btnTakeQrCode.setVisibility(Button.INVISIBLE);
                    btnRetryConnection.setVisibility(Button.GONE);
                    // Do not prevent screen off
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                });
                this.currentState = MainActivityState.LAUNCH_ERROR_PERMISSION;
                return;
            }
            if (newState == MainActivityState.CONNECTING) {
                if (this.currentState != MainActivityState.LAUNCHING
                        && this.currentState != MainActivityState.CONNECTION_ERROR) {
                    throw new IllegalStateException(String.format("Unable to switch to state %s from state %s.", newState, this.currentState));
                }
                this.runOnUiThread(() -> {
                    iconEntrypoint.setImageDrawable(AppCompatResources.getDrawable(this.getApplicationContext(), R.drawable.ic_connecting));
                    iconEntrypoint.setContentDescription(this.getString(R.string.ic_connecting_desc));
                    txtMainInfo.setText(this.getString(R.string.ic_connecting_desc));
                    txtDebugInfo.setVisibility(TextView.INVISIBLE);
                    btnTakeQrCode.setVisibility(Button.GONE);
                    btnRetryConnection.setVisibility(Button.GONE);
                    // Prevent screen off
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                });
                this.currentState = MainActivityState.CONNECTING;
                return;
            }
            if (newState == MainActivityState.CONNECTION_ERROR) {
                if (this.currentState != MainActivityState.CONNECTING
                        && this.currentState != MainActivityState.CONNECTION_ERROR
                        && this.currentState != MainActivityState.RUNNING
                        && this.currentState != MainActivityState.RUNNING_ERROR) {
                    throw new IllegalStateException(String.format("Unable to switch to state %s from state %s.", newState, this.currentState));
                }
                this.runOnUiThread(() -> {
                    iconEntrypoint.setImageDrawable(AppCompatResources.getDrawable(this.getApplicationContext(), R.drawable.ic_error));
                    iconEntrypoint.setContentDescription(this.getString(R.string.ic_error_desc));
                    txtMainInfo.setText(this.getString(R.string.error_connection));
                    txtDebugInfo.setVisibility(TextView.INVISIBLE);
                    btnTakeQrCode.setVisibility(Button.GONE);
                    btnRetryConnection.setVisibility(Button.VISIBLE);
                    // Do not prevent screen off
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                });
                this.currentState = MainActivityState.CONNECTION_ERROR;
                return;
            }
            if (newState == MainActivityState.RUNNING) {
                if (this.currentState == MainActivityState.RUNNING) {
                    Log.i(LOG_TAG, "Ask to set state to RUNNING while already being in that state. do nothing");
                    return;
                }
                if (this.currentState != MainActivityState.CONNECTING) {
                    throw new IllegalStateException(String.format("Unable to switch to state %s from state %s.", newState, this.currentState));
                }
                this.runOnUiThread(() -> {
                    iconEntrypoint.setImageDrawable(AppCompatResources.getDrawable(this.getApplicationContext(), R.drawable.ic_taking_picture));
                    iconEntrypoint.setContentDescription(this.getString(R.string.ic_taking_picture_desc));
                    txtMainInfo.setText(this.getString(R.string.ic_taking_picture_desc));
                    txtDebugInfo.setVisibility(TextView.INVISIBLE);
                    btnTakeQrCode.setVisibility(Button.GONE);
                    btnRetryConnection.setVisibility(Button.GONE);
                    // Prevent screen off
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                });
                this.currentState = MainActivityState.RUNNING;
                return;
            }
            if (newState == MainActivityState.RUNNING_ERROR) {
                if (this.currentState != MainActivityState.RUNNING
                        && this.currentState != MainActivityState.CONNECTION_ERROR) {
                    throw new IllegalStateException(String.format("Unable to switch to state %s from state %s.", newState, this.currentState));
                }
                this.runOnUiThread(() -> {
                    iconEntrypoint.setImageDrawable(AppCompatResources.getDrawable(this.getApplicationContext(), R.drawable.ic_error));
                    iconEntrypoint.setContentDescription(this.getString(R.string.ic_error_desc));
                    txtMainInfo.setText(this.getString(R.string.error_taking_pictures));
                    txtDebugInfo.setVisibility(TextView.INVISIBLE);
                    btnTakeQrCode.setVisibility(Button.GONE);
                    btnRetryConnection.setVisibility(Button.GONE);
                    // Do not prevent screen off
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                });
                this.currentState = MainActivityState.RUNNING_ERROR;
                return;
            }
            throw new IllegalStateException("Unmanaged activity state: " + Objects.toString(newState));
        } catch (IllegalStateException ex) {
            Log.wtf(LOG_TAG, ex.getMessage());
            this.finish();
        }
    }

    private void printUIDebugInfo(Object... messages) {
        final TextView txtDebugInfo = (TextView) this.findViewById(R.id.txtDebugInfo);
        if (messages.length == 0) {
            this.runOnUiThread(() -> {
                txtDebugInfo.setText("");
                txtDebugInfo.setVisibility(TextView.INVISIBLE);
            });
            return;
        }
        final StringBuilder strBuilder = new StringBuilder();
        for (Object message : messages) {
            if (message != null) {
                strBuilder.append(message.toString()).append(System.lineSeparator());
            }
        }
        final String message = strBuilder.toString().trim();
        this.runOnUiThread(() -> {
            txtDebugInfo.setText(message);
            txtDebugInfo.setVisibility(TextView.VISIBLE);
        });
    }

    private void startWatching() {
        this.setState(MainActivityState.CONNECTING);
        LEMSMobileProcotorAgentApplication app = (LEMSMobileProcotorAgentApplication) getApplication();
        try {
            this.dataSentInfo = new WSDataSentInfo();
            app.getCameraManager().setContext(this);
            app.getWebSocketManager().open(this.webSocketEndpoint, this.webSocketJwt, (Object... args) -> {
                this.statCapturingPictures();
            }, (Object... args) -> {
                final String disconnectionMsg = args.length > 0 && args[0] != null ? args.toString() : "unknown";
                Log.w(LOG_TAG, "Disconnected from websocket: " + disconnectionMsg);
                if (this.currentState == MainActivityState.RUNNING) {
                    this.setState(MainActivityState.RUNNING_ERROR);
                    this.printUIDebugInfo("Websocket disconnected:", disconnectionMsg);
                }
            }, (Exception ex) -> {
                final String errorMsg = ex == null ? "unknown" : ex.getMessage();
                Log.e(LOG_TAG, "Disconnected from websocket: " + errorMsg);
                this.setState(MainActivityState.CONNECTION_ERROR);
                this.printUIDebugInfo("Websocket error:", errorMsg);
            }, (Object dataSent) -> {
                this.handleDataSent(dataSent);
            });
        } catch (Exception ex) {
            Log.e(LOG_TAG, "Unable to connect websocket: " + ex.getMessage());
            this.setState(MainActivityState.CONNECTION_ERROR);
            this.printUIDebugInfo("Error Connecting Websocket:", ex.getMessage());
        }
    }

    private void statCapturingPictures() {
        this.setState(MainActivityState.RUNNING);
        LEMSMobileProcotorAgentApplication app = (LEMSMobileProcotorAgentApplication) getApplication();
        try {
            app.getCameraManager().open();
        } catch (Exception ex) {
            Log.e(LOG_TAG, "Error taking picture: " + ex.getMessage());
            this.setState(MainActivityState.RUNNING_ERROR);
            this.printUIDebugInfo("Error starting taking picture: " + ex.getMessage());
        }
    }

    private void handleDataSent(Object dataSent) {
        if (dataSent instanceof PictureSnapshot) {
            PictureSnapshot ps = (PictureSnapshot) dataSent;
            if (ps.getSource().equals(PictureSnapshot.CameraType.FRONT)) {
                this.dataSentInfo.nbFrontPictureSnapshotsSent++;
            } else if (ps.getSource().equals(PictureSnapshot.CameraType.BACK)) {
                this.dataSentInfo.nbBackPictureSnapshotsSent++;
            } else {
                this.dataSentInfo.nbOtherDataSent++;
            }
        } else {
            this.dataSentInfo.nbOtherDataSent++;
        }
        this.printUIDebugInfo("Information on data sent",
                String.format(Locale.US, "- Front pictures: %d", this.dataSentInfo.nbFrontPictureSnapshotsSent),
                String.format(Locale.US, "- Back pictures: %d", this.dataSentInfo.nbBackPictureSnapshotsSent),
                String.format(Locale.US, "- Other data: %d", this.dataSentInfo.nbOtherDataSent));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == AppConstants.REQUEST_CODE_PERMISSIONS) {
            if (this.allPermissionsGranted()) {
                this.startWatching();
            } else {
                this.setState(MainActivityState.LAUNCH_ERROR_PERMISSION);
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private boolean allPermissionsGranted() {
        for (String p : AppConstants.REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(getBaseContext(), p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private boolean checkAndSetLemsUri() {
        if (AppConstants.useWebSocketTestInfo) {
            this.webSocketEndpoint = AppConstants.testWebSocketEndpointDebug;
            this.webSocketJwt = AppConstants.testWebSocketJWT;
            Log.d(LOG_TAG, "Launnch Data found. WebSocket is: " + this.webSocketEndpoint);
            return true;
        }
        Intent intent = this.getIntent();
        Uri data = intent.getData();
        if (data == null) {
            Log.i(LOG_TAG, "Missing launch data");
            return false;
        }
        String urlEncodedEndpoint = data.getQueryParameter("endpoint");
        String rawJwt = data.getQueryParameter("jwt");
        if (urlEncodedEndpoint == null || rawJwt == null) {
            Log.i(LOG_TAG, "Missing launch data endpoint or jwt");
            return false;
        }
        this.webSocketEndpoint = Uri.decode(urlEncodedEndpoint);
        this.webSocketJwt = rawJwt;
        Log.d(LOG_TAG, "Launnch Data found. WebSocket is: " + this.webSocketEndpoint);
        return true;
    }

    private void takeQRCodePicture() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            startActivity(cameraIntent);
        } catch (ActivityNotFoundException ex) {
            Log.e(LOG_TAG, "Unable to start camera: " + ex.getMessage());
        }
    }

    private void retryConnection() {
        this.startWatching();
    }

    private static class WSDataSentInfo {
        public int nbFrontPictureSnapshotsSent = 0;
        public int nbBackPictureSnapshotsSent = 0;
        public int nbOtherDataSent = 0;
    }
}
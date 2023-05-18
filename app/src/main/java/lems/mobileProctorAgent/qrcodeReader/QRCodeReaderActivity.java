package lems.mobileProctorAgent.qrcodeReader;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.annotation.SuppressLint;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.WindowManager;

import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.concurrent.ExecutionException;

import lems.mobileProctorAgent.AppConstants;
import lems.mobileProctorAgent.LEMSMobileProcotorAgentApplication;
import lems.mobileProctorAgent.R;

public class QRCodeReaderActivity extends AppCompatActivity {
    private final static String LOG_TAG = QRCodeReaderActivity.class.getName();

    private final static Size capturingResolution = new Size(1280, 720);
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private BarcodeScannerOptions barcodeScannerOptions;

    @GuardedBy("this")
    private Uri uriFound = null; // The uri that will be found

    @GuardedBy("this")
    private boolean achievingProcessing = false; // A marker of process ending

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set UI
        setContentView(R.layout.activity_qrcode_reader);
        // Prevent screen off
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Check if no testing data have been provided
        if (this.endActivityOnTestInfo()) {
            return;
        }

        // Get CameraProvide
        this.cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        // Prepare Barscanner options
        this.barcodeScannerOptions = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE).build();
        // Prepare image analysis
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(capturingResolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        // Retrieve main app to get the executor service
        LEMSMobileProcotorAgentApplication app = (LEMSMobileProcotorAgentApplication) getApplication();
        // Create the bar code analyzer and set it to the analysis
        imageAnalysis.setAnalyzer(app.getExecutorService(), imageProxy -> {
            @SuppressLint("UnsafeOptInUsageError") Image mediaImage = imageProxy.getImage();
            if (mediaImage != null) {
                // Retrieve the image set with the good angle (based on the device rotation)
                InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                // Get a scanner configured with our options
                BarcodeScanner scanner = BarcodeScanning.getClient(this.barcodeScannerOptions);
                // Start processing the image through the scanner.
                // At the end, if an uri was found, prepare to unbind camera and return
                this.startProcessingImage(scanner, image).addOnCompleteListener((listTask) -> {
                    imageProxy.close();
                    this.checkUriFoundAndstopPreviewAndFinish();
                });
            }
        });
        // Bind the camera to the analysis and preview
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                this.bindCamera(cameraProvider, imageAnalysis);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private boolean endActivityOnTestInfo() {
        if (AppConstants.useWebSocketTestInfo) {
            // Forge test uri
            final String wsEndpoint = AppConstants.testWebSocketEndpointDebug;
            final String wsJWT = AppConstants.testWebSocketJWT;
            Uri uri = Uri.parse(String.format("lems://proctoring?endpoint=%s&jwt=%s", wsEndpoint, wsJWT));
            Log.d(LOG_TAG, "Launch Data WS test found. WebSocket is: " + wsEndpoint);
            setResult(RESULT_OK, ReadQrCodeContract.createReturnedIntent(uri));
            Log.i(LOG_TAG, "Intent set as result. Finish");
            this.finish();
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop Preventing screen off
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void bindCamera(@NonNull ProcessCameraProvider cameraProvider, ImageAnalysis imageAnalysis) {
        // Retrieve the previewView of the UI
        PreviewView previewView = this.findViewById(R.id.qrCodeReaderPreviewView);
        // Build a preview
        Preview preview = new Preview.Builder().build();
        // Select back camera
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();
        // Set the previewVie to the preview
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        // Bind to the camera the analysis and the preview
        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, imageAnalysis, preview);
    }

    private Task<List<Barcode>> startProcessingImage(BarcodeScanner scanner, InputImage image) {
        return scanner.process(image)
                .addOnSuccessListener((List<Barcode> barcodes) -> {
                    for (Barcode barcode : barcodes) {
                        String rawUri = null;
                        if (barcode.getValueType() == Barcode.TYPE_URL) {
                            rawUri = barcode.getUrl().getUrl();
                        } else if (barcode.getValueType() == Barcode.TYPE_TEXT) {
                            rawUri = barcode.getRawValue();
                        }
                        if (rawUri != null) {
                            try {
                                Uri uri = Uri.parse(rawUri);
                                if (uri != null && uri.getScheme().equals("lems")) {
                                    this.setUriFound(uri);
                                    break;
                                }
                            } catch (Exception ex){
                                Log.i(LOG_TAG, "Unable to parse uri to Uri: " + ex.getMessage());
                            }
                        }
                    }
                })
                .addOnFailureListener((@NonNull Exception ex) -> {
                    Log.w(LOG_TAG, "Error while processing barcode: " + ex.getMessage());
                });
    }

    private synchronized void setUriFound(Uri uri) {
        this.uriFound = uri;
    }

    private synchronized void checkUriFoundAndstopPreviewAndFinish() {
        if (this.uriFound == null) {
            return;
        }
        if (this.achievingProcessing) {
            return;
        }
        Log.i(LOG_TAG, "URI FOUND for first time : prepare to close");
        this.achievingProcessing = true;
        final Uri uriToReturn = this.uriFound;
        this.cameraProviderFuture.addListener(() -> {
            try {
                final ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                cameraProvider.unbindAll();
                Log.d(LOG_TAG, "Unbind all done");
            } catch (InterruptedException | ExecutionException ex) {
                Log.d(LOG_TAG, "interruption or execution ex while accessing camer", ex);
            } finally {
                Log.d(LOG_TAG, "Set result and finish");
                setResult(RESULT_OK, ReadQrCodeContract.createReturnedIntent(uriToReturn));
                Log.i(LOG_TAG, "Intent set as result. Finish");
                this.finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }
}
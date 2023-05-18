package lems.mobileProctorAgent.camera;

import android.util.Log;
import android.util.Size;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import lems.mobileProctorAgent.model.PictureSnapshot;

public class TwoFacesPictureTaker implements Runnable{
    private final static String LOG_TAG = TwoFacesPictureTaker.class.getName();

    private final ScheduledExecutorService executorService;
    private final Consumer<PictureSnapshot> pictureSnapshotConsumer;
    private final ComponentActivity context;
    private final int expectedPictureWidth;
    private final int expectedPictureHeight;
    private PictureSnapshot.CameraType currentCameraType = PictureSnapshot.CameraType.FRONT;
    private ImageCapture currentImageCapture;

    public TwoFacesPictureTaker(ScheduledExecutorService executorService,
                                Consumer<PictureSnapshot> pictureSnapshotConsumer, ComponentActivity context,
                                int expectedPictureWidth, int expectedPictureHeight) {
        this.executorService = executorService;
        this.pictureSnapshotConsumer = pictureSnapshotConsumer;
        this.context = context;
        this.expectedPictureWidth = expectedPictureWidth;
        this.expectedPictureHeight = expectedPictureHeight;
    }

    @Override
    public void run() {
        Log.d(LOG_TAG, "In camera manager run; switch selector");
        CameraSelector camSelec = this.switchCameraSelector();
        Log.d(LOG_TAG, "rebind then wait");
        this.rebindCamera(camSelec);
        try {
            synchronized (this) {
                this.wait(3000L);
            }
            if (!Thread.interrupted()) {
                Log.d(LOG_TAG, "Camera manager run waken up, take picture");
                this.currentImageCapture.takePicture(this.executorService, new TwoFacesPictureTaker.PictureTakenCallback());
                Log.d(LOG_TAG, "Wait after having taken picture");
                synchronized (this) {
                    this.wait(500L);
                }
                Log.d(LOG_TAG, "Camera manager run waken up, end");
            }
        } catch (InterruptedException ex) {
            Log.d(LOG_TAG, "interruption or execution ex while waiting", ex);
        }
        Log.d(LOG_TAG, "End of cameraManager run.");
    }

    private CameraSelector switchCameraSelector() {
        //Create new image capture
        this.currentImageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetResolution(new Size(this.expectedPictureWidth, this.expectedPictureHeight))
                .build();
        this.currentCameraType = this.currentCameraType == PictureSnapshot.CameraType.FRONT ? PictureSnapshot.CameraType.BACK : PictureSnapshot.CameraType.FRONT;
        return this.currentCameraType == PictureSnapshot.CameraType.FRONT ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
    }

    private void rebindCamera(CameraSelector selector) {
        Log.d(LOG_TAG, "Camera rebinder, get camera provider future");
        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this.context);
        Log.d(LOG_TAG, "Camera rebinder, add listener");
        cameraProviderFuture.addListener(() -> {
            try {
                Log.d(LOG_TAG, "Camera rebinder, notify unbind then bind");
                final ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this.context, selector, this.currentImageCapture);
            } catch (InterruptedException | ExecutionException ex) {
                Log.d(LOG_TAG, "interruption or execution ex while accessing camer", ex);
            } finally {
                synchronized (this) {
                    Log.d(LOG_TAG, "Camera rebinder, notify all");
                    this.notifyAll();
                }
            }
        }, ContextCompat.getMainExecutor(this.context));
    }

    public class PictureTakenCallback extends ImageCapture.OnImageCapturedCallback {

        @Override
        public void onCaptureSuccess(@NonNull ImageProxy image) {
            try {
                Log.d(LOG_TAG, "Picture taken from camera " + currentCameraType.toString());
                super.onCaptureSuccess(image);
                if (image.getPlanes().length == 0) {
                    Log.w(LOG_TAG, "Image captured without planes");
                }
                // Retrieve buffer
                ByteBuffer bb = image.getPlanes()[0].getBuffer();
                // Retrieve buffer data as array
                final byte[] pictData = new byte[bb.remaining()];
                bb.get(pictData);
                // Forge proof
                final PictureSnapshot proof = new PictureSnapshot(currentCameraType, pictData);
                if (pictureSnapshotConsumer != null) {
                    pictureSnapshotConsumer.accept(proof);
                }
            } catch (Throwable ex) {
                Log.e(LOG_TAG, "Error " + ex.getClass().getName() + " while taking pict: " + ex.getLocalizedMessage());
            } finally {
                image.close();
                Log.d(LOG_TAG, "Wake up");
                synchronized (TwoFacesPictureTaker.PictureTakenCallback.this) {
                    TwoFacesPictureTaker.PictureTakenCallback.this.notifyAll();
                }
            }
        }

        @Override
        public void onError(@NonNull ImageCaptureException exception) {
            super.onError(exception);
            Log.w(LOG_TAG, "Image not captured from cam. " + currentCameraType.toString() + ": " + exception.getLocalizedMessage());
        }

    }
}

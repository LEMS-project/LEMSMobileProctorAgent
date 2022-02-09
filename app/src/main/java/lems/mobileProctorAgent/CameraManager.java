package lems.mobileProctorAgent;

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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import lems.mobileProctorAgent.model.PictureSnapshot;

public class CameraManager implements AutoCloseable, Runnable {

    public static final long DFLT_PICTURE_INTERVAL_MS = 3000;
    private final static String LOG_TAG = CameraManager.class.getName();

    private final ScheduledExecutorService executorService;
    private final Consumer<PictureSnapshot> pictureSnapshotConsumer;
    private final long pictureIntervalMs;
    private ComponentActivity context;
    private volatile ScheduledFuture<?> pendingTask;
    private PictureSnapshot.CameraType currentCameraType = PictureSnapshot.CameraType.FRONT;
    private ImageCapture currentImageCapture;

    public CameraManager(ScheduledExecutorService executorService, Consumer<PictureSnapshot> pictureSnapshotConsumer) {
        this.executorService = executorService;
        this.pictureSnapshotConsumer = pictureSnapshotConsumer;
        this.pictureIntervalMs = DFLT_PICTURE_INTERVAL_MS;
    }

    public CameraManager(ScheduledExecutorService executorService, Consumer<PictureSnapshot> pictureSnapshotConsumer, long pictureIntervalMs) {
        this.executorService = executorService;
        this.pictureSnapshotConsumer = pictureSnapshotConsumer;
        this.pictureIntervalMs = pictureIntervalMs;
    }

    public ComponentActivity getContext() {
        return this.context;
    }

    public void setContext(ComponentActivity context) {
        this.context = context;
    }

    public synchronized void open() {
        if (this.isOpened()) {
            return;
        }
        Log.i(LOG_TAG, "Opening picture manager");
        this.prepareCameras();
        Log.d(LOG_TAG, "Setup taks in executor");
        this.pendingTask = this.executorService.scheduleWithFixedDelay(this,
                0L, this.pictureIntervalMs, TimeUnit.MILLISECONDS);
    }

    public boolean isOpened() {
        return this.pendingTask != null
                && !this.pendingTask.isDone()
                && !this.pendingTask.isCancelled();
    }

    public synchronized void close() throws Exception {
        Log.d(LOG_TAG, "Closing camera manager");
        if (this.isOpened()) {
            Log.d(LOG_TAG, "Shutdown pending task");
            this.pendingTask.cancel(true);
            Log.d(LOG_TAG, "Wait for task termination");
            try {
                this.pendingTask.get(3L, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Log.d(LOG_TAG, "interruption while waiting for task to achieve", ex);
            }
            Log.d(LOG_TAG, "Stopping cameras");
            this.releaseCameras();
            this.pendingTask = null;
        }

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
            if (Thread.interrupted()) {
                return;
            }
            Log.d(LOG_TAG, "Camera manager run waken up, take picture");
            this.takePicture(new PictureTakenCallback());
            Log.d(LOG_TAG, "Wait after having taken picture");
            synchronized (this) {
                this.wait(500L);
            }
            Log.d(LOG_TAG, "Camera manager run waken up, end");
        } catch (InterruptedException ex) {
            Log.d(LOG_TAG, "interruption or execution ex while waiting", ex);
        }
    }

    private void prepareCameras() {

    }

    private void releaseCameras() {
        Log.i(LOG_TAG, "Unbind all camera use cases");
        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this.context);
        try {
            final ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
            cameraProvider.unbindAll();
        } catch (InterruptedException | ExecutionException ex) {
            Log.w(LOG_TAG, "interruption or execution ex while accessing camer", ex);
        }
    }

    private CameraSelector switchCameraSelector() {
        //Create new image capture
        this.currentImageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetResolution(new Size(AppConstants.EXPECTED_PICTURE_WIDTH, AppConstants.EXPECTED_PICTURE_HEIGHT))
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

    private void takePicture(PictureTakenCallback callback) {
        this.currentImageCapture.takePicture(this.executorService, callback);
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
                Log.d(LOG_TAG, "Retrieve buffer");
                ByteBuffer bb = image.getPlanes()[0].getBuffer();
                Log.d(LOG_TAG, "Retrieve buffer data as array");
                final byte[] pictData = new byte[bb.remaining()];
                bb.get(pictData);
                Log.d(LOG_TAG, "Forge proof");
                final PictureSnapshot proof = new PictureSnapshot(currentCameraType, pictData);
                if (pictureSnapshotConsumer == null) {
                    Log.i(LOG_TAG, "Picture handled: " + proof.toString());
                } else {
                    pictureSnapshotConsumer.accept(proof);
                }
            } catch (Throwable ex) {
                Log.e(LOG_TAG, "Error " + ex.getClass().getName() + " while taking pict: " + ex.getLocalizedMessage());
            } finally {
                image.close();
                Log.d(LOG_TAG, "Wake up");
                synchronized (PictureTakenCallback.this) {
                    PictureTakenCallback.this.notifyAll();
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

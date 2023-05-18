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
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import lems.mobileProctorAgent.model.PictureSnapshot;

public class CameraManager implements AutoCloseable {

    public static final long DFLT_PICTURE_INTERVAL_MS = 3000;
    public static final int DFLT_EXPECTED_PICTURE_WIDTH = 416;
    public static final int DFLT_EXPECTED_PICTURE_HEIGHT = 416;
    private final static String LOG_TAG = CameraManager.class.getName();

    private final ScheduledExecutorService executorService;
    private final Consumer<PictureSnapshot> pictureSnapshotConsumer;
    private final long pictureIntervalMs;
    private final int expectedPictureWidth;
    private final int expectedPictureHeight;
    private ComponentActivity context;
    private volatile ScheduledFuture<?> pendingTask;

    public CameraManager(ScheduledExecutorService executorService, Consumer<PictureSnapshot> pictureSnapshotConsumer) {
        this.executorService = executorService;
        this.pictureSnapshotConsumer = pictureSnapshotConsumer;
        this.pictureIntervalMs = DFLT_PICTURE_INTERVAL_MS;
        this.expectedPictureWidth = DFLT_EXPECTED_PICTURE_WIDTH;
        this.expectedPictureHeight = DFLT_EXPECTED_PICTURE_HEIGHT;
    }

    public CameraManager(ScheduledExecutorService executorService, Consumer<PictureSnapshot> pictureSnapshotConsumer,
                         long pictureIntervalMs, int expectedPictureWidth, int expectedPictureHeight) {
        this.executorService = executorService;
        this.pictureSnapshotConsumer = pictureSnapshotConsumer;
        this.pictureIntervalMs = pictureIntervalMs;
        this.expectedPictureWidth = expectedPictureWidth;
        this.expectedPictureHeight = expectedPictureHeight;
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
        final TwoFacesPictureTaker runner = new TwoFacesPictureTaker(this.executorService,
                this.pictureSnapshotConsumer, this.context, this.expectedPictureWidth,
                this.expectedPictureHeight);
        this.pendingTask = this.executorService.scheduleWithFixedDelay(runner,
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
            this.pendingTask.cancel(false);
            Log.d(LOG_TAG, "Wait for task termination");
            try {
                this.pendingTask.get(3L, TimeUnit.SECONDS);
            } catch (InterruptedException | CancellationException ex) {
                Log.d(LOG_TAG, "interruption while waiting for task to achieve");
            }
            //Log.d(LOG_TAG, "Stopping cameras");
            //this.releaseCameras();
            this.pendingTask = null;
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
}

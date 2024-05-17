package lems.mobileProctorAgent.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import androidx.activity.ComponentActivity;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import lems.mobileProctorAgent.AppConstants;
import lems.mobileProctorAgent.model.ControlInfo;
import lems.mobileProctorAgent.model.ControlOrder;
import lems.mobileProctorAgent.websocket.DeviceControlListener;
import lems.mobileProctorAgent.websocket.WebsocketManager;

public class BluetoothManager implements DeviceControlListener, AutoCloseable {
    private final static String LOG_TAG = BluetoothManager.class.getName();

    private final ScheduledExecutorService executorService;
    private final ArrayList<BluetoothManagerListener> btManagerListeners;

    private WebsocketManager wsManager;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic characteristic;
    private String bluetoothDeviceName;
    private boolean ready;

    boolean controlled;

    ScheduledFuture futurResetPitch;

    public BluetoothManager(ScheduledExecutorService executorService) {
        this.executorService = executorService;
        this.btManagerListeners = new ArrayList<>();
        this.ready = false;
    }

    public void open(ComponentActivity context, String controllerAddrMac) throws SecurityException {
        this.ready = false;
        this.init(context);
        final BluetoothDevice connectingDevice = this.bluetoothAdapter.getRemoteDevice(controllerAddrMac);
        // Connect the device through GATT
        this.bluetoothGatt = connectingDevice.connectGatt(context, false, this.bluetoothGattCallback);
    }

    public boolean isOpened() {
        return this.ready;
    }

    public String getBluetoothDeviceName() {
        return this.bluetoothDeviceName;
    }

    public boolean isControlled() {
        return this.controlled;
    }

    @Override
    public void close() {
        this.ready = false;
        this.bluetoothDeviceName = null;
        this.bluetoothGatt = null;
        this.characteristic = null;
    }

    public void addManagerListener(BluetoothManagerListener listener) {
        if (!this.btManagerListeners.contains(listener)) {
            this.btManagerListeners.add(listener);
        }
    }

    public void removeManagerListener(BluetoothManagerListener listener) {
        this.btManagerListeners.remove(listener);
    }

    public void setWebsocketManager(WebsocketManager mgr) {
        if (this.wsManager != null) {
            this.wsManager.removeDeviceControlListener(this);
        }
        this.wsManager = mgr;
        if (this.wsManager != null) {
            this.wsManager.addDeviceControlListener(this);
        }
    }

    public void sendControlOrder(ControlOrder order) throws SecurityException {
        if (!this.ready) {
            Log.w(LOG_TAG,"Cannot send control order, manager not opened");
            return;
        }
        if (!order.isValidMoveCommand()) {
            Log.w(LOG_TAG, String.format("Unmanaged controler move command: {%s, %s, %s}", order.getCode(), order.getRotation(), order.getPitch()));
            return;
        }

        final byte[] command = BluetoothCommandUtils.computeMoveOrderMessage(order.getRotation(), order.getPitch());
        boolean res = false;
        synchronized (this) {
            if (this.futurResetPitch != null) {
                this.futurResetPitch.cancel(true);
                this.futurResetPitch = null;
            }
            this.characteristic.setValue(command);
            res  = this.bluetoothGatt.writeCharacteristic(this.characteristic);
            // Send stop pitch order in 200ms if required
            if (order.getPitch() != 0) {
                this.futurResetPitch = this.executorService.schedule(
                        new PitchReseter(order.getRotation()), 200, TimeUnit.MILLISECONDS);
            }
        }
        Log.i(LOG_TAG, "Command sent: " + res);

        /*final BluetoothGatt gatt = this.bluetoothGatt;
        final BluetoothGattCharacteristic charac = this.characteristic;
        this.executorService.submit(() -> {
            final byte[] command = BluetoothCommandUtils.computeMoveOrderMessage(order.getRotation(), order.getPitch());
            charac.setValue(command);
            boolean res  = gatt.writeCharacteristic(charac);
            Log.i(LOG_TAG, "Command sent: " + res);
        });*/
    }

    public void startAutoRotate() {
        ControlOrder order = new ControlOrder(ControlOrder.ControlerOrderCode.MOVE);
        order.setRotation(1);
        order.setPitch(0);
        this.sendControlOrder(order);
    }

    public void stopAutoRotate() {
        ControlOrder order = new ControlOrder(ControlOrder.ControlerOrderCode.MOVE);
        order.setRotation(0);
        order.setPitch(0);
        this.sendControlOrder(order);
    }

    @Override
    public void onControlOrder(ControlOrder order) {
        boolean controlChanged = false;
        switch (order.getCode()) {
            case LOCK:
                controlChanged = !this.controlled;
                this.controlled = true;
                this.wsManager.sendControlInfo(new ControlInfo(this.controlled));
                break;
            case UNLOCK:
                controlChanged = this.controlled;
                this.controlled = false;
                this.wsManager.sendControlInfo(new ControlInfo(this.controlled));
                break;
            case MOVE:
                if (!this.controlled) {
                    Log.w(LOG_TAG, "Received controller position change order while not controlled");
                } else {
                    Log.i(LOG_TAG, "Got order");
                    this.sendControlOrder(order);
                }
                break;
            default:
                Log.d(LOG_TAG, "Unmanaged command: " + order.getCode());
                return;
        }
        btManagerListeners.forEach((l) -> l.onCommandReceived(order));
    }

    private void init(ComponentActivity context) {
        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final android.bluetooth.BluetoothManager bluetoothManager =
                (android.bluetooth.BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager.getAdapter();
        // Check that adapter exist (bluetooth not available otherwise, but this should have been checked previously)
        if (this.bluetoothAdapter == null) {
            Log.w(LOG_TAG, "Cannot handle bluetooth");
            throw new IllegalStateException("Cannot handle bluetooth");
        }
        // Check that bluetooth has been enabled. Otherwise ask for it
        if (!this.bluetoothAdapter.isEnabled()) {
            Log.w(LOG_TAG, "bluetooth not enabled");
            throw new IllegalStateException("bluetooth not enabled");
        }
    }

    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) throws SecurityException {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // successfully connected to the GATT Server
                Log.i(LOG_TAG, "GATT Connection change for connected");
                bluetoothGatt.discoverServices();

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // disconnected from the GATT Server
                Log.i(LOG_TAG, "GATT Connection change for disconnected");
                bluetoothGatt = null;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(LOG_TAG, "Services discovered for bt " + bluetoothGatt.getDevice().getAddress());
                retrieveServiceAndCharacteristic();
            } else {
                Log.w(LOG_TAG, "onServicesDiscovered received: " + status);
                bluetoothGatt = null;
            }
        }
    };

    private void retrieveServiceAndCharacteristic() throws SecurityException {
        BluetoothGattService svc = this.bluetoothGatt.getService(UUID.fromString(AppConstants.BT_GATT_SERVICE));
        if (svc == null) {
            Log.w(LOG_TAG, "Cannot get service");
            this.bluetoothGatt = null;
            return;
        }
        if (svc.getCharacteristics().isEmpty()) {
            Log.w(LOG_TAG, "Service does not have any characteristic");
            this.bluetoothGatt = null;
            return;
        }
        if (svc.getCharacteristics().size() > 1) {
            Log.w(LOG_TAG, "Service has more than one characteristic. Take the first one");
        }
        this.characteristic = svc.getCharacteristics().get(0);
        this.ready = true;
        final String deviceName = this.bluetoothGatt.getDevice().getName();
        this.bluetoothDeviceName = deviceName == null || deviceName.isEmpty() ? this.bluetoothGatt.getDevice().getAddress() : deviceName;
        btManagerListeners.forEach((l) -> l.onDeviceReady(this.bluetoothDeviceName));
    }

    private class PitchReseter implements Runnable {
        private final int lastRotation;

        public PitchReseter(int lastRotation) {
            this.lastRotation = lastRotation;
        }

        @Override
        public void run() {
            final byte[] command = BluetoothCommandUtils.computeMoveOrderMessage(this.lastRotation, 0);
            boolean res = false;
            synchronized (BluetoothManager.this) {
                try {
                    characteristic.setValue(command);
                    res = bluetoothGatt.writeCharacteristic(characteristic);
                } catch (SecurityException ex){

                }
            }
            Log.i(LOG_TAG, "Reset Pitch sent: " + res);
        }
    }

}

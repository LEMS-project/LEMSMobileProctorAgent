package lems.mobileProctorAgent.bluetooth;

import lems.mobileProctorAgent.model.ControlOrder;

public interface BluetoothManagerListener {
    void onDeviceReady(String deviceName);

    void onControllChanged(boolean controlled);

    void onCommandReceived(ControlOrder order);
}

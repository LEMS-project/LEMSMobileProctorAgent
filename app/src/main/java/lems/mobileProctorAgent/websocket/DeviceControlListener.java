package lems.mobileProctorAgent.websocket;

import lems.mobileProctorAgent.model.ControlOrder;

public interface DeviceControlListener {

    void onControlOrder(ControlOrder order);
}

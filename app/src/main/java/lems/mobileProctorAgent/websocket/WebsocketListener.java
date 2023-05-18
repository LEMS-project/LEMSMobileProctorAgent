package lems.mobileProctorAgent.websocket;

import lems.mobileProctorAgent.model.WebSocketEventTypes;

public interface WebsocketListener {

    void onConnect(Object[] info);

    void onDisconnect(Object[] info);

    void onConnectError(Exception ex);

    void onDataSent(String eventType, Object data);
}

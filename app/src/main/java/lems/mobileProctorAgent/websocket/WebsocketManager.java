package lems.mobileProctorAgent.websocket;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.client.SocketOptionBuilder;
import io.socket.emitter.Emitter;
import io.socket.engineio.client.EngineIOException;
import lems.mobileProctorAgent.model.ControlInfo;
import lems.mobileProctorAgent.model.ControlOrder;
import lems.mobileProctorAgent.model.PictureSnapshot;
import lems.mobileProctorAgent.model.WebSocketEventTypes;

public class WebsocketManager implements AutoCloseable {
    private final static String LOG_TAG = WebsocketManager.class.getName();

    private final Gson jsonConverter;
    private final ArrayList<WebsocketListener> websocketListeners;
    private final ArrayList<DeviceControlListener> deviceControlListeners;
    private boolean connecting;
    private String endpoint;
    private Socket websocket;


    public WebsocketManager() {
        this.jsonConverter = new GsonBuilder()
                .registerTypeAdapter(PictureSnapshot.class, new PictureSnapshot.Serializer())
                .serializeNulls()
                .create();
        this.websocketListeners = new ArrayList<>();
        this.deviceControlListeners = new ArrayList<>();
    }

    public void addWebsocketListener(WebsocketListener listener) {
        if (!this.websocketListeners.contains(listener)) {
            this.websocketListeners.add(listener);
        }
    }

    public void removeWebsocketListener(WebsocketListener listener) {
        this.websocketListeners.remove(listener);
    }

    public void addDeviceControlListener(DeviceControlListener listener) {
        if (!this.deviceControlListeners.contains(listener)) {
            this.deviceControlListeners.add(listener);
        }
    }

    public void removeDeviceControlListener(DeviceControlListener listener) {
        this.deviceControlListeners.remove(listener);
    }

    public void open(String wsEndpoint, String jwt, String wsPath) {
        if (this.websocket != null) {
            this.close();
        }
        Map<String, String> authOption = new HashMap<>();
        authOption.put("jwt", jwt);
        SocketOptionBuilder builder = IO.Options.builder()
                .setReconnectionAttempts(1)
                .setAuth(authOption);
        if (wsPath != null) {
            builder.setPath(wsPath);
        }
        IO.Options sockOptions = builder.build();
        try {
            this.connecting = true;
            this.endpoint = wsEndpoint;
            this.websocket = IO.socket(wsEndpoint, sockOptions);
            this.websocket.on(Socket.EVENT_CONNECT, this.intOnConnect);
            this.websocket.on(Socket.EVENT_DISCONNECT, this.intOnDisconnect);
            this.websocket.on(Socket.EVENT_CONNECT_ERROR, this.intOnConnectError);
            this.websocket.on(WebSocketEventTypes.CONTROL_COMMAND_EVENT_TYPE, this.intOnControlCommand);
            this.websocket.connect();
        } catch (Exception ex) {
            this.connecting = false;
            this.intOnConnectError.call(ex);
        }
    }

    public boolean isConnecting() {
        return this.connecting;
    }

    public boolean isOpened() {
        return this.websocket != null && this.websocket.connected();
    }

    public String getEndpoint() {
        return this.websocket != null ? this.endpoint : null;
    }

    @Override
    public synchronized void close() {
        this.connecting = false;
        if (this.websocket != null) {
            if (this.websocket.connected()) {
                this.websocket.disconnect();
            }
            this.websocket.off(Socket.EVENT_CONNECT, this.intOnConnect);
            this.websocket.off(Socket.EVENT_DISCONNECT, this.intOnDisconnect);
            this.websocket.off(Socket.EVENT_CONNECT_ERROR, this.intOnConnectError);
            this.websocket.close();
            this.websocket = null;
        }

    }

    public void sendPictureSnapshot(PictureSnapshot pictureSnapshot) {
        if (pictureSnapshot == null) {
            return;
        }
        if (!this.isOpened()) {
            Log.w(LOG_TAG, "Cannot send picture snapshot");
            return;
        }
        try {
            final JSONObject jsonObject = new JSONObject(this.jsonConverter.toJson(pictureSnapshot));
            this.websocket.emit(WebSocketEventTypes.PICTURE_SNAPSHOT_EVENT_TYPE, jsonObject);
            websocketListeners.forEach((l) -> l.onDataSent(WebSocketEventTypes.PICTURE_SNAPSHOT_EVENT_TYPE, pictureSnapshot));
        } catch (JSONException ex) {
            Log.e(LOG_TAG, "Cannot convert picture snapshot to json: " + ex.getMessage());
        } catch (Exception ex) {
            Log.e(LOG_TAG, "Exception " + ex.getClass().getName() + " while sending picture snapshot over websocket: " + ex.getMessage());
        }
    }

    public void sendControlInfo(ControlInfo controlInfo) {
        try {
            final JSONObject jsonObject = new JSONObject(jsonConverter.toJson(controlInfo));
            websocket.emit(WebSocketEventTypes.MOBILE_CONTROL_EVENT_TYPE, jsonObject);
            websocketListeners.forEach((l) -> l.onDataSent(WebSocketEventTypes.MOBILE_CONTROL_EVENT_TYPE, jsonObject));
        } catch (JSONException ex) {
            Log.e(LOG_TAG, "Cannot controlled indicator to json: " + ex.getMessage());
        } catch (Exception ex) {
            Log.e(LOG_TAG, "Exception " + ex.getClass().getName() + " while sending mobile control over websocket: " + ex.getMessage());
        }
    }

    private final Emitter.Listener intOnConnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            Log.d(LOG_TAG, "Websocket connected");
            connecting = false;
            websocketListeners.forEach((l) -> l.onConnect(args));
        }
    };

    private final Emitter.Listener intOnDisconnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            Log.d(LOG_TAG, "Websocket disconnected");
            for (Object arg : args) {
                if (arg == null) {
                    continue;
                }
                Log.d(LOG_TAG, "- arg: " + arg + " (class: " + arg.getClass().getName() + ")");
            }
            connecting = false;
            websocketListeners.forEach((l) -> l.onDisconnect(args));
        }
    };

    private final Emitter.Listener intOnConnectError = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            Log.e(LOG_TAG, "Websocket encoutered error while connecting");
            Exception exception = null;
            for (Object arg : args) {
                if (arg == null) {
                    continue;
                }
                if (arg instanceof Exception) {
                    exception = (Exception) arg;
                    if (exception instanceof EngineIOException) {
                        EngineIOException eiex = (EngineIOException) exception;
                        Log.i(LOG_TAG, String.format("EngineIOException transport=%s, code=%s : %s",
                                eiex.transport, eiex.code, eiex.getMessage()));
                        eiex.printStackTrace();
                    } else {
                        Log.i(LOG_TAG, "Exception " + exception.getClass().getName() + ": " + exception.getMessage());
                    }
                    break;
                } else if (arg instanceof JSONObject) {
                    JSONObject jsonObj = (JSONObject) arg;
                    if (jsonObj.has("message")) {
                        try {
                            exception = new Exception(jsonObj.getString("message"));
                            break;
                        } catch (JSONException ex) {
                            exception = new Exception(jsonObj.toString());
                            break;
                        }
                    } else {
                        exception = new Exception(jsonObj.toString());
                        break;
                    }
                } else {
                    Log.i(LOG_TAG, "Other: " + arg + " (class: " + arg.getClass().getName() + ")");
                    exception = new Exception(arg.toString());
                    break;
                }
            }
            connecting = false;
            final Exception ex = exception;
            websocketListeners.forEach((l) -> l.onConnectError(ex));
        }
    };

    private final Emitter.Listener intOnControlCommand = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            if (args.length == 0 || ! (args[0] instanceof JSONObject)) {
                Log.d(LOG_TAG, "No Control info");
                return;
            }
            final ControlOrder co = ControlOrder.fromJSONObject((JSONObject) args[0]);
            if (!co.isValidCode()) {
                Log.d(LOG_TAG, "Unmanaged command: " + co.getCode());
                return;
            }
            deviceControlListeners.forEach((l) -> l.onControlOrder(co));
        }
    };
}

package lems.mobileProctorAgent;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import io.socket.engineio.client.EngineIOException;
import lems.mobileProctorAgent.model.PictureSnapshot;
import lems.mobileProctorAgent.model.WebSocketEventTypes;

public class WebSocketManager implements AutoCloseable {
    private final static String LOG_TAG = WebSocketManager.class.getName();
    private final Gson jsonConverter;
    private Socket websocket;
    private Consumer<Object[]> onConnect, onDisconnect;
    private Consumer<Exception> onConnectError;
    private Consumer<Object> onDataSent;

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
            if (onDisconnect != null) {
                onDisconnect.accept(args);
            }
        }
    };

    private final Emitter.Listener intOnConnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            Log.d(LOG_TAG, "Websocket connected");
            if (onConnect != null) {
                onConnect.accept(args);
            }
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
            if (onConnectError != null) {
                onConnectError.accept(exception);
            }
        }
    };

    public WebSocketManager() {
        this.jsonConverter = new GsonBuilder()
                .registerTypeAdapter(PictureSnapshot.class, new PictureSnapshot.Serializer())
                .serializeNulls()
                .create();
    }

    public synchronized Socket open(String wsEndpoint,
                                    String jwt,
                                    Consumer<Object[]> onConnect,
                                    Consumer<Object[]> onDisconnect,
                                    Consumer<Exception> onConnectError,
                                    Consumer<Object> onDataSent) throws Exception {
        if (this.websocket != null) {
            this.close();
        }
        Map<String, String> authOption = new HashMap<>();
        authOption.put("jwt", jwt);
        IO.Options sockOptions = IO.Options.builder()
                .setReconnectionAttempts(1)
                .setAuth(authOption)
                .build();
        this.websocket = IO.socket(wsEndpoint, sockOptions);
        this.onConnect = onConnect;
        this.onDisconnect = onDisconnect;
        this.onConnectError = onConnectError;
        this.onDataSent = onDataSent;
        this.websocket.on(Socket.EVENT_CONNECT, this.intOnConnect);
        this.websocket.on(Socket.EVENT_DISCONNECT, this.intOnDisconnect);
        this.websocket.on(Socket.EVENT_CONNECT_ERROR, this.intOnConnectError);
        this.websocket.connect();
        return this.websocket;
    }

    public synchronized Socket open(String wsEndpoint, String jwt) throws Exception {
        return this.open(wsEndpoint, jwt, null, null, null, null);
    }

    public boolean isOpen() {
        return this.websocket != null && this.websocket.connected();
    }

    @Override
    public synchronized void close() {
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

    public void sendTest() {
        if (!this.isOpen()) {
            Log.w(LOG_TAG, "Cannot send test");
            return;
        }
        try {
            this.websocket.emit(WebSocketEventTypes.TEST_EVENT_TYPE, "TEST");
        } catch (Exception ex) {
            Log.e(LOG_TAG, "Exception " + ex.getClass().getName() + " while sending test over websocket: " + ex.getMessage());
        }
    }

    public void sendPictureSnapshot(PictureSnapshot pictureSnapshot) {
        if (pictureSnapshot == null) {
            return;
        }
        if (!this.isOpen()) {
            Log.w(LOG_TAG, "Cannot send picture snapshot");
            return;
        }
        try {
            final JSONObject jsonObject = new JSONObject(this.jsonConverter.toJson(pictureSnapshot));
            this.websocket.emit(WebSocketEventTypes.PICTURE_SNAPSHOT_EVENT_TYPE, jsonObject);
            if (this.onDataSent != null) {
                this.onDataSent.accept(pictureSnapshot);
            }
        } catch (JSONException ex) {
            Log.e(LOG_TAG, "Cannot convert picture snapshot to json: " + ex.getMessage());
        } catch (Exception ex) {
            Log.e(LOG_TAG, "Exception " + ex.getClass().getName() + " while sending picture snapshot over websocket: " + ex.getMessage());
        }
    }

    protected Socket getWebsocket() {
        return this.websocket;
    }
}

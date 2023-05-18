package lems.mobileProctorAgent.model;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

public class ControlOrder {
    private final ControlerOrderCode code;
    private Integer rotation; // <0: counter-clockwise | 0: stop | >0: clockwise
    private Integer pitch; // <0: down | 0: stop | >0: up

    public ControlOrder(ControlerOrderCode code) {
        this.code = code;
    }

    public ControlerOrderCode getCode() {
        return code;
    }

    public Integer getRotation() {
        return rotation;
    }

    public void setRotation(Integer rotation) {
        this.rotation = rotation;
    }

    public Integer getPitch() {
        return pitch;
    }

    public void setPitch(Integer pitch) {
        this.pitch = pitch;
    }

    public boolean isValidCode() {
        switch (code) {
            case LOCK:
            case UNLOCK:
            case MOVE:
                return true;
            default:
                return false;
        }
    }

    public boolean isValidMoveCommand() {
        if (this.code != ControlerOrderCode.MOVE) {
            return false;
        }
        if (this.rotation == null || this.pitch == null) {
            return false;
        }
        return true;
    }

    public static ControlOrder fromJSONObject(JSONObject jsonObj) {
        try {
            final String sCode = jsonObj.getString("code");
            final ControlerOrderCode code = ControlerOrderCode.valueOf(sCode);
            final ControlOrder order = new ControlOrder(code);
            if (jsonObj.has("rotation")) {
                order.setRotation(jsonObj.getInt("rotation"));
            }
            if (jsonObj.has("pitch")) {
                order.setPitch(jsonObj.getInt("pitch"));
            }
            return order;
        } catch (IllegalArgumentException | JSONException ex) {
            return null;
        }
    }

    public enum ControlerOrderCode {
        LOCK, UNLOCK, MOVE
    }
}

package lems.mobileProctorAgent.model;

import android.util.Base64;

import androidx.annotation.NonNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Objects;

public class PictureSnapshot {
    public enum CameraType {FRONT, BACK}
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);
    private CameraType source;
    private Long timestamp;
    private byte[] data;

    public PictureSnapshot() {
    }

    public PictureSnapshot(CameraType source, Long timestamp, byte[] data) {
        this.source = source;
        this.timestamp = timestamp;
        this.data = data;
    }

    public CameraType getSource() {
        return source;
    }

    public void setSource(CameraType source) {
        this.source = source;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    @NonNull
    @Override
    public String toString() {
        String tsRep;
        try {
            tsRep = DATE_FORMAT.format(timestamp);
        } catch (IllegalArgumentException ex) {
            tsRep = timestamp != null ? Long.toString(timestamp) : "null";
        }
        return String.format(Locale.ENGLISH,
                "Proof{source = '%s', timestamp = '%s', |data| = %d}",
                Objects.toString(this.source), tsRep, (data != null ? data.length : -1));
    }

    public static class Serializer implements JsonSerializer<PictureSnapshot> {

        @Override
        public JsonElement serialize(PictureSnapshot pictureSnapshot, Type typeOfSrc, JsonSerializationContext context) {
            final JsonObject obj = new JsonObject();
            obj.add("source", new JsonPrimitive(Objects.toString(pictureSnapshot.getSource())));
            String tsRep;
            try {
                tsRep = DATE_FORMAT.format(pictureSnapshot.getTimestamp());
            } catch (IllegalArgumentException ex) {
                tsRep = pictureSnapshot.getTimestamp() != null ? Long.toString(pictureSnapshot.getTimestamp()) : null;
            }
            obj.add("timestamp", tsRep != null ? new JsonPrimitive(tsRep) : JsonNull.INSTANCE);
            String dataRepr = Base64.encodeToString(pictureSnapshot.getData(), 0,
                    pictureSnapshot.getData().length, Base64.DEFAULT);
            obj.add("data", new JsonPrimitive(dataRepr));
            return obj;
        }
    }
}

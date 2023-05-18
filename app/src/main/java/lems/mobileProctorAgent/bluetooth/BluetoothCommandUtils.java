package lems.mobileProctorAgent.bluetooth;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BluetoothCommandUtils {
    public static final int MESSAGE_LENGTH = 2;
    /*
    2 bytes for Read and Write
    Message format:
        2 bytes, little-endian
        1st byte: rotiation
        2nd byte: pitch

        0x00 = -100 steps/rev
        0x80 = 0 steps/rev
        0xFF = 100 steps/rev

        stop the plateform from moving: 0x8080 (stop rotational and pitch)
        rotate the plateform clockwise: 0xFF80
        rotate the plateforme counter-clowise: 0x0080
        pitch up the pltaform: 0x80FF
        pitch down the plateform 0x8000
        rotate and pitch the plateform at the same time: 0xFFFF (clockwise and up) or 0x0000 (counter-clockwise and down)

     */

    /**
     * Compute a 2 bytes little-endian move (rotation and pitch) order message for the bluetooth controller
     * @param rotation the rotation sens. 0: stop, <0: counter-clockwise, >0: clockwise
     * @param pitch the pitch movement. 0: stop, <0: down, >0: up
     * @return the 2 bytes array
     */
    public static byte[] computeMoveOrderMessage(int rotation, int pitch) {
        final ByteBuffer buffer = ByteBuffer
                .allocate(MESSAGE_LENGTH)
                .order(ByteOrder.LITTLE_ENDIAN);

        //First we put the pitch order
        if (pitch > 0) { // Go up
            buffer.put((byte) 0xFF);
        } else if (pitch < 0) { // Go down
            buffer.put((byte) 0x00);
        } else { // Stop pitch
            buffer.put((byte) 0x80);
        }

        //Then we put the rotation order
        if (rotation > 0) { // Go clockwise
            buffer.put((byte) 0xFF);
        } else if (rotation < 0) { // Go counter-clockwise
            buffer.put((byte) 0x00);
        } else { // Stop rotation
            buffer.put((byte) 0x80);
        }

        return buffer.array();
    }

    public static String moveOrderMessageToString(byte[] orderMessage, int offset) {
        if (orderMessage == null || orderMessage.length < offset + MESSAGE_LENGTH) {
            return null;
        }
        return String.format("%x %x", orderMessage[offset + 1], orderMessage[offset]);
    }
}

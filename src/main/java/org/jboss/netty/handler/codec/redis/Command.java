package org.jboss.netty.handler.codec.redis;

import org.jboss.netty.buffer.ChannelBuffer;

import java.io.IOException;

/**
 * Command serialization.
 * User: sam
 * Date: 7/27/11
 * Time: 3:04 PM
 * To change this template use File | Settings | File Templates.
 */
public class Command {
    public static final byte[] ARGS_PREFIX = "*".getBytes();
    public static final byte[] CRLF = "\r\n".getBytes();
    public static final byte[] BYTES_PREFIX = "$".getBytes();
    public static final byte[] EMPTY_BYTES = new byte[0];
    public static final byte[] NEG_ONE = Command.numToBytes(-1);

    private byte[][] arguments;
    private Object[] objects;

    public String getName() {
        if (arguments == null) {
            Object o = objects[0];
            if (o instanceof byte[]) {
                return new String((byte[]) o);
            } else {
                return o.toString();
            }
        } else {
            return new String(arguments[0]);
        }
    }

    public Command(byte[]... arguments) {
        this.arguments = arguments;
    }

    public Command(Object... objects) {
        this.objects = objects;
    }

    public void write(ChannelBuffer os) throws IOException {
        writeDirect(os, objects);
    }

    public static void writeDirect(ChannelBuffer os, Object... objects) throws IOException {
        int length = objects.length;
        byte[][] arguments = new byte[length][];
        for (int i = 0; i < length; i++) {
            Object object = objects[i];
            if (object == null) {
                arguments[i] = EMPTY_BYTES;
            } else if (object instanceof byte[]) {
                arguments[i] = (byte[]) object;
            } else {
                arguments[i] = object.toString().getBytes(Reply.UTF_8);
            }
        }
        writeDirect(os, arguments);
    }

    private static void writeDirect(ChannelBuffer os, byte[][] arguments) throws IOException {
        os.writeBytes(ARGS_PREFIX);
        os.writeBytes(Command.numToBytes(arguments.length));
        os.writeBytes(CRLF);
        for (byte[] argument : arguments) {
            os.writeBytes(BYTES_PREFIX);
            os.writeBytes(Command.numToBytes(argument.length));
            os.writeBytes(CRLF);
            os.writeBytes(argument);
            os.writeBytes(CRLF);
        }
    }

    private static final int NUM_MAP_LENGTH = 256;
    private static byte[][] numMap = new byte[NUM_MAP_LENGTH][];

    static {
        for (int i = 0; i < NUM_MAP_LENGTH; i++) {
            numMap[i] = convert(i);
        }
    }

    // Optimized for the direct to ASCII bytes case
    // Could be even more optimized but it is already
    // about twice as fast as using Long.toString().getBytes()
    public static byte[] numToBytes(long value) {
        if (value >= 0 && value < NUM_MAP_LENGTH) {
            return numMap[((int) value)];
        } else if (value == -1) {
            return NEG_ONE;
        }
        return convert(value);
    }

    private static byte[] convert(long value) {
        boolean negative = value < 0;
        int index = negative ? 2 : 1;
        long current = negative ? -value : value;
        while ((current /= 10) > 0) {
            index++;
        }
        byte[] bytes = new byte[index];
        if (negative) {
            bytes[0] = '-';
        }
        current = negative ? -value : value;
        long tmp = current;
        while ((tmp /= 10) > 0) {
            bytes[--index] = (byte) ('0' + (current % 10));
            current = tmp;
        }
        bytes[--index] = (byte) ('0' + current);
        return bytes;
    }

}

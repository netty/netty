package io.netty.channel.unix;

import java.nio.ByteBuffer;

public final class ControlMessage {

    /* Set GSO segmentation size */
    private static final int UDP_SEGMENT = 103;
    private static final int SOL_UDP = 17;

    private final int level;
    private final int type;
    private final ByteBuffer data;

    private ControlMessage(int level, int type, ByteBuffer data) {
        this.level = level;
        this.type = type;
        this.data = data;
    }

    public int length() {
        return data().remaining();
    }

    public int level() {
        return level;
    }

    public int type() {
        return type;
    }

    public ByteBuffer data() {
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ControlMessage that = (ControlMessage) o;

        if (level != that.level) {
            return false;
        }
        if (type != that.type) {
            return false;
        }
        return data.equals(that.data);
    }

    @Override
    public int hashCode() {
        int result = level;
        result = 31 * result + type;
        result = 31 * result + data.hashCode();
        return result;
    }

    public static ControlMessage udpSegment(int segmentSize) {
        return new ControlMessage(SOL_UDP, UDP_SEGMENT , ByteBuffer.allocate(4).putInt(segmentSize).flip());
    }

    public static ControlMessage generic(int level, int type, ByteBuffer buffer) {
        return new ControlMessage(level, type, buffer);
    }
}

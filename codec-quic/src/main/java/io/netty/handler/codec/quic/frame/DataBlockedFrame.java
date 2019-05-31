package io.netty.handler.codec.quic.frame;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.quic.VarInt;

public class DataBlockedFrame extends QuicFrame {

    private VarInt dataLimit;

    public DataBlockedFrame() {
        super(FrameType.DATA_BLOCKED);
    }

    public DataBlockedFrame(VarInt dataLimit) {
        this();
        this.dataLimit = dataLimit;
    }

    public DataBlockedFrame(long dataLimit) {
        this(VarInt.byLong(dataLimit));
    }

    @Override
    public void read(ByteBuf buf) {
        dataLimit = VarInt.read(buf);
    }

    @Override
    public void write(ByteBuf buf) {
        super.write(buf);
        dataLimit.write(buf);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        DataBlockedFrame that = (DataBlockedFrame) o;

        return dataLimit != null ? dataLimit.equals(that.dataLimit) : that.dataLimit == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (dataLimit != null ? dataLimit.hashCode() : 0);
        return result;
    }

    public VarInt dataLimit() {
        return dataLimit;
    }

    public void dataLimit(VarInt dataLimit) {
        this.dataLimit = dataLimit;
    }
}

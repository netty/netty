package io.netty.handler.codec.quic.frame;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.quic.VarInt;

public class MaxDataFrame extends QuicFrame {

    private VarInt maxData;

    public MaxDataFrame() {
        super(FrameType.MAX_DATA);
    }

    public MaxDataFrame(long maxData) {
        this(VarInt.byLong(maxData));
    }

    public MaxDataFrame(VarInt maxData) {
        this();
        this.maxData = maxData;
    }

    @Override
    public void read(ByteBuf buf) {
        maxData = VarInt.read(buf);
    }

    @Override
    public void write(ByteBuf buf) {
        super.write(buf);
        maxData.write(buf);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        MaxDataFrame that = (MaxDataFrame) o;

        return maxData != null ? maxData.equals(that.maxData) : that.maxData == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (maxData != null ? maxData.hashCode() : 0);
        return result;
    }

    public VarInt maxData() {
        return maxData;
    }

    public void maxData(VarInt maxData) {
        this.maxData = maxData;
    }
}

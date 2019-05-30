package io.netty.handler.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.quic.frame.*;
import io.netty.util.internal.StringUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class QuicFrameTest {

    public static final QuicFrame[] TEST_FRAMES = new QuicFrame[] {
            new CloseFrame((short) 200, "Server Error", true),
            new CloseFrame((short) 200, "Server Error", false),
            new CryptFrame(200, new byte[400]),
            new DataLimitFrame(20),
            new QuicFrame(FrameType.PING),
            new MaxDataFrame(200),
            new MaxStreamDataFrame(20, 200),
            new MaxStreamsFrame(true, 20),
            new PaddingFrame(200),
            new ResetStreamFrame(20, (short) 1000, 400)
    };

    @Test
    public void testReadWrite(){
        for (QuicFrame frame : TEST_FRAMES) {
            ByteBuf buf = Unpooled.buffer();
            frame.write(buf);
            assertEquals(StringUtil.simpleClassName(frame), frame, FrameType.readFrame(buf));
        }
    }

}

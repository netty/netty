/*
 *
 *  * Copyright 2019 The Netty Project
 *  *
 *  * The Netty Project licenses this file to you under the Apache License,
 *  * version 2.0 (the "License"); you may not use this file except in compliance
 *  * with the License. You may obtain a copy of the License at:
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  * License for the specific language governing permissions and limitations
 *  * under the License.
 *
 */

package io.netty.handler.codec.quic.frame;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.quic.StreamID;
import io.netty.handler.codec.quic.TransportError;
import io.netty.util.internal.StringUtil;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class QuicFrameTest {

    public static final QuicFrame[] TEST_FRAMES = new QuicFrame[] {
            //new ApplicationCloseFrame((short) 4, "Server Error"),
            //new ConnectionCloseFrame(TransportError.INTERNAL_ERROR, "Server Error", (byte) 0x00),
            new CryptFrame(40, new byte[400]),
            new DataBlockedFrame(20),
            new QuicFrame(FrameType.PING),
            new MaxDataFrame(200),
            new MaxStreamDataFrame(20, 200),
            new MaxStreamsFrame(true, 20),
            new PaddingFrame(200),
            new PathFrame(true, new byte[8]),
            new PathFrame(false, new byte[8]),
            new RetireConnectionIdFrame(2),
            new StopSendingFrame(StreamID.byLong(80000), (short) 2),
            new StreamBlockedFrame(true, 200),
            new StreamDataBlockedFrame(StreamID.byLong(80000), 300),
            //new StreamFrame(true, StreamID.byLong(80000), new byte[32]),
            new StreamResetFrame(20, (short) 1000, 400)
    };

    @Test
    public void testReadWrite(){
        for (QuicFrame frame : TEST_FRAMES) {
            System.out.println(frame.toString());
            ByteBuf buf = Unpooled.buffer();
            try {
                frame.write(buf);
                //byte[] bytes = new byte[buf.readableBytes()];
                //buf.readBytes(bytes);
                //System.out.println(Arrays.toString(bytes));
                QuicFrame actual = FrameType.readFrame(buf);
                assertEquals(0, buf.readableBytes());
                assertEquals(StringUtil.simpleClassName(frame), frame, actual);
            } finally {
                buf.release();
            }
        }
    }

    @Test
    public void testTypeById() {
        byte id = 0x1d;
        do {
            assertNotNull("No Frame type for " + Integer.toHexString(id), FrameType.typeById(id));
        } while (--id >= 0x00);
    }

    @Test
    public void testTypesImplemented() {
        Set<Class> types = new HashSet<Class>();
        for (FrameType type : FrameType.values()) {
            QuicFrame frame = type.constructFrame(type.firstIdentifier());
            if (!(frame instanceof PathFrame)) {
                Class clazz = frame.getClass();
                assertTrue(StringUtil.simpleClassName(clazz) + " is used by two types", types.add(clazz));
            }
        }
    }

    @Test
    public void hasToString() {
        for (FrameType type : FrameType.values()) {
            QuicFrame frame = type.constructFrame(type.firstIdentifier());
            String toString = frame.toString();
            boolean noToString = toString.contains("@") || (QuicFrame.class != frame.getClass() && toString.startsWith("QuicFrame"));
            assertFalse(StringUtil.simpleClassName(frame) + " has no toString() method", noToString);
        }
    }

}

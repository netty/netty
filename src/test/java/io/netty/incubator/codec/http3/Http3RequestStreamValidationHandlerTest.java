/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.incubator.codec.http3;

import io.netty.buffer.Unpooled;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.incubator.codec.quic.QuicChannel;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static io.netty.incubator.codec.http3.Http3TestUtils.assertException;
import static io.netty.incubator.codec.http3.Http3TestUtils.assertFrameEquals;
import static io.netty.incubator.codec.http3.Http3TestUtils.mockParent;
import static io.netty.incubator.codec.http3.Http3TestUtils.verifyClose;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class Http3RequestStreamValidationHandlerTest extends Http3FrameTypeValidationHandlerTest {
    @Override
    protected Http3FrameTypeValidationHandler<Http3RequestStreamFrame> newHandler() {
        return new Http3RequestStreamValidationHandler(true);
    }

    @Override
    protected List<Http3RequestStreamFrame> newValidFrames() {
        return Arrays.asList(new DefaultHttp3HeadersFrame(), new DefaultHttp3DataFrame(Unpooled.directBuffer()),
                new DefaultHttp3UnknownFrame(Http3CodecUtils.MAX_RESERVED_FRAME_TYPE, Unpooled.buffer()));
    }

    @Test
    public void testInvalidFrameSequenceStartInbound() {
        QuicChannel parent = mockParent();
        EmbeddedChannel channel = new EmbeddedChannel(parent, DefaultChannelId.newInstance(), true, false,
                newHandler());
        Http3DataFrame dataFrame = new DefaultHttp3DataFrame(Unpooled.buffer());
        try {
            channel.writeInbound(dataFrame);
            fail();
        } catch (Exception e) {
            assertException(Http3ErrorCode.H3_FRAME_UNEXPECTED, e);
        }
        verifyClose(Http3ErrorCode.H3_FRAME_UNEXPECTED, parent);
        assertEquals(0, dataFrame.refCnt());
        assertFalse(channel.finish());
    }

    @Test
    public void testInvalidFrameSequenceEndInbound() {
        QuicChannel parent = mockParent();

        EmbeddedChannel channel = new EmbeddedChannel(parent, DefaultChannelId.newInstance(), true, false,
                newHandler());
        Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
        Http3DataFrame dataFrame = new DefaultHttp3DataFrame(Unpooled.buffer());
        Http3DataFrame dataFrame2 = new DefaultHttp3DataFrame(Unpooled.buffer());
        Http3DataFrame dataFrame3 = new DefaultHttp3DataFrame(Unpooled.buffer());
        Http3HeadersFrame trailersFrame = new DefaultHttp3HeadersFrame();

        assertTrue(channel.writeInbound(headersFrame));
        assertTrue(channel.writeInbound(dataFrame.retainedDuplicate()));
        assertTrue(channel.writeInbound(dataFrame2.retainedDuplicate()));
        assertTrue(channel.writeInbound(trailersFrame));
        try {
            channel.writeInbound(dataFrame3);
            fail();
        } catch (Exception e) {
            assertException(Http3ErrorCode.H3_FRAME_UNEXPECTED, e);
        }

        verifyClose(Http3ErrorCode.H3_FRAME_UNEXPECTED, parent);
        assertTrue(channel.finish());
        assertEquals(0, dataFrame3.refCnt());

        assertFrameEquals(headersFrame, channel.readInbound());
        assertFrameEquals(dataFrame, channel.readInbound());
        assertFrameEquals(dataFrame2, channel.readInbound());
        assertFrameEquals(trailersFrame, channel.readInbound());
        assertNull(channel.readInbound());
    }

    @Test
    public void testInvalidFrameSequenceStartOutbound() {
        EmbeddedChannel channel = new EmbeddedChannel(newHandler());

        Http3DataFrame dataFrame = new DefaultHttp3DataFrame(Unpooled.buffer());
        try {
            channel.writeOutbound(dataFrame);
            fail();
        } catch (Exception e) {
            assertException(Http3ErrorCode.H3_FRAME_UNEXPECTED, e);
        }
        assertFalse(channel.finish());
        assertEquals(0, dataFrame.refCnt());
    }

    @Test
    public void testInvalidFrameSequenceEndOutbound() {
        EmbeddedChannel channel = new EmbeddedChannel(newHandler());

        Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
        Http3DataFrame dataFrame = new DefaultHttp3DataFrame(Unpooled.buffer());
        Http3DataFrame dataFrame2 = new DefaultHttp3DataFrame(Unpooled.buffer());
        Http3DataFrame dat3Frame3 = new DefaultHttp3DataFrame(Unpooled.buffer());
        Http3HeadersFrame trailersFrame = new DefaultHttp3HeadersFrame();
        assertTrue(channel.writeOutbound(headersFrame));
        assertTrue(channel.writeOutbound(dataFrame.retainedDuplicate()));
        assertTrue(channel.writeOutbound(dataFrame2.retainedDuplicate()));
        assertTrue(channel.writeOutbound(trailersFrame));

        try {
            channel.writeOutbound(dat3Frame3);
            fail();
        } catch (Exception e) {
            assertException(Http3ErrorCode.H3_FRAME_UNEXPECTED, e);
        }
        assertTrue(channel.finish());
        assertEquals(0, dat3Frame3.refCnt());

        assertFrameEquals(headersFrame, channel.readOutbound());
        assertFrameEquals(dataFrame, channel.readOutbound());
        assertFrameEquals(dataFrame2, channel.readOutbound());
        assertFrameEquals(trailersFrame, channel.readOutbound());
        assertNull(channel.readOutbound());
    }
}

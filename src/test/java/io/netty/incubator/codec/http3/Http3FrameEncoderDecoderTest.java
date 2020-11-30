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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class Http3FrameEncoderDecoderTest {

    @Parameterized.Parameters
    public static Object[] parameters() {
        return new Object[] { false, true };
    }

    private final boolean fragmented;

    public Http3FrameEncoderDecoderTest(boolean fragmented) {
        this.fragmented = fragmented;
    }

    @Test
    public void testHttp3CancelPushFrame_63() {
        testFrameEncodedAndDecoded(new DefaultHttp3CancelPushFrame(63));
    }

    @Test
    public void testHttp3CancelPushFrame_16383() {
        testFrameEncodedAndDecoded(new DefaultHttp3CancelPushFrame(16383));
    }

    @Test
    public void testHttp3CancelPushFrame_1073741823() {
        testFrameEncodedAndDecoded(new DefaultHttp3CancelPushFrame(1073741823));
    }

    @Test
    public void testHttp3CancelPushFrame_4611686018427387903() {
        testFrameEncodedAndDecoded(new DefaultHttp3CancelPushFrame(4611686018427387903L));
    }

    @Test
    public void testHttp3DataFrame() {
        byte[] bytes = new byte[1024];
        ThreadLocalRandom.current().nextBytes(bytes);
        testFrameEncodedAndDecoded(new DefaultHttp3DataFrame(Unpooled.wrappedBuffer(bytes)));
    }

    @Test
    public void testHttp3GoAwayFrame_63() {
        testFrameEncodedAndDecoded(new DefaultHttp3GoAwayFrame(63));
    }

    @Test
    public void testHttp3GoAwayFrame_16383() {
        testFrameEncodedAndDecoded(new DefaultHttp3GoAwayFrame(16383));
    }

    @Test
    public void testHttp3GoAwayFrame_1073741823() {
        testFrameEncodedAndDecoded(new DefaultHttp3GoAwayFrame(1073741823));
    }

    @Test
    public void testHttp3MaxPushIdFrame_63() {
        testFrameEncodedAndDecoded(new DefaultHttp3MaxPushIdFrame(63));
    }

    @Test
    public void testHttp3MaxPushIdFrame_16383() {
        testFrameEncodedAndDecoded(new DefaultHttp3MaxPushIdFrame(16383));
    }

    @Test
    public void testHttp3MaxPushIdFrame_1073741823() {
        testFrameEncodedAndDecoded(new DefaultHttp3MaxPushIdFrame(1073741823));
    }

    @Test
    public void testHttp3SettingsFrame() {
        Http3SettingsFrame settingsFrame = new DefaultHttp3SettingsFrame();
        settingsFrame.put(Http3Constants.SETTINGS_QPACK_MAX_TABLE_CAPACITY, 100L);
        settingsFrame.put(Http3Constants.SETTINGS_QPACK_BLOCKED_STREAMS, 1L);
        settingsFrame.put(Http3Constants.SETTINGS_MAX_FIELD_SECTION_SIZE, 128L);
        // Ensure we can encode and decode all sizes correctly.
        settingsFrame.put(63, 63L);
        settingsFrame.put(16383, 16383L);
        settingsFrame.put(1073741823, 1073741823L);
        settingsFrame.put(4611686018427387903L, 4611686018427387903L);
        testFrameEncodedAndDecoded(settingsFrame);
    }

    @Test
    public void testHttp3HeadersFrame() {
        Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
        addRequestHeaders(headersFrame.headers());
        testFrameEncodedAndDecoded(headersFrame);
    }

    @Test
    public void testHttpPushPromiseFrame() {
        Http3PushPromiseFrame pushPromiseFrame = new DefaultHttp3PushPromiseFrame(9);
        addRequestHeaders(pushPromiseFrame.headers());
        testFrameEncodedAndDecoded(pushPromiseFrame);
    }

    private static void addRequestHeaders(Http3Headers headers) {
        headers.add(":authority", "netty.quic"); // name only
        headers.add(":path", "/"); // name & value
        headers.add(":method", "GET"); // name & value with few options per name
        headers.add("x-qpack-draft", "19");
    }

    private void testFrameEncodedAndDecoded(Http3Frame frame) {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(new Http3FrameEncoder(new QpackEncoder()));
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new Http3FrameDecoder(new QpackDecoder()));

        assertTrue(encoderChannel.writeOutbound(retainAndDuplicate(frame)));
        ByteBuf buffer = encoderChannel.readOutbound();
        if (fragmented) {
            do {
                ByteBuf slice = buffer.readRetainedSlice(
                        ThreadLocalRandom.current().nextInt(buffer.readableBytes() + 1));
                assertEquals(!buffer.isReadable(), decoderChannel.writeInbound(slice));
            } while (buffer.isReadable());
            buffer.release();
        } else {
            assertTrue(decoderChannel.writeInbound(buffer));
        }
        Http3Frame readFrame = decoderChannel.readInbound();
        assertEquals(frame, readFrame);
        ReferenceCountUtil.release(readFrame);
        ReferenceCountUtil.release(frame);
        assertFalse(encoderChannel.finish());
        assertFalse(decoderChannel.finish());
    }

    private static Http3Frame retainAndDuplicate(Http3Frame frame) {
        if (frame instanceof ByteBufHolder) {
            return (Http3Frame) ((ByteBufHolder) frame).retainedDuplicate();
        }
        return frame;
    }
}

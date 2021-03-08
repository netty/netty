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
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.incubator.codec.quic.QuicChannel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_CANCEL_PUSH_FRAME_MAX_LEN;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_CANCEL_PUSH_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_GO_AWAY_FRAME_MAX_LEN;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_GO_AWAY_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_HEADERS_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_MAX_PUSH_ID_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_PUSH_PROMISE_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_SETTINGS_FRAME_MAX_LEN;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_SETTINGS_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.writeVariableLengthInteger;
import static io.netty.incubator.codec.http3.Http3TestUtils.assertException;
import static io.netty.incubator.codec.http3.Http3TestUtils.mockParent;
import static io.netty.incubator.codec.http3.Http3TestUtils.verifyClose;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class Http3FrameCodecTest {
    private static final int MAX_HEADER_SIZE = 1024;

    @Parameterized.Parameters(name = "{index}: fragmented = {0}")
    public static Object[] parameters() {
        return new Object[] { false, true };
    }

    private final boolean fragmented;

    public Http3FrameCodecTest(boolean fragmented) {
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
        settingsFrame.put(Http3SettingsFrame.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY, 100L);
        settingsFrame.put(Http3SettingsFrame.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS, 1L);
        settingsFrame.put(Http3SettingsFrame.HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE, 128L);
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
    public void testHttp3PushPromiseFrame() {
        Http3PushPromiseFrame pushPromiseFrame = new DefaultHttp3PushPromiseFrame(9);
        addRequestHeaders(pushPromiseFrame.headers());
        testFrameEncodedAndDecoded(pushPromiseFrame);
    }

    @Test
    public void testHttp3UnknownFrame() {
        testFrameEncodedAndDecoded(new DefaultHttp3UnknownFrame(Http3CodecUtils.MIN_RESERVED_FRAME_TYPE,
                Unpooled.buffer().writeLong(8)));
    }

    // Reserved types that were used in HTTP/2 and should close the connection with an error
    @Test
    public void testDecodeReservedFrameType0x2() {
        testDecodeReservedFrameType(0x2);
    }

    @Test
    public void testDecodeReservedFrameType0x6() {
        testDecodeReservedFrameType(0x6);
    }

    @Test
    public void testDecodeReservedFrameType0x8() {
        testDecodeReservedFrameType(0x8);
    }

    @Test
    public void testDecodeReservedFrameType0x9() {
        testDecodeReservedFrameType(0x9);
    }

    private void testDecodeReservedFrameType(long type) {
        QuicChannel parent = mockParent();

        EmbeddedChannel decoderChannel = new EmbeddedChannel(parent, DefaultChannelId.newInstance(),
                true, false, newCodec());
        ByteBuf buffer = Unpooled.buffer();
        Http3CodecUtils.writeVariableLengthInteger(buffer, type);

        try {
            decoderChannel.writeInbound(buffer);
        } catch (Exception e) {
            assertException(Http3ErrorCode.H3_FRAME_UNEXPECTED, e);
        }
        verifyClose(Http3ErrorCode.H3_FRAME_UNEXPECTED, parent);
        assertFalse(decoderChannel.finish());
        assertEquals(0, buffer.refCnt());
    }

    @Test
    public void testEncodeReservedFrameType0x2() {
        testEncodeReservedFrameType(0x2);
    }

    @Test
    public void testEncodeReservedFrameType0x6() {
        testEncodeReservedFrameType(0x6);
    }

    @Test
    public void testEncodeReservedFrameType0x8() {
        testEncodeReservedFrameType(0x8);
    }

    @Test
    public void testEncodeReservedFrameType0x9() {
        testEncodeReservedFrameType(0x9);
    }

    private void testEncodeReservedFrameType(long type) {
        QuicChannel parent = mockParent();

        EmbeddedChannel encoderChannel = new EmbeddedChannel(parent, DefaultChannelId.newInstance(),
                true, false, newCodec());
        Http3UnknownFrame frame = mock(Http3UnknownFrame.class);
        when(frame.type()).thenReturn(type);
        when(frame.touch()).thenReturn(frame);
        when(frame.touch(any())).thenReturn(frame);
        try {
            encoderChannel.writeOutbound(frame);
        } catch (Exception e) {
            assertException(Http3ErrorCode.H3_FRAME_UNEXPECTED, e);
        }
        // should have released the frame as well
        verify(frame, times(1)).release();
        verifyClose(Http3ErrorCode.H3_FRAME_UNEXPECTED, parent);
        assertFalse(encoderChannel.finish());
    }

    // Reserved types that were used in HTTP/2 and should close the connection with an error
    @Test
    public void testDecodeReservedSettingsKey0x2() {
        testDecodeReservedSettingsKey(0x2);
    }

    @Test
    public void testDecodeReservedSettingsKey0x3() {
        testDecodeReservedSettingsKey(0x3);
    }

    @Test
    public void testDecodeReservedSettingsKey0x4() {
        testDecodeReservedSettingsKey(0x4);
    }

    @Test
    public void testDecodeReservedSettingsKey0x5() {
        testDecodeReservedSettingsKey(0x5);
    }

    private void testDecodeReservedSettingsKey(long key) {
        ByteBuf buffer = Unpooled.buffer();
        Http3CodecUtils.writeVariableLengthInteger(buffer, Http3CodecUtils.HTTP3_SETTINGS_FRAME_TYPE);
        Http3CodecUtils.writeVariableLengthInteger(buffer, 2);
        // Write the key and some random value... Both should be only 1 byte long each.
        Http3CodecUtils.writeVariableLengthInteger(buffer, key);
        Http3CodecUtils.writeVariableLengthInteger(buffer, 1);
        testDecodeInvalidSettings(buffer);
    }

    @Test
    public void testDecodeSettingsWithSameKey() {
        ByteBuf buffer = Unpooled.buffer();
        Http3CodecUtils.writeVariableLengthInteger(buffer, Http3CodecUtils.HTTP3_SETTINGS_FRAME_TYPE);
        Http3CodecUtils.writeVariableLengthInteger(buffer, 4);
        // Write the key and some random value... Both should be only 1 byte long each.
        Http3CodecUtils.writeVariableLengthInteger(buffer, Http3SettingsFrame.HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE);
        Http3CodecUtils.writeVariableLengthInteger(buffer, 1);
        Http3CodecUtils.writeVariableLengthInteger(buffer, Http3SettingsFrame.HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE);
        Http3CodecUtils.writeVariableLengthInteger(buffer, 1);

        testDecodeInvalidSettings(buffer);
    }

    private void testDecodeInvalidSettings(ByteBuf buffer) {
        QuicChannel parent = mockParent();

        EmbeddedChannel decoderChannel = new EmbeddedChannel(parent, DefaultChannelId.newInstance(),
                true, false, newCodec());
        try {
            decoderChannel.writeInbound(buffer);
        } catch (Exception e) {
            assertException(Http3ErrorCode.H3_SETTINGS_ERROR, e);
        }
        verifyClose(Http3ErrorCode.H3_SETTINGS_ERROR, parent);
        assertFalse(decoderChannel.finish());
        assertEquals(0, buffer.refCnt());
    }

    @Test
    public void testEncodeReservedSettingsKey0x2() {
        testEncodeReservedSettingsKey(0x2);
    }

    @Test
    public void testEncodeReservedSettingsKey0x3() {
        testEncodeReservedSettingsKey(0x3);
    }

    @Test
    public void testEncodeReservedSettingsKey0x4() {
        testEncodeReservedSettingsKey(0x4);
    }

    @Test
    public void testEncodeReservedSettingsKey0x5() {
        testEncodeReservedSettingsKey(0x5);
    }

    private void testEncodeReservedSettingsKey(long key) {
        QuicChannel parent = mockParent();

        EmbeddedChannel encoderChannel = new EmbeddedChannel(parent, DefaultChannelId.newInstance(),
                true, false, newCodec());
        Http3SettingsFrame frame = mock(Http3SettingsFrame.class);
        when(frame.iterator()).thenReturn(Collections.singletonMap(key, 0L).entrySet().iterator());
        try {
            encoderChannel.writeOutbound(frame);
        } catch (Exception e) {
            assertException(Http3ErrorCode.H3_SETTINGS_ERROR, e);
        }
        verifyClose(Http3ErrorCode.H3_SETTINGS_ERROR, parent);
        assertFalse(encoderChannel.finish());
    }

    private static void addRequestHeaders(Http3Headers headers) {
        headers.add(":authority", "netty.quic"); // name only
        headers.add(":path", "/"); // name & value
        headers.add(":method", "GET"); // name & value with few options per name
        headers.add(":scheme", "https");
        headers.add("x-qpack-draft", "19");
    }

    private void testFrameEncodedAndDecoded(Http3Frame frame) {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(newCodec());
        EmbeddedChannel decoderChannel = new EmbeddedChannel(newCodec());

        boolean isDataFrame = frame instanceof Http3DataFrame;
        assertTrue(encoderChannel.writeOutbound(retainAndDuplicate(frame)));
        ByteBuf buffer = encoderChannel.readOutbound();
        if (fragmented) {
            do {
                ByteBuf slice = buffer.readRetainedSlice(
                        ThreadLocalRandom.current().nextInt(buffer.readableBytes() + 1));
                boolean producedData = decoderChannel.writeInbound(slice);
                if (!isDataFrame) {
                    assertEquals(!buffer.isReadable(), producedData);
                }
            } while (buffer.isReadable());
            buffer.release();
        } else {
            assertTrue(decoderChannel.writeInbound(buffer));
        }

        final Http3Frame actualFrame;
        if (isDataFrame) {
            CompositeByteBuf composite = Unpooled.compositeBuffer();
            for (;;) {
                Http3DataFrame dataFrame = decoderChannel.readInbound();
                if (dataFrame == null) {
                    break;
                }
                composite.addComponent(true, dataFrame.content());
            }
            actualFrame = new DefaultHttp3DataFrame(composite);
        } else {
            actualFrame = decoderChannel.readInbound();
        }
        Http3TestUtils.assertFrameEquals(frame, actualFrame);
        assertFalse(encoderChannel.finish());
        assertFalse(decoderChannel.finish());
    }

    private static Http3Frame retainAndDuplicate(Http3Frame frame) {
        if (frame instanceof ByteBufHolder) {
            return (Http3Frame) ((ByteBufHolder) frame).retainedDuplicate();
        }
        return frame;
    }

    @Test
    public void testMultipleFramesEncodedAndDecodedInOneBufferHeaders() {
        Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
        addRequestHeaders(headersFrame.headers());
        testMultipleFramesEncodedAndDecodedInOneBuffer(headersFrame,
                new DefaultHttp3DataFrame(Unpooled.buffer().writeLong(1)));
    }

    @Test
    public void testMultipleFramesEncodedAndDecodedInOneBufferPushPromise() {
        Http3PushPromiseFrame pushPromiseFrame = new DefaultHttp3PushPromiseFrame(9);
        addRequestHeaders(pushPromiseFrame.headers());
        testMultipleFramesEncodedAndDecodedInOneBuffer(pushPromiseFrame,
                new DefaultHttp3DataFrame(Unpooled.buffer().writeLong(1)));
    }

    private void testMultipleFramesEncodedAndDecodedInOneBuffer(Http3Frame first, Http3Frame second) {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(newCodec());
        EmbeddedChannel decoderChannel = new EmbeddedChannel(newCodec());

        assertTrue(encoderChannel.writeOutbound(retainAndDuplicate(first)));
        assertTrue(encoderChannel.writeOutbound(retainAndDuplicate(second)));

        ByteBuf mergedBuffer = Unpooled.buffer();
        for (;;) {
            ByteBuf buffer = encoderChannel.readOutbound();
            if (buffer == null) {
                break;
            }
            mergedBuffer.writeBytes(buffer);
            buffer.release();
        }
        assertTrue(decoderChannel.writeInbound(mergedBuffer));

        Http3Frame readFrame = decoderChannel.readInbound();
        Http3TestUtils.assertFrameEquals(first, readFrame);
        readFrame = decoderChannel.readInbound();
        Http3TestUtils.assertFrameEquals(second, readFrame);

        assertFalse(encoderChannel.finish());
        assertFalse(decoderChannel.finish());
    }

    @Test
    public void testInvalidHttp3MaxPushIdFrame() {
        testInvalidHttp3Frame0(HTTP3_MAX_PUSH_ID_FRAME_TYPE,
                HTTP3_CANCEL_PUSH_FRAME_MAX_LEN + 1, Http3ErrorCode.H3_FRAME_ERROR);
    }

    @Test
    public void testInvalidHttp3GoAwayFrame() {
        testInvalidHttp3Frame0(HTTP3_GO_AWAY_FRAME_TYPE,
                HTTP3_GO_AWAY_FRAME_MAX_LEN + 1, Http3ErrorCode.H3_FRAME_ERROR);
    }

    @Test
    public void testInvalidHttp3SettingsFrame() {
        testInvalidHttp3Frame0(HTTP3_SETTINGS_FRAME_TYPE,
                HTTP3_SETTINGS_FRAME_MAX_LEN + 1, Http3ErrorCode.H3_EXCESSIVE_LOAD);
    }

    @Test
    public void testInvalidHttp3CancelPushFrame() {
        testInvalidHttp3Frame0(HTTP3_CANCEL_PUSH_FRAME_TYPE,
                HTTP3_CANCEL_PUSH_FRAME_MAX_LEN + 1, Http3ErrorCode.H3_FRAME_ERROR);
    }

    @Test
    public void testInvalidHttp3HeadersFrame() {
        testInvalidHttp3Frame0(HTTP3_HEADERS_FRAME_TYPE,
                MAX_HEADER_SIZE + 1, Http3ErrorCode.H3_EXCESSIVE_LOAD);
    }

    @Test
    public void testInvalidHttp3PushPromiseFrame() {
        testInvalidHttp3Frame0(HTTP3_PUSH_PROMISE_FRAME_TYPE,
                MAX_HEADER_SIZE + 9, Http3ErrorCode.H3_EXCESSIVE_LOAD);
    }

    @Test
    public void testSkipUnknown() {
        ByteBuf buffer = Unpooled.buffer();
        writeVariableLengthInteger(buffer, 4611686018427387903L);
        writeVariableLengthInteger(buffer, 10);
        buffer.writeZero(10);

        QuicChannel parent = mockParent();

        EmbeddedChannel decoderChannel = new EmbeddedChannel(parent, DefaultChannelId.newInstance(),
                true, false, newCodec());
        assertFalse(decoderChannel.writeInbound(buffer));
        assertFalse(decoderChannel.finish());
    }

    private static void testInvalidHttp3Frame0(int type, int length, Http3ErrorCode code) {
        ByteBuf buffer = Unpooled.buffer();
        writeVariableLengthInteger(buffer, type);
        writeVariableLengthInteger(buffer, length);

        QuicChannel parent = mockParent();

        EmbeddedChannel decoderChannel = new EmbeddedChannel(parent, DefaultChannelId.newInstance(),
                true, false, newCodec());
        try {
            decoderChannel.writeInbound(buffer);
        } catch (Exception e) {
            assertException(code, e);
        }
        verifyClose(code, parent);
        assertFalse(decoderChannel.finish());
    }

    private static Http3FrameCodec newCodec() {
        return new Http3FrameCodec(new QpackDecoder(), MAX_HEADER_SIZE,
                new QpackEncoder());
    }
}

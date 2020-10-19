/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.spdy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Random;

import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_HEADER_SIZE;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SpdyFrameDecoderTest {

    private static final Random RANDOM = new Random();

    private final SpdyFrameDecoderDelegate delegate = mock(SpdyFrameDecoderDelegate.class);
    private final TestSpdyFrameDecoderDelegate testDelegate = new TestSpdyFrameDecoderDelegate();
    private SpdyFrameDecoder decoder;

    @Before
    public void createDecoder() {
        decoder = new SpdyFrameDecoder(SpdyVersion.SPDY_3_1, testDelegate);
    }

    @After
    public void releaseBuffers() {
        testDelegate.releaseAll();
    }

    private final class TestSpdyFrameDecoderDelegate implements SpdyFrameDecoderDelegate {
        private final Queue<ByteBuf> buffers = new ArrayDeque<ByteBuf>();

        @Override
        public void readDataFrame(int streamId, boolean last, ByteBuf data) {
            delegate.readDataFrame(streamId, last, data);
            buffers.add(data);
        }

        @Override
        public void readSynStreamFrame(int streamId, int associatedToStreamId,
        byte priority, boolean last, boolean unidirectional) {
            delegate.readSynStreamFrame(streamId, associatedToStreamId, priority, last, unidirectional);
        }

        @Override
        public void readSynReplyFrame(int streamId, boolean last) {
            delegate.readSynReplyFrame(streamId, last);
        }

        @Override
        public void readRstStreamFrame(int streamId, int statusCode) {
            delegate.readRstStreamFrame(streamId, statusCode);
        }

        @Override
        public void readSettingsFrame(boolean clearPersisted) {
            delegate.readSettingsFrame(clearPersisted);
        }

        @Override
        public void readSetting(int id, int value, boolean persistValue, boolean persisted) {
            delegate.readSetting(id, value, persistValue, persisted);
        }

        @Override
        public void readSettingsEnd() {
            delegate.readSettingsEnd();
        }

        @Override
        public void readPingFrame(int id) {
            delegate.readPingFrame(id);
        }

        @Override
        public void readGoAwayFrame(int lastGoodStreamId, int statusCode) {
            delegate.readGoAwayFrame(lastGoodStreamId, statusCode);
        }

        @Override
        public void readHeadersFrame(int streamId, boolean last) {
            delegate.readHeadersFrame(streamId, last);
        }

        @Override
        public void readWindowUpdateFrame(int streamId, int deltaWindowSize) {
            delegate.readWindowUpdateFrame(streamId, deltaWindowSize);
        }

        @Override
        public void readHeaderBlock(ByteBuf headerBlock) {
            delegate.readHeaderBlock(headerBlock);
            buffers.add(headerBlock);
        }

        @Override
        public void readHeaderBlockEnd() {
            delegate.readHeaderBlockEnd();
        }

        @Override
        public void readFrameError(String message) {
            delegate.readFrameError(message);
        }

        void releaseAll() {
            for (;;) {
                ByteBuf buf = buffers.poll();
                if (buf == null) {
                    return;
                }
                buf.release();
            }
        }
    }

    private static void encodeDataFrameHeader(ByteBuf buffer, int streamId, byte flags, int length) {
        buffer.writeInt(streamId & 0x7FFFFFFF);
        buffer.writeByte(flags);
        buffer.writeMedium(length);
    }

    private static void encodeControlFrameHeader(ByteBuf buffer, short type, byte flags, int length) {
        buffer.writeShort(0x8000 | SpdyVersion.SPDY_3_1.getVersion());
        buffer.writeShort(type);
        buffer.writeByte(flags);
        buffer.writeMedium(length);
    }

    @Test
    public void testSpdyDataFrame() throws Exception {
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        byte flags = 0;
        int length = 1024;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeDataFrameHeader(buf, streamId, flags, length);
        for (int i = 0; i < 256; i ++) {
            buf.writeInt(RANDOM.nextInt());
        }
        decoder.decode(buf);
        verify(delegate).readDataFrame(streamId, false, buf.slice(SPDY_HEADER_SIZE, length));
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testEmptySpdyDataFrame() throws Exception {
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        byte flags = 0;
        int length = 0;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeDataFrameHeader(buf, streamId, flags, length);

        decoder.decode(buf);
        verify(delegate).readDataFrame(streamId, false, Unpooled.EMPTY_BUFFER);
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testLastSpdyDataFrame() throws Exception {
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        byte flags = 0x01; // FLAG_FIN
        int length = 0;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeDataFrameHeader(buf, streamId, flags, length);

        decoder.decode(buf);
        verify(delegate).readDataFrame(streamId, true, Unpooled.EMPTY_BUFFER);
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testUnknownSpdyDataFrameFlags() throws Exception {
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        byte flags = (byte) 0xFE; // should ignore any unknown flags
        int length = 0;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeDataFrameHeader(buf, streamId, flags, length);

        decoder.decode(buf);
        verify(delegate).readDataFrame(streamId, false, Unpooled.EMPTY_BUFFER);
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testIllegalSpdyDataFrameStreamId() throws Exception {
        int streamId = 0; // illegal stream identifier
        byte flags = 0;
        int length = 0;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeDataFrameHeader(buf, streamId, flags, length);

        decoder.decode(buf);
        verify(delegate).readFrameError((String) any());
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testPipelinedSpdyDataFrames() throws Exception {
        int streamId1 = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int streamId2 = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        byte flags = 0;
        int length = 0;

        ByteBuf buf = Unpooled.buffer(2 * (SPDY_HEADER_SIZE + length));
        encodeDataFrameHeader(buf, streamId1, flags, length);
        encodeDataFrameHeader(buf, streamId2, flags, length);

        decoder.decode(buf);
        verify(delegate).readDataFrame(streamId1, false, Unpooled.EMPTY_BUFFER);
        verify(delegate).readDataFrame(streamId2, false, Unpooled.EMPTY_BUFFER);
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testSpdySynStreamFrame() throws Exception {
        short type = 1;
        byte flags = 0;
        int length = 10;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int associatedToStreamId = RANDOM.nextInt() & 0x7FFFFFFF;
        byte priority = (byte) (RANDOM.nextInt() & 0x07);

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(associatedToStreamId);
        buf.writeByte(priority << 5);
        buf.writeByte(0);

        decoder.decode(buf);
        verify(delegate).readSynStreamFrame(streamId, associatedToStreamId, priority, false, false);
        verify(delegate).readHeaderBlockEnd();
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testLastSpdySynStreamFrame() throws Exception {
        short type = 1;
        byte flags = 0x01; // FLAG_FIN
        int length = 10;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int associatedToStreamId = RANDOM.nextInt() & 0x7FFFFFFF;
        byte priority = (byte) (RANDOM.nextInt() & 0x07);

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(associatedToStreamId);
        buf.writeByte(priority << 5);
        buf.writeByte(0);

        decoder.decode(buf);
        verify(delegate).readSynStreamFrame(streamId, associatedToStreamId, priority, true, false);
        verify(delegate).readHeaderBlockEnd();
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testUnidirectionalSpdySynStreamFrame() throws Exception {
        short type = 1;
        byte flags = 0x02; // FLAG_UNIDIRECTIONAL
        int length = 10;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int associatedToStreamId = RANDOM.nextInt() & 0x7FFFFFFF;
        byte priority = (byte) (RANDOM.nextInt() & 0x07);

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(associatedToStreamId);
        buf.writeByte(priority << 5);
        buf.writeByte(0);

        decoder.decode(buf);
        verify(delegate).readSynStreamFrame(streamId, associatedToStreamId, priority, false, true);
        verify(delegate).readHeaderBlockEnd();
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testIndependentSpdySynStreamFrame() throws Exception {
        short type = 1;
        byte flags = 0;
        int length = 10;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int associatedToStreamId = 0; // independent of all other streams
        byte priority = (byte) (RANDOM.nextInt() & 0x07);

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(associatedToStreamId);
        buf.writeByte(priority << 5);
        buf.writeByte(0);

        decoder.decode(buf);
        verify(delegate).readSynStreamFrame(streamId, associatedToStreamId, priority, false, false);
        verify(delegate).readHeaderBlockEnd();
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testUnknownSpdySynStreamFrameFlags() throws Exception {
        short type = 1;
        byte flags = (byte) 0xFC; // undefined flags
        int length = 10;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int associatedToStreamId = RANDOM.nextInt() & 0x7FFFFFFF;
        byte priority = (byte) (RANDOM.nextInt() & 0x07);

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(associatedToStreamId);
        buf.writeByte(priority << 5);
        buf.writeByte(0);

        decoder.decode(buf);
        verify(delegate).readSynStreamFrame(streamId, associatedToStreamId, priority, false, false);
        verify(delegate).readHeaderBlockEnd();
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testReservedSpdySynStreamFrameBits() throws Exception {
        short type = 1;
        byte flags = 0;
        int length = 10;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int associatedToStreamId = RANDOM.nextInt() & 0x7FFFFFFF;
        byte priority = (byte) (RANDOM.nextInt() & 0x07);

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId | 0x80000000); // should ignore reserved bit
        buf.writeInt(associatedToStreamId | 0x80000000); // should ignore reserved bit
        buf.writeByte(priority << 5 | 0x1F); // should ignore reserved bits
        buf.writeByte(0xFF); // should ignore reserved bits

        decoder.decode(buf);
        verify(delegate).readSynStreamFrame(streamId, associatedToStreamId, priority, false, false);
        verify(delegate).readHeaderBlockEnd();
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testInvalidSpdySynStreamFrameLength() throws Exception {
        short type = 1;
        byte flags = 0;
        int length = 8; // invalid length
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int associatedToStreamId = RANDOM.nextInt() & 0x7FFFFFFF;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(associatedToStreamId);

        decoder.decode(buf);
        verify(delegate).readFrameError(anyString());
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testIllegalSpdySynStreamFrameStreamId() throws Exception {
        short type = 1;
        byte flags = 0;
        int length = 10;
        int streamId = 0; // invalid stream identifier
        int associatedToStreamId = RANDOM.nextInt() & 0x7FFFFFFF;
        byte priority = (byte) (RANDOM.nextInt() & 0x07);

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(associatedToStreamId);
        buf.writeByte(priority << 5);
        buf.writeByte(0);

        decoder.decode(buf);
        verify(delegate).readFrameError(anyString());
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testSpdySynStreamFrameHeaderBlock() throws Exception {
        short type = 1;
        byte flags = 0;
        int length = 10;
        int headerBlockLength = 1024;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int associatedToStreamId = RANDOM.nextInt() & 0x7FFFFFFF;
        byte priority = (byte) (RANDOM.nextInt() & 0x07);

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length + headerBlockLength);
        encodeControlFrameHeader(buf, type, flags, length + headerBlockLength);
        buf.writeInt(streamId);
        buf.writeInt(associatedToStreamId);
        buf.writeByte(priority << 5);
        buf.writeByte(0);

        ByteBuf headerBlock = Unpooled.buffer(headerBlockLength);
        for (int i = 0; i < 256; i ++) {
            headerBlock.writeInt(RANDOM.nextInt());
        }

        decoder.decode(buf);
        decoder.decode(headerBlock);
        verify(delegate).readSynStreamFrame(streamId, associatedToStreamId, priority, false, false);
        verify(delegate).readHeaderBlock(headerBlock.slice(0, headerBlock.writerIndex()));
        verify(delegate).readHeaderBlockEnd();
        assertFalse(buf.isReadable());
        assertFalse(headerBlock.isReadable());
        buf.release();
        headerBlock.release();
    }

    @Test
    public void testSpdySynReplyFrame() throws Exception {
        short type = 2;
        byte flags = 0;
        int length = 4;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);

        decoder.decode(buf);
        verify(delegate).readSynReplyFrame(streamId, false);
        verify(delegate).readHeaderBlockEnd();
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testLastSpdySynReplyFrame() throws Exception {
        short type = 2;
        byte flags = 0x01; // FLAG_FIN
        int length = 4;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);

        decoder.decode(buf);
        verify(delegate).readSynReplyFrame(streamId, true);
        verify(delegate).readHeaderBlockEnd();
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testUnknownSpdySynReplyFrameFlags() throws Exception {
        short type = 2;
        byte flags = (byte) 0xFE; // undefined flags
        int length = 4;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);

        decoder.decode(buf);
        verify(delegate).readSynReplyFrame(streamId, false);
        verify(delegate).readHeaderBlockEnd();
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testReservedSpdySynReplyFrameBits() throws Exception {
        short type = 2;
        byte flags = 0;
        int length = 4;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId | 0x80000000); // should ignore reserved bit

        decoder.decode(buf);
        verify(delegate).readSynReplyFrame(streamId, false);
        verify(delegate).readHeaderBlockEnd();

        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testInvalidSpdySynReplyFrameLength() throws Exception {
        short type = 2;
        byte flags = 0;
        int length = 0; // invalid length

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);

        decoder.decode(buf);
        verify(delegate).readFrameError(anyString());
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testIllegalSpdySynReplyFrameStreamId() throws Exception {
        short type = 2;
        byte flags = 0;
        int length = 4;
        int streamId = 0; // invalid stream identifier

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);

        decoder.decode(buf);
        verify(delegate).readFrameError(anyString());
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testSpdySynReplyFrameHeaderBlock() throws Exception {
        short type = 2;
        byte flags = 0;
        int length = 4;
        int headerBlockLength = 1024;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length + headerBlockLength);
        encodeControlFrameHeader(buf, type, flags, length + headerBlockLength);
        buf.writeInt(streamId);

        ByteBuf headerBlock = Unpooled.buffer(headerBlockLength);
        for (int i = 0; i < 256; i ++) {
            headerBlock.writeInt(RANDOM.nextInt());
        }

        decoder.decode(buf);
        decoder.decode(headerBlock);
        verify(delegate).readSynReplyFrame(streamId, false);
        verify(delegate).readHeaderBlock(headerBlock.slice(0, headerBlock.writerIndex()));
        verify(delegate).readHeaderBlockEnd();
        assertFalse(buf.isReadable());
        assertFalse(headerBlock.isReadable());
        buf.release();
        headerBlock.release();
    }

    @Test
    public void testSpdyRstStreamFrame() throws Exception {
        short type = 3;
        byte flags = 0;
        int length = 8;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int statusCode = RANDOM.nextInt() | 0x01;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(statusCode);

        decoder.decode(buf);
        verify(delegate).readRstStreamFrame(streamId, statusCode);
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testReservedSpdyRstStreamFrameBits() throws Exception {
        short type = 3;
        byte flags = 0;
        int length = 8;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int statusCode = RANDOM.nextInt() | 0x01;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId | 0x80000000); // should ignore reserved bit
        buf.writeInt(statusCode);

        decoder.decode(buf);
        verify(delegate).readRstStreamFrame(streamId, statusCode);
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testInvalidSpdyRstStreamFrameFlags() throws Exception {
        short type = 3;
        byte flags = (byte) 0xFF; // invalid flags
        int length = 8;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int statusCode = RANDOM.nextInt() | 0x01;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(statusCode);

        decoder.decode(buf);
        verify(delegate).readFrameError(anyString());
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testInvalidSpdyRstStreamFrameLength() throws Exception {
        short type = 3;
        byte flags = 0;
        int length = 12; // invalid length
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int statusCode = RANDOM.nextInt() | 0x01;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(statusCode);

        decoder.decode(buf);
        verify(delegate).readFrameError(anyString());
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testIllegalSpdyRstStreamFrameStreamId() throws Exception {
        short type = 3;
        byte flags = 0;
        int length = 8;
        int streamId = 0; // invalid stream identifier
        int statusCode = RANDOM.nextInt() | 0x01;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(statusCode);

        decoder.decode(buf);
        verify(delegate).readFrameError(anyString());
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testIllegalSpdyRstStreamFrameStatusCode() throws Exception {
        short type = 3;
        byte flags = 0;
        int length = 8;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int statusCode = 0; // invalid status code

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(statusCode);

        decoder.decode(buf);
        verify(delegate).readFrameError(anyString());
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testSpdySettingsFrame() throws Exception {
        short type = 4;
        byte flags = 0;
        int numSettings = 2;
        int length = 8 * numSettings + 4;
        byte idFlags = 0;
        int id = RANDOM.nextInt() & 0x00FFFFFF;
        int value = RANDOM.nextInt();

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(numSettings);
        for (int i = 0; i < numSettings; i++) {
            buf.writeByte(idFlags);
            buf.writeMedium(id);
            buf.writeInt(value);
        }

        delegate.readSettingsEnd();
        decoder.decode(buf);
        verify(delegate).readSettingsFrame(false);
        verify(delegate, times(numSettings)).readSetting(id, value, false, false);
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testEmptySpdySettingsFrame() throws Exception {
        short type = 4;
        byte flags = 0;
        int numSettings = 0;
        int length = 8 * numSettings + 4;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(numSettings);

        decoder.decode(buf);
        verify(delegate).readSettingsFrame(false);
        verify(delegate).readSettingsEnd();
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testSpdySettingsFrameClearFlag() throws Exception {
        short type = 4;
        byte flags = 0x01; // FLAG_SETTINGS_CLEAR_SETTINGS
        int numSettings = 0;
        int length = 8 * numSettings + 4;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(numSettings);

        decoder.decode(buf);
        verify(delegate).readSettingsFrame(true);
        verify(delegate).readSettingsEnd();
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testSpdySettingsPersistValues() throws Exception {
        short type = 4;
        byte flags = 0;
        int numSettings = 1;
        int length = 8 * numSettings + 4;
        byte idFlags = 0x01; // FLAG_SETTINGS_PERSIST_VALUE
        int id = RANDOM.nextInt() & 0x00FFFFFF;
        int value = RANDOM.nextInt();

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(numSettings);
        for (int i = 0; i < numSettings; i++) {
            buf.writeByte(idFlags);
            buf.writeMedium(id);
            buf.writeInt(value);
        }

        delegate.readSettingsEnd();
        decoder.decode(buf);
        verify(delegate).readSettingsFrame(false);
        verify(delegate, times(numSettings)).readSetting(id, value, true, false);
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testSpdySettingsPersistedValues() throws Exception {
        short type = 4;
        byte flags = 0;
        int numSettings = 1;
        int length = 8 * numSettings + 4;
        byte idFlags = 0x02; // FLAG_SETTINGS_PERSISTED
        int id = RANDOM.nextInt() & 0x00FFFFFF;
        int value = RANDOM.nextInt();

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(numSettings);
        for (int i = 0; i < numSettings; i++) {
            buf.writeByte(idFlags);
            buf.writeMedium(id);
            buf.writeInt(value);
        }

        delegate.readSettingsEnd();
        decoder.decode(buf);
        verify(delegate).readSettingsFrame(false);
        verify(delegate, times(numSettings)).readSetting(id, value, false, true);
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testUnknownSpdySettingsFrameFlags() throws Exception {
        short type = 4;
        byte flags = (byte) 0xFE; // undefined flags
        int numSettings = 0;
        int length = 8 * numSettings + 4;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(numSettings);

        decoder.decode(buf);
        verify(delegate).readSettingsFrame(false);
        verify(delegate).readSettingsEnd();
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testUnknownSpdySettingsFlags() throws Exception {
        short type = 4;
        byte flags = 0;
        int numSettings = 1;
        int length = 8 * numSettings + 4;
        byte idFlags = (byte) 0xFC; // undefined flags
        int id = RANDOM.nextInt() & 0x00FFFFFF;
        int value = RANDOM.nextInt();

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(numSettings);
        for (int i = 0; i < numSettings; i++) {
            buf.writeByte(idFlags);
            buf.writeMedium(id);
            buf.writeInt(value);
        }

        delegate.readSettingsEnd();
        decoder.decode(buf);
        verify(delegate).readSettingsFrame(false);
        verify(delegate, times(numSettings)).readSetting(id, value, false, false);
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testInvalidSpdySettingsFrameLength() throws Exception {
        short type = 4;
        byte flags = 0;
        int numSettings = 2;
        int length = 8 * numSettings + 8; // invalid length
        byte idFlags = 0;
        int id = RANDOM.nextInt() & 0x00FFFFFF;
        int value = RANDOM.nextInt();

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(numSettings);
        for (int i = 0; i < numSettings; i++) {
            buf.writeByte(idFlags);
            buf.writeMedium(id);
            buf.writeInt(value);
        }

        decoder.decode(buf);
        verify(delegate).readFrameError(anyString());
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testInvalidSpdySettingsFrameNumSettings() throws Exception {
        short type = 4;
        byte flags = 0;
        int numSettings = 2;
        int length = 8 * numSettings + 4;
        byte idFlags = 0;
        int id = RANDOM.nextInt() & 0x00FFFFFF;
        int value = RANDOM.nextInt();

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(0); // invalid num_settings
        for (int i = 0; i < numSettings; i++) {
            buf.writeByte(idFlags);
            buf.writeMedium(id);
            buf.writeInt(value);
        }

        decoder.decode(buf);
        verify(delegate).readFrameError(anyString());
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testDiscardUnknownFrame() throws Exception {
        short type = 5;
        byte flags = (byte) 0xFF;
        int length = 8;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeLong(RANDOM.nextLong());

        decoder.decode(buf);
        verifyZeroInteractions(delegate);
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testDiscardUnknownEmptyFrame() throws Exception {
        short type = 5;
        byte flags = (byte) 0xFF;
        int length = 0;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);

        decoder.decode(buf);
        verifyZeroInteractions(delegate);
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testProgressivelyDiscardUnknownEmptyFrame() throws Exception {
        short type = 5;
        byte flags = (byte) 0xFF;
        int segment = 4;
        int length = 2 * segment;

        ByteBuf header = Unpooled.buffer(SPDY_HEADER_SIZE);
        ByteBuf segment1 = Unpooled.buffer(segment);
        ByteBuf segment2 = Unpooled.buffer(segment);
        encodeControlFrameHeader(header, type, flags, length);
        segment1.writeInt(RANDOM.nextInt());
        segment2.writeInt(RANDOM.nextInt());

        decoder.decode(header);
        decoder.decode(segment1);
        decoder.decode(segment2);
        verifyZeroInteractions(delegate);
        assertFalse(header.isReadable());
        assertFalse(segment1.isReadable());
        assertFalse(segment2.isReadable());
        header.release();
        segment1.release();
        segment2.release();
    }

    @Test
    public void testSpdyPingFrame() throws Exception {
        short type = 6;
        byte flags = 0;
        int length = 4;
        int id = RANDOM.nextInt();

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(id);

        decoder.decode(buf);
        verify(delegate).readPingFrame(id);
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testUnknownSpdyPingFrameFlags() throws Exception {
        short type = 6;
        byte flags = (byte) 0xFF; // undefined flags
        int length = 4;
        int id = RANDOM.nextInt();

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(id);

        decoder.decode(buf);
        verify(delegate).readPingFrame(id);
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testInvalidSpdyPingFrameLength() throws Exception {
        short type = 6;
        byte flags = 0;
        int length = 8; // invalid length
        int id = RANDOM.nextInt();

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(id);

        decoder.decode(buf);
        verify(delegate).readFrameError(anyString());
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testSpdyGoAwayFrame() throws Exception {
        short type = 7;
        byte flags = 0;
        int length = 8;
        int lastGoodStreamId = RANDOM.nextInt() & 0x7FFFFFFF;
        int statusCode = RANDOM.nextInt() | 0x01;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(lastGoodStreamId);
        buf.writeInt(statusCode);

        decoder.decode(buf);
        verify(delegate).readGoAwayFrame(lastGoodStreamId, statusCode);
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testUnknownSpdyGoAwayFrameFlags() throws Exception {
        short type = 7;
        byte flags = (byte) 0xFF; // undefined flags
        int length = 8;
        int lastGoodStreamId = RANDOM.nextInt() & 0x7FFFFFFF;
        int statusCode = RANDOM.nextInt() | 0x01;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(lastGoodStreamId);
        buf.writeInt(statusCode);

        decoder.decode(buf);
        verify(delegate).readGoAwayFrame(lastGoodStreamId, statusCode);
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testReservedSpdyGoAwayFrameBits() throws Exception {
        short type = 7;
        byte flags = 0;
        int length = 8;
        int lastGoodStreamId = RANDOM.nextInt() & 0x7FFFFFFF;
        int statusCode = RANDOM.nextInt() | 0x01;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(lastGoodStreamId | 0x80000000); // should ignore reserved bit
        buf.writeInt(statusCode);

        decoder.decode(buf);
        verify(delegate).readGoAwayFrame(lastGoodStreamId, statusCode);
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testInvalidSpdyGoAwayFrameLength() throws Exception {
        short type = 7;
        byte flags = 0;
        int length = 12; // invalid length
        int lastGoodStreamId = RANDOM.nextInt() & 0x7FFFFFFF;
        int statusCode = RANDOM.nextInt() | 0x01;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(lastGoodStreamId);
        buf.writeInt(statusCode);

        decoder.decode(buf);
        verify(delegate).readFrameError(anyString());
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testSpdyHeadersFrame() throws Exception {
        short type = 8;
        byte flags = 0;
        int length = 4;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);

        decoder.decode(buf);
        verify(delegate).readHeadersFrame(streamId, false);
        verify(delegate).readHeaderBlockEnd();
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testLastSpdyHeadersFrame() throws Exception {
        short type = 8;
        byte flags = 0x01; // FLAG_FIN
        int length = 4;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);

        decoder.decode(buf);
        verify(delegate).readHeadersFrame(streamId, true);
        verify(delegate).readHeaderBlockEnd();
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testUnknownSpdyHeadersFrameFlags() throws Exception {
        short type = 8;
        byte flags = (byte) 0xFE; // undefined flags
        int length = 4;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);

        decoder.decode(buf);
        verify(delegate).readHeadersFrame(streamId, false);
        verify(delegate).readHeaderBlockEnd();
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testReservedSpdyHeadersFrameBits() throws Exception {
        short type = 8;
        byte flags = 0;
        int length = 4;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId | 0x80000000); // should ignore reserved bit

        decoder.decode(buf);
        verify(delegate).readHeadersFrame(streamId, false);
        verify(delegate).readHeaderBlockEnd();
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testInvalidSpdyHeadersFrameLength() throws Exception {
        short type = 8;
        byte flags = 0;
        int length = 0; // invalid length

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);

        decoder.decode(buf);
        verify(delegate).readFrameError(anyString());
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testInvalidSpdyHeadersFrameStreamId() throws Exception {
        short type = 8;
        byte flags = 0;
        int length = 4;
        int streamId = 0; // invalid stream identifier

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);

        decoder.decode(buf);
        verify(delegate).readFrameError(anyString());
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testSpdyHeadersFrameHeaderBlock() throws Exception {
        short type = 8;
        byte flags = 0;
        int length = 4;
        int headerBlockLength = 1024;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length + headerBlockLength);
        buf.writeInt(streamId);

        ByteBuf headerBlock = Unpooled.buffer(headerBlockLength);
        for (int i = 0; i < 256; i ++) {
            headerBlock.writeInt(RANDOM.nextInt());
        }
        decoder.decode(buf);
        decoder.decode(headerBlock);
        verify(delegate).readHeadersFrame(streamId, false);
        verify(delegate).readHeaderBlock(headerBlock.slice(0, headerBlock.writerIndex()));
        verify(delegate).readHeaderBlockEnd();
        assertFalse(buf.isReadable());
        assertFalse(headerBlock.isReadable());
        buf.release();
        headerBlock.release();
    }

    @Test
    public void testSpdyWindowUpdateFrame() throws Exception {
        short type = 9;
        byte flags = 0;
        int length = 8;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF;
        int deltaWindowSize = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(deltaWindowSize);

        decoder.decode(buf);
        verify(delegate).readWindowUpdateFrame(streamId, deltaWindowSize);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testUnknownSpdyWindowUpdateFrameFlags() throws Exception {
        short type = 9;
        byte flags = (byte) 0xFF; // undefined flags
        int length = 8;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF;
        int deltaWindowSize = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(deltaWindowSize);

        decoder.decode(buf);
        verify(delegate).readWindowUpdateFrame(streamId, deltaWindowSize);
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testReservedSpdyWindowUpdateFrameBits() throws Exception {
        short type = 9;
        byte flags = 0;
        int length = 8;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF;
        int deltaWindowSize = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId | 0x80000000); // should ignore reserved bit
        buf.writeInt(deltaWindowSize | 0x80000000); // should ignore reserved bit

        decoder.decode(buf);
        verify(delegate).readWindowUpdateFrame(streamId, deltaWindowSize);
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testInvalidSpdyWindowUpdateFrameLength() throws Exception {
        short type = 9;
        byte flags = 0;
        int length = 12; // invalid length
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF;
        int deltaWindowSize = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(deltaWindowSize);

        decoder.decode(buf);
        verify(delegate).readFrameError(anyString());
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    public void testIllegalSpdyWindowUpdateFrameDeltaWindowSize() throws Exception {
        short type = 9;
        byte flags = 0;
        int length = 8;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF;
        int deltaWindowSize = 0; // invalid delta window size

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(deltaWindowSize);

        decoder.decode(buf);
        verify(delegate).readFrameError(anyString());
        assertFalse(buf.isReadable());
        buf.release();
    }
}

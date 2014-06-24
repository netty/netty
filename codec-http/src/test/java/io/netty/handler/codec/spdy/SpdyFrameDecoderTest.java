/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import io.netty.util.ReferenceCountUtil;
import org.junit.Before;
import org.junit.Test;

import java.util.Random;

import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_HEADER_SIZE;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.*;

public class SpdyFrameDecoderTest {

    private static final Random RANDOM = new Random();

    private final SpdyFrameDecoderDelegate delegate = createStrictMock(SpdyFrameDecoderDelegate.class);
    private SpdyFrameDecoder decoder;

    @Before
    public void createDecoder() {
        decoder = new SpdyFrameDecoder(SpdyVersion.SPDY_3_1, new SpdyFrameDecoderDelegate() {
            @Override
            public void readDataFrame(int streamId, boolean last, ByteBuf data) {
                try {
                    delegate.readDataFrame(streamId, last, data);
                } finally {
                    // release the data after we delegate it and so checked it.
                    data.release();
                }
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
                try {
                    delegate.readHeaderBlock(headerBlock);
                } finally {
                    // release the data after we delegate it and so checked it.
                    headerBlock.release();
                }
            }

            @Override
            public void readHeaderBlockEnd() {
                delegate.readHeaderBlockEnd();
            }

            @Override
            public void readFrameError(String message) {
                delegate.readFrameError(message);
            }
        });
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

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeDataFrameHeader(buf, streamId, flags, length);
        for (int i = 0; i < 256; i ++) {
            buf.writeInt(RANDOM.nextInt());
        }
        delegate.readDataFrame(streamId, false, buf.slice(SPDY_HEADER_SIZE, length));
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testEmptySpdyDataFrame() throws Exception {
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        byte flags = 0;
        int length = 0;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeDataFrameHeader(buf, streamId, flags, length);

        delegate.readDataFrame(streamId, false, Unpooled.EMPTY_BUFFER);
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testLastSpdyDataFrame() throws Exception {
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        byte flags = 0x01; // FLAG_FIN
        int length = 0;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeDataFrameHeader(buf, streamId, flags, length);

        delegate.readDataFrame(streamId, true, Unpooled.EMPTY_BUFFER);
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testUnknownSpdyDataFrameFlags() throws Exception {
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        byte flags = (byte) 0xFE; // should ignore any unknown flags
        int length = 0;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeDataFrameHeader(buf, streamId, flags, length);

        delegate.readDataFrame(streamId, false, Unpooled.EMPTY_BUFFER);
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testIllegalSpdyDataFrameStreamId() throws Exception {
        int streamId = 0; // illegal stream identifier
        byte flags = 0;
        int length = 0;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeDataFrameHeader(buf, streamId, flags, length);

        delegate.readFrameError((String) anyObject());
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testPipelinedSpdyDataFrames() throws Exception {
        int streamId1 = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int streamId2 = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        byte flags = 0;
        int length = 0;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(2 * (SPDY_HEADER_SIZE + length)));
        encodeDataFrameHeader(buf, streamId1, flags, length);
        encodeDataFrameHeader(buf, streamId2, flags, length);

        delegate.readDataFrame(streamId1, false, Unpooled.EMPTY_BUFFER);
        delegate.readDataFrame(streamId2, false, Unpooled.EMPTY_BUFFER);
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testSpdySynStreamFrame() throws Exception {
        short type = 1;
        byte flags = 0;
        int length = 10;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int associatedToStreamId = RANDOM.nextInt() & 0x7FFFFFFF;
        byte priority = (byte) (RANDOM.nextInt() & 0x07);

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(associatedToStreamId);
        buf.writeByte(priority << 5);
        buf.writeByte(0);

        delegate.readSynStreamFrame(streamId, associatedToStreamId, priority, false, false);
        delegate.readHeaderBlockEnd();
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testLastSpdySynStreamFrame() throws Exception {
        short type = 1;
        byte flags = 0x01; // FLAG_FIN
        int length = 10;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int associatedToStreamId = RANDOM.nextInt() & 0x7FFFFFFF;
        byte priority = (byte) (RANDOM.nextInt() & 0x07);

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(associatedToStreamId);
        buf.writeByte(priority << 5);
        buf.writeByte(0);

        delegate.readSynStreamFrame(streamId, associatedToStreamId, priority, true, false);
        delegate.readHeaderBlockEnd();
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testUnidirectionalSpdySynStreamFrame() throws Exception {
        short type = 1;
        byte flags = 0x02; // FLAG_UNIDIRECTIONAL
        int length = 10;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int associatedToStreamId = RANDOM.nextInt() & 0x7FFFFFFF;
        byte priority = (byte) (RANDOM.nextInt() & 0x07);

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(associatedToStreamId);
        buf.writeByte(priority << 5);
        buf.writeByte(0);

        delegate.readSynStreamFrame(streamId, associatedToStreamId, priority, false, true);
        delegate.readHeaderBlockEnd();
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testIndependentSpdySynStreamFrame() throws Exception {
        short type = 1;
        byte flags = 0;
        int length = 10;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int associatedToStreamId = 0; // independent of all other streams
        byte priority = (byte) (RANDOM.nextInt() & 0x07);

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(associatedToStreamId);
        buf.writeByte(priority << 5);
        buf.writeByte(0);

        delegate.readSynStreamFrame(streamId, associatedToStreamId, priority, false, false);
        delegate.readHeaderBlockEnd();
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testUnknownSpdySynStreamFrameFlags() throws Exception {
        short type = 1;
        byte flags = (byte) 0xFC; // undefined flags
        int length = 10;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int associatedToStreamId = RANDOM.nextInt() & 0x7FFFFFFF;
        byte priority = (byte) (RANDOM.nextInt() & 0x07);

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(associatedToStreamId);
        buf.writeByte(priority << 5);
        buf.writeByte(0);

        delegate.readSynStreamFrame(streamId, associatedToStreamId, priority, false, false);
        delegate.readHeaderBlockEnd();
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testReservedSpdySynStreamFrameBits() throws Exception {
        short type = 1;
        byte flags = 0;
        int length = 10;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int associatedToStreamId = RANDOM.nextInt() & 0x7FFFFFFF;
        byte priority = (byte) (RANDOM.nextInt() & 0x07);

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId | 0x80000000); // should ignore reserved bit
        buf.writeInt(associatedToStreamId | 0x80000000); // should ignore reserved bit
        buf.writeByte(priority << 5 | 0x1F); // should ignore reserved bits
        buf.writeByte(0xFF); // should ignore reserved bits

        delegate.readSynStreamFrame(streamId, associatedToStreamId, priority, false, false);
        delegate.readHeaderBlockEnd();
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testInvalidSpdySynStreamFrameLength() throws Exception {
        short type = 1;
        byte flags = 0;
        int length = 8; // invalid length
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int associatedToStreamId = RANDOM.nextInt() & 0x7FFFFFFF;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(associatedToStreamId);

        delegate.readFrameError((String) anyObject());
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testIllegalSpdySynStreamFrameStreamId() throws Exception {
        short type = 1;
        byte flags = 0;
        int length = 10;
        int streamId = 0; // invalid stream identifier
        int associatedToStreamId = RANDOM.nextInt() & 0x7FFFFFFF;
        byte priority = (byte) (RANDOM.nextInt() & 0x07);

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(associatedToStreamId);
        buf.writeByte(priority << 5);
        buf.writeByte(0);

        delegate.readFrameError((String) anyObject());
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
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

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length + headerBlockLength));
        encodeControlFrameHeader(buf, type, flags, length + headerBlockLength);
        buf.writeInt(streamId);
        buf.writeInt(associatedToStreamId);
        buf.writeByte(priority << 5);
        buf.writeByte(0);

        ByteBuf headerBlock = ReferenceCountUtil.releaseLater(Unpooled.buffer(headerBlockLength));
        for (int i = 0; i < 256; i ++) {
            headerBlock.writeInt(RANDOM.nextInt());
        }

        delegate.readSynStreamFrame(streamId, associatedToStreamId, priority, false, false);
        delegate.readHeaderBlock(headerBlock.duplicate());
        delegate.readHeaderBlockEnd();
        replay(delegate);
        decoder.decode(buf);
        decoder.decode(headerBlock);
        verify(delegate);
        assertFalse(buf.isReadable());
        assertFalse(headerBlock.isReadable());
    }

    @Test
    public void testSpdySynReplyFrame() throws Exception {
        short type = 2;
        byte flags = 0;
        int length = 4;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);

        delegate.readSynReplyFrame(streamId, false);
        delegate.readHeaderBlockEnd();
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testLastSpdySynReplyFrame() throws Exception {
        short type = 2;
        byte flags = 0x01; // FLAG_FIN
        int length = 4;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);

        delegate.readSynReplyFrame(streamId, true);
        delegate.readHeaderBlockEnd();
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testUnknownSpdySynReplyFrameFlags() throws Exception {
        short type = 2;
        byte flags = (byte) 0xFE; // undefined flags
        int length = 4;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);

        delegate.readSynReplyFrame(streamId, false);
        delegate.readHeaderBlockEnd();
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testReservedSpdySynReplyFrameBits() throws Exception {
        short type = 2;
        byte flags = 0;
        int length = 4;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId | 0x80000000); // should ignore reserved bit

        delegate.readSynReplyFrame(streamId, false);
        delegate.readHeaderBlockEnd();
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testInvalidSpdySynReplyFrameLength() throws Exception {
        short type = 2;
        byte flags = 0;
        int length = 0; // invalid length

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);

        delegate.readFrameError((String) anyObject());
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testIllegalSpdySynReplyFrameStreamId() throws Exception {
        short type = 2;
        byte flags = 0;
        int length = 4;
        int streamId = 0; // invalid stream identifier

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);

        delegate.readFrameError((String) anyObject());
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testSpdySynReplyFrameHeaderBlock() throws Exception {
        short type = 2;
        byte flags = 0;
        int length = 4;
        int headerBlockLength = 1024;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length + headerBlockLength));
        encodeControlFrameHeader(buf, type, flags, length + headerBlockLength);
        buf.writeInt(streamId);

        ByteBuf headerBlock = Unpooled.buffer(headerBlockLength);
        for (int i = 0; i < 256; i ++) {
            headerBlock.writeInt(RANDOM.nextInt());
        }

        delegate.readSynReplyFrame(streamId, false);
        delegate.readHeaderBlock(headerBlock.duplicate());
        delegate.readHeaderBlockEnd();
        replay(delegate);
        decoder.decode(buf);
        decoder.decode(headerBlock);
        verify(delegate);
        assertFalse(buf.isReadable());
        assertFalse(headerBlock.isReadable());
    }

    @Test
    public void testSpdyRstStreamFrame() throws Exception {
        short type = 3;
        byte flags = 0;
        int length = 8;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int statusCode = RANDOM.nextInt() | 0x01;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(statusCode);

        delegate.readRstStreamFrame(streamId, statusCode);
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testReservedSpdyRstStreamFrameBits() throws Exception {
        short type = 3;
        byte flags = 0;
        int length = 8;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int statusCode = RANDOM.nextInt() | 0x01;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId | 0x80000000); // should ignore reserved bit
        buf.writeInt(statusCode);

        delegate.readRstStreamFrame(streamId, statusCode);
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testInvalidSpdyRstStreamFrameFlags() throws Exception {
        short type = 3;
        byte flags = (byte) 0xFF; // invalid flags
        int length = 8;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int statusCode = RANDOM.nextInt() | 0x01;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(statusCode);

        delegate.readFrameError((String) anyObject());
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testInvalidSpdyRstStreamFrameLength() throws Exception {
        short type = 3;
        byte flags = 0;
        int length = 12; // invalid length
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int statusCode = RANDOM.nextInt() | 0x01;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(statusCode);

        delegate.readFrameError((String) anyObject());
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testIllegalSpdyRstStreamFrameStreamId() throws Exception {
        short type = 3;
        byte flags = 0;
        int length = 8;
        int streamId = 0; // invalid stream identifier
        int statusCode = RANDOM.nextInt() | 0x01;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(statusCode);

        delegate.readFrameError((String) anyObject());
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testIllegalSpdyRstStreamFrameStatusCode() throws Exception {
        short type = 3;
        byte flags = 0;
        int length = 8;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;
        int statusCode = 0; // invalid status code

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(statusCode);

        delegate.readFrameError((String) anyObject());
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
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

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(numSettings);
        for (int i = 0; i < numSettings; i++) {
            buf.writeByte(idFlags);
            buf.writeMedium(id);
            buf.writeInt(value);
        }

        delegate.readSettingsFrame(false);
        delegate.readSetting(id, value, false, false);
        expectLastCall().times(numSettings);
        delegate.readSettingsEnd();
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testEmptySpdySettingsFrame() throws Exception {
        short type = 4;
        byte flags = 0;
        int numSettings = 0;
        int length = 8 * numSettings + 4;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(numSettings);

        delegate.readSettingsFrame(false);
        delegate.readSettingsEnd();
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testSpdySettingsFrameClearFlag() throws Exception {
        short type = 4;
        byte flags = 0x01; // FLAG_SETTINGS_CLEAR_SETTINGS
        int numSettings = 0;
        int length = 8 * numSettings + 4;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(numSettings);

        delegate.readSettingsFrame(true);
        delegate.readSettingsEnd();
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
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

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(numSettings);
        for (int i = 0; i < numSettings; i++) {
            buf.writeByte(idFlags);
            buf.writeMedium(id);
            buf.writeInt(value);
        }

        delegate.readSettingsFrame(false);
        delegate.readSetting(id, value, true, false);
        expectLastCall().times(numSettings);
        delegate.readSettingsEnd();
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
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

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(numSettings);
        for (int i = 0; i < numSettings; i++) {
            buf.writeByte(idFlags);
            buf.writeMedium(id);
            buf.writeInt(value);
        }

        delegate.readSettingsFrame(false);
        delegate.readSetting(id, value, false, true);
        expectLastCall().times(numSettings);
        delegate.readSettingsEnd();
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testUnknownSpdySettingsFrameFlags() throws Exception {
        short type = 4;
        byte flags = (byte) 0xFE; // undefined flags
        int numSettings = 0;
        int length = 8 * numSettings + 4;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(numSettings);

        delegate.readSettingsFrame(false);
        delegate.readSettingsEnd();
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
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

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(numSettings);
        for (int i = 0; i < numSettings; i++) {
            buf.writeByte(idFlags);
            buf.writeMedium(id);
            buf.writeInt(value);
        }

        delegate.readSettingsFrame(false);
        delegate.readSetting(id, value, false, false);
        expectLastCall().times(numSettings);
        delegate.readSettingsEnd();
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
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

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(numSettings);
        for (int i = 0; i < numSettings; i++) {
            buf.writeByte(idFlags);
            buf.writeMedium(id);
            buf.writeInt(value);
        }

        delegate.readFrameError((String) anyObject());
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
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

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(0); // invalid num_settings
        for (int i = 0; i < numSettings; i++) {
            buf.writeByte(idFlags);
            buf.writeMedium(id);
            buf.writeInt(value);
        }

        delegate.readFrameError((String) anyObject());
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testDiscardUnknownFrame() throws Exception {
        short type = 5;
        byte flags = (byte) 0xFF;
        int length = 8;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeLong(RANDOM.nextLong());

        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testDiscardUnknownEmptyFrame() throws Exception {
        short type = 5;
        byte flags = (byte) 0xFF;
        int length = 0;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);

        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testProgressivelyDiscardUnknownEmptyFrame() throws Exception {
        short type = 5;
        byte flags = (byte) 0xFF;
        int segment = 4;
        int length = 2 * segment;

        ByteBuf header = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE));
        ByteBuf segment1 = Unpooled.buffer(segment);
        ByteBuf segment2 = Unpooled.buffer(segment);
        encodeControlFrameHeader(header, type, flags, length);
        segment1.writeInt(RANDOM.nextInt());
        segment2.writeInt(RANDOM.nextInt());

        replay(delegate);
        decoder.decode(header);
        decoder.decode(segment1);
        decoder.decode(segment2);
        verify(delegate);
        assertFalse(header.isReadable());
        assertFalse(segment1.isReadable());
        assertFalse(segment2.isReadable());
    }

    @Test
    public void testSpdyPingFrame() throws Exception {
        short type = 6;
        byte flags = 0;
        int length = 4;
        int id = RANDOM.nextInt();

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(id);

        delegate.readPingFrame(id);
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testUnknownSpdyPingFrameFlags() throws Exception {
        short type = 6;
        byte flags = (byte) 0xFF; // undefined flags
        int length = 4;
        int id = RANDOM.nextInt();

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(id);

        delegate.readPingFrame(id);
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testInvalidSpdyPingFrameLength() throws Exception {
        short type = 6;
        byte flags = 0;
        int length = 8; // invalid length
        int id = RANDOM.nextInt();

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(id);

        delegate.readFrameError((String) anyObject());
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testSpdyGoAwayFrame() throws Exception {
        short type = 7;
        byte flags = 0;
        int length = 8;
        int lastGoodStreamId = RANDOM.nextInt() & 0x7FFFFFFF;
        int statusCode = RANDOM.nextInt() | 0x01;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(lastGoodStreamId);
        buf.writeInt(statusCode);

        delegate.readGoAwayFrame(lastGoodStreamId, statusCode);
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testUnknownSpdyGoAwayFrameFlags() throws Exception {
        short type = 7;
        byte flags = (byte) 0xFF; // undefined flags
        int length = 8;
        int lastGoodStreamId = RANDOM.nextInt() & 0x7FFFFFFF;
        int statusCode = RANDOM.nextInt() | 0x01;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(lastGoodStreamId);
        buf.writeInt(statusCode);

        delegate.readGoAwayFrame(lastGoodStreamId, statusCode);
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testReservedSpdyGoAwayFrameBits() throws Exception {
        short type = 7;
        byte flags = 0;
        int length = 8;
        int lastGoodStreamId = RANDOM.nextInt() & 0x7FFFFFFF;
        int statusCode = RANDOM.nextInt() | 0x01;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(lastGoodStreamId | 0x80000000); // should ignore reserved bit
        buf.writeInt(statusCode);

        delegate.readGoAwayFrame(lastGoodStreamId, statusCode);
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testInvalidSpdyGoAwayFrameLength() throws Exception {
        short type = 7;
        byte flags = 0;
        int length = 12; // invalid length
        int lastGoodStreamId = RANDOM.nextInt() & 0x7FFFFFFF;
        int statusCode = RANDOM.nextInt() | 0x01;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(lastGoodStreamId);
        buf.writeInt(statusCode);

        delegate.readFrameError((String) anyObject());
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testSpdyHeadersFrame() throws Exception {
        short type = 8;
        byte flags = 0;
        int length = 4;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);

        delegate.readHeadersFrame(streamId, false);
        delegate.readHeaderBlockEnd();
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testLastSpdyHeadersFrame() throws Exception {
        short type = 8;
        byte flags = 0x01; // FLAG_FIN
        int length = 4;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);

        delegate.readHeadersFrame(streamId, true);
        delegate.readHeaderBlockEnd();
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testUnknownSpdyHeadersFrameFlags() throws Exception {
        short type = 8;
        byte flags = (byte) 0xFE; // undefined flags
        int length = 4;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);

        delegate.readHeadersFrame(streamId, false);
        delegate.readHeaderBlockEnd();
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testReservedSpdyHeadersFrameBits() throws Exception {
        short type = 8;
        byte flags = 0;
        int length = 4;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId | 0x80000000); // should ignore reserved bit

        delegate.readHeadersFrame(streamId, false);
        delegate.readHeaderBlockEnd();
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testInvalidSpdyHeadersFrameLength() throws Exception {
        short type = 8;
        byte flags = 0;
        int length = 0; // invalid length

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);

        delegate.readFrameError((String) anyObject());
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testInvalidSpdyHeadersFrameStreamId() throws Exception {
        short type = 8;
        byte flags = 0;
        int length = 4;
        int streamId = 0; // invalid stream identifier

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);

        delegate.readFrameError((String) anyObject());
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testSpdyHeadersFrameHeaderBlock() throws Exception {
        short type = 8;
        byte flags = 0;
        int length = 4;
        int headerBlockLength = 1024;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length + headerBlockLength);
        buf.writeInt(streamId);

        ByteBuf headerBlock = ReferenceCountUtil.releaseLater(Unpooled.buffer(headerBlockLength));
        for (int i = 0; i < 256; i ++) {
            headerBlock.writeInt(RANDOM.nextInt());
        }

        delegate.readHeadersFrame(streamId, false);
        delegate.readHeaderBlock(headerBlock.duplicate());
        delegate.readHeaderBlockEnd();
        replay(delegate);
        decoder.decode(buf);
        decoder.decode(headerBlock);
        verify(delegate);
        assertFalse(buf.isReadable());
        assertFalse(headerBlock.isReadable());
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

        delegate.readWindowUpdateFrame(streamId, deltaWindowSize);
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testUnknownSpdyWindowUpdateFrameFlags() throws Exception {
        short type = 9;
        byte flags = (byte) 0xFF; // undefined flags
        int length = 8;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF;
        int deltaWindowSize = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(deltaWindowSize);

        delegate.readWindowUpdateFrame(streamId, deltaWindowSize);
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testReservedSpdyWindowUpdateFrameBits() throws Exception {
        short type = 9;
        byte flags = 0;
        int length = 8;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF;
        int deltaWindowSize = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId | 0x80000000); // should ignore reserved bit
        buf.writeInt(deltaWindowSize | 0x80000000); // should ignore reserved bit

        delegate.readWindowUpdateFrame(streamId, deltaWindowSize);
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testInvalidSpdyWindowUpdateFrameLength() throws Exception {
        short type = 9;
        byte flags = 0;
        int length = 12; // invalid length
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF;
        int deltaWindowSize = RANDOM.nextInt() & 0x7FFFFFFF | 0x01;

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(deltaWindowSize);

        delegate.readFrameError((String) anyObject());
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }

    @Test
    public void testIllegalSpdyWindowUpdateFrameDeltaWindowSize() throws Exception {
        short type = 9;
        byte flags = 0;
        int length = 8;
        int streamId = RANDOM.nextInt() & 0x7FFFFFFF;
        int deltaWindowSize = 0; // invalid delta window size

        ByteBuf buf = ReferenceCountUtil.releaseLater(Unpooled.buffer(SPDY_HEADER_SIZE + length));
        encodeControlFrameHeader(buf, type, flags, length);
        buf.writeInt(streamId);
        buf.writeInt(deltaWindowSize);

        delegate.readFrameError((String) anyObject());
        replay(delegate);
        decoder.decode(buf);
        verify(delegate);
        assertFalse(buf.isReadable());
    }
}

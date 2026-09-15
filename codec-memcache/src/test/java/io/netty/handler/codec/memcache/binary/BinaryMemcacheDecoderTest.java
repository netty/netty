/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.memcache.binary;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.memcache.LastMemcacheContent;
import io.netty.handler.codec.memcache.MemcacheContent;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCounted;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the correct functionality of the {@link AbstractBinaryMemcacheDecoder}.
 * <p/>
 * While technically there are both a {@link BinaryMemcacheRequestDecoder} and a {@link BinaryMemcacheResponseDecoder}
 * they implement the same basics and just differ in the type of headers returned.
 */
public class BinaryMemcacheDecoderTest {

    /**
     * Represents a GET request header with a key size of three.
     */
    private static final byte[] GET_REQUEST = {
        (byte) 0x80, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x66, 0x6f, 0x6f
    };

    private static final byte[] SET_REQUEST_WITH_CONTENT = {
        (byte) 0x80, 0x01, 0x00, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0B, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x66, 0x6f, 0x6f, 0x01, 0x02, 0x03, 0x04, 0x05,
        0x06, 0x07, 0x08
    };

    private static final byte[] GET_RESPONSE_CHUNK_1 =  {
        (byte) 0x81, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x09, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x4e, 0x6f, 0x74, 0x20, 0x66, 0x6f, 0x75, 0x6e,
        0x64, (byte) 0x81, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x09, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x4e, 0x6f, 0x74, 0x20, 0x66, 0x6f, 0x75,
    };

    private static final byte[] GET_RESPONSE_CHUNK_2 = {
            0x6e, 0x64, (byte) 0x81, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x09, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x4e, 0x6f, 0x74, 0x20, 0x66, 0x6f,
            0x75, 0x6e, 0x64
    };

    private EmbeddedChannel channel;

    @BeforeEach
    public void setup() {
        channel = new EmbeddedChannel(new BinaryMemcacheRequestDecoder());
    }

    @AfterEach
    public void teardown() {
        channel.finishAndReleaseAll();
    }

    /**
     * This tests a simple GET request with a key as the value.
     */
    @Test
    public void shouldDecodeRequestWithSimpleValue() {
        ByteBuf incoming = Unpooled.buffer();
        incoming.writeBytes(GET_REQUEST);
        channel.writeInbound(incoming);

        BinaryMemcacheRequest request = channel.readInbound();

        assertNotNull(request);
        assertNotNull(request.key());
        assertNull(request.extras());

        assertEquals((short) 3, request.keyLength());
        assertEquals((byte) 0, request.extrasLength());
        assertEquals(3, request.totalBodyLength());

        request.release();
        assertInstanceOf(LastMemcacheContent.class, channel.readInbound());
    }

    /**
     * This test makes sure that large content is emitted in chunks.
     */
    @Test
    public void shouldDecodeRequestWithChunkedContent() {
        int smallBatchSize = 2;
        channel = new EmbeddedChannel(new BinaryMemcacheRequestDecoder(smallBatchSize));

        ByteBuf incoming = Unpooled.buffer();
        incoming.writeBytes(SET_REQUEST_WITH_CONTENT);
        channel.writeInbound(incoming);

        BinaryMemcacheRequest request = channel.readInbound();

        assertNotNull(request);
        assertNotNull(request.key());
        assertNull(request.extras());

        assertEquals((short) 3, request.keyLength());
        assertEquals((byte) 0, request.extrasLength());
        assertEquals(11, request.totalBodyLength());

        request.release();

        int expectedContentChunks = 4;
        for (int i = 1; i <= expectedContentChunks; i++) {
            MemcacheContent content = channel.readInbound();
            if (i < expectedContentChunks) {
                assertInstanceOf(MemcacheContent.class, content);
            } else {
                assertInstanceOf(LastMemcacheContent.class, content);
            }
            assertEquals(2, content.content().readableBytes());
            content.release();
        }
        assertNull(channel.readInbound());
    }

    /**
     * This test makes sure that even when the decoder is confronted with various chunk
     * sizes in the middle of decoding, it can recover and decode all the time eventually.
     */
    @Test
    public void shouldHandleNonUniformNetworkBatches() {
        ByteBuf incoming = Unpooled.copiedBuffer(SET_REQUEST_WITH_CONTENT);
        while (incoming.isReadable()) {
            channel.writeInbound(incoming.readBytes(5));
        }
        incoming.release();

        BinaryMemcacheRequest request = channel.readInbound();

        assertNotNull(request);
        assertNotNull(request.key());
        assertNull(request.extras());

        request.release();

        MemcacheContent content1 = channel.readInbound();
        MemcacheContent content2 = channel.readInbound();

        assertInstanceOf(MemcacheContent.class, content1);
        assertInstanceOf(LastMemcacheContent.class, content2);

        assertEquals(3, content1.content().readableBytes());
        assertEquals(5, content2.content().readableBytes());

        content1.release();
        content2.release();
    }

    /**
     * This test makes sure that even when more requests arrive in the same batch, they
     * get emitted as separate messages.
     */
    @Test
    public void shouldHandleTwoMessagesInOneBatch() {
        channel.writeInbound(Unpooled.buffer().writeBytes(GET_REQUEST).writeBytes(GET_REQUEST));

        BinaryMemcacheRequest request = channel.readInbound();
        assertInstanceOf(BinaryMemcacheRequest.class, request);
        assertNotNull(request);
        request.release();

        Object lastContent = channel.readInbound();
        assertInstanceOf(LastMemcacheContent.class, lastContent);
        ((ReferenceCounted) lastContent).release();

        request = channel.readInbound();
        assertInstanceOf(BinaryMemcacheRequest.class, request);
        assertNotNull(request);
        request.release();

        lastContent = channel.readInbound();
        assertInstanceOf(LastMemcacheContent.class, lastContent);
        ((ReferenceCounted) lastContent).release();
    }

    @Test
    public void shouldDecodeSeparatedValues() {
        String msgBody = "Not found";
        channel = new EmbeddedChannel(new BinaryMemcacheResponseDecoder());

        channel.writeInbound(Unpooled.buffer().writeBytes(GET_RESPONSE_CHUNK_1));
        channel.writeInbound(Unpooled.buffer().writeBytes(GET_RESPONSE_CHUNK_2));

        // First message
        BinaryMemcacheResponse response = channel.readInbound();
        assertEquals(BinaryMemcacheResponseStatus.KEY_ENOENT, response.status());
        assertEquals(msgBody.length(), response.totalBodyLength());
        response.release();

        // First message first content chunk
        MemcacheContent content = channel.readInbound();
        assertInstanceOf(LastMemcacheContent.class, content);
        assertEquals(msgBody, content.content().toString(CharsetUtil.UTF_8));
        content.release();

        // Second message
        response = channel.readInbound();
        assertEquals(BinaryMemcacheResponseStatus.KEY_ENOENT, response.status());
        assertEquals(msgBody.length(), response.totalBodyLength());
        response.release();

        // Second message first content chunk
        content = channel.readInbound();
        assertInstanceOf(MemcacheContent.class, content);
        assertEquals(msgBody.substring(0, 7), content.content().toString(CharsetUtil.UTF_8));
        content.release();

        // Second message second content chunk
        content = channel.readInbound();
        assertInstanceOf(LastMemcacheContent.class, content);
        assertEquals(msgBody.substring(7, 9), content.content().toString(CharsetUtil.UTF_8));
        content.release();

        // Third message
        response = channel.readInbound();
        assertEquals(BinaryMemcacheResponseStatus.KEY_ENOENT, response.status());
        assertEquals(msgBody.length(), response.totalBodyLength());
        response.release();

        // Third message first content chunk
        content = channel.readInbound();
        assertInstanceOf(LastMemcacheContent.class, content);
        assertEquals(msgBody, content.content().toString(CharsetUtil.UTF_8));
        content.release();
    }

    @Test
    public void shouldRetainCurrentMessageWhenSendingItOut() {
        channel = new EmbeddedChannel(
                new BinaryMemcacheRequestEncoder(),
                new BinaryMemcacheRequestDecoder());

        ByteBuf key = Unpooled.copiedBuffer("Netty", CharsetUtil.UTF_8);
        ByteBuf extras = Unpooled.copiedBuffer("extras", CharsetUtil.UTF_8);
        BinaryMemcacheRequest request = new DefaultBinaryMemcacheRequest(key, extras);

        assertTrue(channel.writeOutbound(request));
        for (;;) {
            ByteBuf buffer = channel.readOutbound();
            if (buffer == null) {
                break;
            }
            channel.writeInbound(buffer);
        }
        BinaryMemcacheRequest read = channel.readInbound();
        read.release();
        // tearDown will call "channel.finish()"
    }

    /**
     * Builds a header, where {@code keyLength}, {@code extrasLength} and {@code totalBodyLength} are written as the
     * raw (unsigned) protocol values. Requests and responses share this layout and only differ in the magic byte
     * and in how the two bytes at offset 6 are interpreted.
     */
    private static ByteBuf header(boolean request, int keyLength, int extrasLength, int totalBodyLength) {
        return Unpooled.buffer()
            .writeByte(request ? 0x80 : 0x81)   // magic
            .writeByte(0x00)                    // opcode
            .writeShort(keyLength)
            .writeByte(extrasLength)
            .writeByte(0x00)                    // dataType
            .writeShort(0x0000)                 // reserved (request) / status (response)
            .writeInt(totalBodyLength)
            .writeInt(0x00000000)               // opaque
            .writeLong(0L);                     // cas
    }

    /**
     * A message with no key, no extras and a four byte value, used to verify whether the decoder is still in sync
     * with the stream after the message preceding it.
     */
    private static ByteBuf followUpMessage(boolean request) {
        return header(request, 0, 0, 4).writeBytes(new byte[] { 'A', 'B', 'C', 'D' });
    }

    private void resetChannel(boolean request) {
        channel = new EmbeddedChannel(request
            ? new BinaryMemcacheRequestDecoder() : new BinaryMemcacheResponseDecoder());
    }

    private void assertFollowUpMessageDecodedInSync() {
        BinaryMemcacheMessage message = channel.readInbound();
        assertNotNull(message);
        assertTrue(message.decoderResult().isSuccess());
        assertEquals(0, message.keyLength());
        assertEquals(0, message.extrasLength());
        assertEquals(4, message.totalBodyLength());
        message.release();

        MemcacheContent content = channel.readInbound();
        assertInstanceOf(LastMemcacheContent.class, content);
        assertEquals("ABCD", content.content().toString(CharsetUtil.UTF_8));
        content.release();

        assertNull(channel.readInbound());
    }

    private void assertRejectedWithoutExposingBody() {
        BinaryMemcacheMessage message = channel.readInbound();
        assertNotNull(message);
        assertTrue(message.decoderResult().isFailure());
        assertInstanceOf(CorruptedFrameException.class, message.decoderResult().cause());
        // A rejected header must not hand any part of the stream to the application. Before the length fields were
        // validated, the key of such a message could contain the bytes of the message following it, including its
        // opaque, which correlates a response to the request that asked for it.
        assertEquals(0, message.key() == null ? 0 : message.key().readableBytes());
        assertEquals(0, message.extras() == null ? 0 : message.extras().readableBytes());
        message.release();
    }

    /**
     * The {@code extrasLength} header field is an unsigned 8 bit value, so it has to be interpreted as such rather
     * than as a signed {@code byte}. Otherwise the extras of a message with a length of {@code 0x80} or above are
     * not consumed and the following message is silently read as part of this message's value.
     */
    @ParameterizedTest(name = "request = {0}")
    @ValueSource(booleans = { false, true })
    public void shouldDecodeExtrasWithLengthAboveSignedByteRange(boolean request) {
        resetChannel(request);

        ByteBuf frame = header(request, 0, 0x80, 128).writeBytes(new byte[128]);
        channel.writeInbound(Unpooled.buffer().writeBytes(frame).writeBytes(followUpMessage(request)));
        frame.release();

        BinaryMemcacheMessage message = channel.readInbound();
        assertNotNull(message);
        assertTrue(message.decoderResult().isSuccess());
        assertNotNull(message.extras());
        assertEquals(128, message.extras().readableBytes());
        assertEquals(128, message.extrasLength() & 0xFF);
        assertEquals(128, message.totalBodyLength());
        message.release();

        // The whole body is extras, so there is no value.
        assertInstanceOf(LastMemcacheContent.class, channel.readInbound());

        assertFollowUpMessageDecodedInSync();
    }

    /**
     * The {@code keyLength} header field is an unsigned 16 bit value, so it has to be interpreted as such rather
     * than as a signed {@code short}. Otherwise the key of a message with a length of {@code 0x8000} or above is
     * not consumed and the following message is silently read as part of this message's value.
     */
    @ParameterizedTest(name = "request = {0}")
    @ValueSource(booleans = { false, true })
    public void shouldDecodeKeyWithLengthAboveSignedShortRange(boolean request) {
        resetChannel(request);

        ByteBuf frame = header(request, 0x8000, 0, 32768).writeBytes(new byte[32768]);
        channel.writeInbound(Unpooled.buffer().writeBytes(frame).writeBytes(followUpMessage(request)));
        frame.release();

        BinaryMemcacheMessage message = channel.readInbound();
        assertNotNull(message);
        assertTrue(message.decoderResult().isSuccess());
        assertNotNull(message.key());
        assertEquals(32768, message.key().readableBytes());
        assertEquals(32768, message.keyLength() & 0xFFFF);
        assertEquals(32768, message.totalBodyLength());
        message.release();

        // The whole body is the key, so there is no value.
        assertInstanceOf(LastMemcacheContent.class, channel.readInbound());

        assertFollowUpMessageDecodedInSync();
    }

    /**
     * The protocol defines {@code totalBodyLength} as the length of extras + key + value, so a header where the
     * extras and the key alone are longer than the total body can not be framed and has to be rejected. Note that
     * this needs no oversized length at all: decoding a 20 byte key out of a 4 byte body reads past the end of the
     * message and desynchronizes the stream.
     */
    @ParameterizedTest(name = "request = {0}")
    @ValueSource(booleans = { false, true })
    public void shouldRejectExtrasAndKeyLongerThanTotalBody(boolean request) {
        resetChannel(request);

        ByteBuf frame = header(request, 20, 0, 4).writeBytes(new byte[] { 'w', 'x', 'y', 'z' });
        channel.writeInbound(Unpooled.buffer().writeBytes(frame).writeBytes(followUpMessage(request)));
        frame.release();

        assertRejectedWithoutExposingBody();
    }

    /**
     * {@code totalBodyLength} is an unsigned 32 bit value, so it can exceed what an {@code int} can represent. Such
     * a message has to be rejected rather than treated as having a negative body length, which would leave the body
     * on the wire to be decoded as the next header.
     */
    @ParameterizedTest(name = "request = {0}")
    @ValueSource(booleans = { false, true })
    public void shouldRejectTotalBodyLengthAboveIntegerMaxValue(boolean request) {
        resetChannel(request);

        ByteBuf frame = header(request, 0, 0, 0x80000000).writeBytes(new byte[] { 'w', 'x', 'y', 'z' });
        channel.writeInbound(Unpooled.buffer().writeBytes(frame).writeBytes(followUpMessage(request)));
        frame.release();

        assertRejectedWithoutExposingBody();
    }

    /**
     * A {@code totalBodyLength} close to {@link Integer#MAX_VALUE} is representable and has to be accepted, with
     * the value length derived from it staying positive. Subtracting a signed, negative {@code keyLength} from such
     * a total body length overflowed instead, so the value was treated as empty and the message was terminated
     * before its body had been read.
     */
    @ParameterizedTest(name = "request = {0}")
    @ValueSource(booleans = { false, true })
    public void shouldNotOverflowValueLengthForLargeTotalBodyLength(boolean request) {
        resetChannel(request);

        // 0x7FFFFFFF - (-32768) overflows, while 0x7FFFFFFF - 32768 does not. The body is far too large to supply,
        // so feed the key plus the first few value bytes and observe that the value is still being read.
        ByteBuf frame = header(request, 0x8000, 0, Integer.MAX_VALUE)
            .writeBytes(new byte[32768])
            .writeBytes(new byte[] { 'v', 'a', 'l', 'u', 'e' });
        channel.writeInbound(frame);

        BinaryMemcacheMessage message = channel.readInbound();
        assertNotNull(message);
        assertTrue(message.decoderResult().isSuccess());
        assertNotNull(message.key());
        assertEquals(32768, message.key().readableBytes());
        message.release();

        MemcacheContent content = channel.readInbound();
        assertNotNull(content);
        assertEquals("value", content.content().toString(CharsetUtil.UTF_8));
        // Not the last chunk: the vast majority of the value is still outstanding.
        assertFalse(content instanceof LastMemcacheContent);
        content.release();

        assertNull(channel.readInbound());
    }

    /**
     * A framing violation can not be recovered from, because there is no way to tell which of the length fields
     * lied. Decoding therefore has to stop permanently rather than attempt to resynchronize: everything that
     * arrives after the rejected header, including otherwise well formed messages, must be discarded.
     */
    @ParameterizedTest(name = "request = {0}")
    @ValueSource(booleans = { false, true })
    public void shouldStopDecodingPermanentlyAfterRejectingAHeader(boolean request) {
        resetChannel(request);

        ByteBuf frame = header(request, 20, 0, 4).writeBytes(new byte[] { 'w', 'x', 'y', 'z' });
        channel.writeInbound(frame);

        assertRejectedWithoutExposingBody();
        assertNull(channel.readInbound());

        // Several perfectly valid messages arriving afterwards, in separate reads, must all be dropped.
        for (int i = 0; i < 3; i++) {
            channel.writeInbound(followUpMessage(request));
            assertNull(channel.readInbound());
        }
    }
}

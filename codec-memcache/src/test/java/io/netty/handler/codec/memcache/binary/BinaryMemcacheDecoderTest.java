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
import io.netty.handler.codec.memcache.LastMemcacheContent;
import io.netty.handler.codec.memcache.MemcacheContent;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCounted;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    public void setup() throws Exception {
        channel = new EmbeddedChannel(new BinaryMemcacheRequestDecoder());
    }

    @AfterEach
    public void teardown() throws Exception {
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
}

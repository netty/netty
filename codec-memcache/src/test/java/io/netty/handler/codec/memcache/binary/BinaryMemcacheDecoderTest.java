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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertTrue;

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

    @Before
    public void setup() throws Exception {
        channel = new EmbeddedChannel(new BinaryMemcacheRequestDecoder());
    }

    @After
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

        assertThat(request, notNullValue());
        assertThat(request.key(), notNullValue());
        assertThat(request.extras(), nullValue());

        assertThat(request.keyLength(), is((short) 3));
        assertThat(request.extrasLength(), is((byte) 0));
        assertThat(request.totalBodyLength(), is(3));

        request.release();
        assertThat(channel.readInbound(), instanceOf(LastMemcacheContent.class));
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

        assertThat(request, notNullValue());
        assertThat(request.key(), notNullValue());
        assertThat(request.extras(), nullValue());

        assertThat(request.keyLength(), is((short) 3));
        assertThat(request.extrasLength(), is((byte) 0));
        assertThat(request.totalBodyLength(), is(11));

        request.release();

        int expectedContentChunks = 4;
        for (int i = 1; i <= expectedContentChunks; i++) {
            MemcacheContent content = channel.readInbound();
            if (i < expectedContentChunks) {
                assertThat(content, instanceOf(MemcacheContent.class));
            } else {
                assertThat(content, instanceOf(LastMemcacheContent.class));
            }
            assertThat(content.content().readableBytes(), is(2));
            content.release();
        }
        assertThat(channel.readInbound(), nullValue());
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

        assertThat(request, notNullValue());
        assertThat(request.key(), notNullValue());
        assertThat(request.extras(), nullValue());

        request.release();

        MemcacheContent content1 = channel.readInbound();
        MemcacheContent content2 = channel.readInbound();

        assertThat(content1, instanceOf(MemcacheContent.class));
        assertThat(content2, instanceOf(LastMemcacheContent.class));

        assertThat(content1.content().readableBytes(), is(3));
        assertThat(content2.content().readableBytes(), is(5));

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
        assertThat(request, instanceOf(BinaryMemcacheRequest.class));
        assertThat(request, notNullValue());
        request.release();

        Object lastContent = channel.readInbound();
        assertThat(lastContent, instanceOf(LastMemcacheContent.class));
        ((ReferenceCounted) lastContent).release();

        request = channel.readInbound();
        assertThat(request, instanceOf(BinaryMemcacheRequest.class));
        assertThat(request, notNullValue());
        request.release();

        lastContent = channel.readInbound();
        assertThat(lastContent, instanceOf(LastMemcacheContent.class));
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
        assertThat(response.status(), is(BinaryMemcacheResponseStatus.KEY_ENOENT));
        assertThat(response.totalBodyLength(), is(msgBody.length()));
        response.release();

        // First message first content chunk
        MemcacheContent content = channel.readInbound();
        assertThat(content, instanceOf(LastMemcacheContent.class));
        assertThat(content.content().toString(CharsetUtil.UTF_8), is(msgBody));
        content.release();

        // Second message
        response = channel.readInbound();
        assertThat(response.status(), is(BinaryMemcacheResponseStatus.KEY_ENOENT));
        assertThat(response.totalBodyLength(), is(msgBody.length()));
        response.release();

        // Second message first content chunk
        content = channel.readInbound();
        assertThat(content, instanceOf(MemcacheContent.class));
        assertThat(content.content().toString(CharsetUtil.UTF_8), is(msgBody.substring(0, 7)));
        content.release();

        // Second message second content chunk
        content = channel.readInbound();
        assertThat(content, instanceOf(LastMemcacheContent.class));
        assertThat(content.content().toString(CharsetUtil.UTF_8), is(msgBody.substring(7, 9)));
        content.release();

        // Third message
        response = channel.readInbound();
        assertThat(response.status(), is(BinaryMemcacheResponseStatus.KEY_ENOENT));
        assertThat(response.totalBodyLength(), is(msgBody.length()));
        response.release();

        // Third message first content chunk
        content = channel.readInbound();
        assertThat(content, instanceOf(LastMemcacheContent.class));
        assertThat(content.content().toString(CharsetUtil.UTF_8), is(msgBody));
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

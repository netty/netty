/*
 * Copyright 2021 The Netty Project
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
import io.netty.buffer.Unpooled;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.incubator.codec.quic.QuicChannel;
import org.junit.Test;

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
import static org.junit.Assert.assertFalse;

public class Http3FrameDecoderTest {

    private static final int MAX_HEADER_SIZE = 1024;

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
                true, false, new Http3FrameDecoder(new QpackDecoder(), MAX_HEADER_SIZE));
        assertFalse(decoderChannel.writeInbound(buffer));
        assertFalse(decoderChannel.finish());
    }

    private static void testInvalidHttp3Frame0(int type, int length, Http3ErrorCode code) {
        ByteBuf buffer = Unpooled.buffer();
        writeVariableLengthInteger(buffer, type);
        writeVariableLengthInteger(buffer, length);

        QuicChannel parent = mockParent();

        EmbeddedChannel decoderChannel = new EmbeddedChannel(parent, DefaultChannelId.newInstance(),
                true, false, new Http3FrameDecoder(new QpackDecoder(), MAX_HEADER_SIZE));
        try {
            decoderChannel.writeInbound(buffer);
        } catch (Exception e) {
            assertException(code, e);
        }
        verifyClose(code, parent);
        assertFalse(decoderChannel.finish());
    }
}

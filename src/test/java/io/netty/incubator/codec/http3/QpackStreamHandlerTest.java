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
import io.netty.buffer.Unpooled;
import io.netty.incubator.codec.quic.QuicStreamType;
import org.junit.jupiter.api.Test;

import static io.netty.incubator.codec.http3.Http3TestUtils.verifyClose;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class QpackStreamHandlerTest {

    @Test
    public void testStreamClosedWhileParentStillActive() throws Exception {
        EmbeddedQuicChannel parent = new EmbeddedQuicChannel(true);

        EmbeddedQuicStreamChannel channel =
                (EmbeddedQuicStreamChannel) parent.createStream(QuicStreamType.UNIDIRECTIONAL,
                        new QpackDecoderHandler(new QpackEncoder())).get();
        assertFalse(channel.finish());
        verifyClose(1, Http3ErrorCode.H3_CLOSED_CRITICAL_STREAM, parent);
    }

    @Test
    public void testStreamClosedWhileParentIsInactive() throws Exception {
        EmbeddedQuicChannel parent = new EmbeddedQuicChannel(true);
        parent.close().get();

        EmbeddedQuicStreamChannel channel =
                (EmbeddedQuicStreamChannel) parent.createStream(QuicStreamType.UNIDIRECTIONAL,
                        new QpackDecoderHandler(new QpackEncoder())).get();
        assertFalse(channel.finish());
    }

    @Test
    public void testStreamDropsInboundData() throws Exception {
        EmbeddedQuicChannel parent = new EmbeddedQuicChannel(true);
        parent.close().get();

        EmbeddedQuicStreamChannel channel =
                (EmbeddedQuicStreamChannel) parent.createStream(QuicStreamType.UNIDIRECTIONAL,
                        new QpackDecoderHandler(new QpackEncoder())).get();
        ByteBuf buffer = Unpooled.buffer();
        assertFalse(channel.writeInbound(buffer));
        assertEquals(0, buffer.refCnt());
        assertFalse(channel.finish());
    }
}

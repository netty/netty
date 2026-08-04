/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.codec.socksx.v4;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.EncoderException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Socks4ClientEncoderTest {
    @Test
    public void mustEncodeCommandRequestToIPv4() {
        EmbeddedChannel encoder = new EmbeddedChannel(Socks4ClientEncoder.INSTANCE);
        DefaultSocks4CommandRequest request = new DefaultSocks4CommandRequest(
                Socks4CommandType.CONNECT, "127.0.0.1", 8008, "user");
        assertTrue(encoder.writeOutbound(request));
        ByteBuf buf = encoder.readOutbound();
        assertNotNull(buf);
        buf.release();
        assertFalse(encoder.finish());
    }
    @Test
    public void mustEncodeCommandRequestToDomain() {
        EmbeddedChannel encoder = new EmbeddedChannel(Socks4ClientEncoder.INSTANCE);
        DefaultSocks4CommandRequest request = new DefaultSocks4CommandRequest(
                Socks4CommandType.CONNECT, "unix://uds.sock", 8008, "user");
        assertTrue(encoder.writeOutbound(request));
        ByteBuf buf = encoder.readOutbound();
        assertNotNull(buf);
        buf.release();
        assertFalse(encoder.finish());
    }

    @Test
    public void mustRejectNulByteInUserIdWithIPv4Destination() {
        EmbeddedChannel encoder = new EmbeddedChannel(Socks4ClientEncoder.INSTANCE);
        DefaultSocks4CommandRequest request = new DefaultSocks4CommandRequest(
                Socks4CommandType.CONNECT, "127.0.0.1", 8008, "use\0r");
        assertThatThrownBy(() -> encoder.writeOutbound(request))
                .isInstanceOf(EncoderException.class)
                .hasMessageContaining("Illegal character");
        assertFalse(encoder.finish());
    }

    @Test
    public void mustRejectNulByteInUserIdWithDomainDestination() {
        EmbeddedChannel encoder = new EmbeddedChannel(Socks4ClientEncoder.INSTANCE);
        DefaultSocks4CommandRequest request = new DefaultSocks4CommandRequest(
                Socks4CommandType.CONNECT, "unix://uds.sock", 8008, "use\0r");
        assertThatThrownBy(() -> encoder.writeOutbound(request))
                .isInstanceOf(EncoderException.class)
                .hasMessageContaining("Illegal character");
        assertFalse(encoder.finish());
    }

    @Test
    public void mustRejectNulByteInDstAddr() {
        EmbeddedChannel encoder = new EmbeddedChannel(Socks4ClientEncoder.INSTANCE);
        DefaultSocks4CommandRequest request = new DefaultSocks4CommandRequest(
                Socks4CommandType.CONNECT, "unix://uds\0.sock", 8008, "user");
        assertThatThrownBy(() -> encoder.writeOutbound(request))
                .isInstanceOf(EncoderException.class)
                .hasMessageContaining("Illegal character");
        assertFalse(encoder.finish());
    }
}

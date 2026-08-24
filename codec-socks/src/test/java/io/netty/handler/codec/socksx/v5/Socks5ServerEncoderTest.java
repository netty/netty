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
package io.netty.handler.codec.socksx.v5;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.socksx.SocksVersion;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Socks5ServerEncoderTest {
    @Test
    public void commandResponseEncodingMustAcceptMaxLengthDstAddr() {
        EmbeddedChannel encoder = new EmbeddedChannel(Socks5ServerEncoder.DEFAULT);
        String dstAddr = Stream.generate(() -> "aaa").limit(64).collect(Collectors.joining("."));
        assertThat(dstAddr).hasSize(255);
        assertTrue(encoder.writeOutbound(new DefaultSocks5CommandResponse(
                Socks5CommandStatus.SUCCESS, Socks5AddressType.DOMAIN,
                dstAddr, 8080)));
        ByteBuf buf = encoder.readOutbound();
        assertNotNull(buf);
        buf.release();
        assertFalse(encoder.finish());
    }

    @Test
    public void commandResponseEncodingMustAcceptNullDstAddr() {
        EmbeddedChannel encoder = new EmbeddedChannel(Socks5ServerEncoder.DEFAULT);
        assertTrue(encoder.writeOutbound(new Socks5CommandResponse() {
            @Override
            public DecoderResult decoderResult() {
                return DecoderResult.SUCCESS;
            }

            @Override
            public void setDecoderResult(DecoderResult result) {
            }

            @Override
            public SocksVersion version() {
                return SocksVersion.SOCKS5;
            }

            @Override
            public Socks5CommandStatus status() {
                return Socks5CommandStatus.SUCCESS;
            }

            @Override
            public Socks5AddressType bndAddrType() {
                return Socks5AddressType.DOMAIN;
            }

            @Override
            public String bndAddr() {
                return null;
            }

            @Override
            public int bndPort() {
                return 8080;
            }
        }));
        ByteBuf buf = encoder.readOutbound();
        assertNotNull(buf);
        buf.release();
        assertFalse(encoder.finish());
    }

    @Test
    public void commandResponseEncodingMustRejectTooLongDstAddr() {
        EmbeddedChannel encoder = new EmbeddedChannel(Socks5ServerEncoder.DEFAULT);
        assertThatThrownBy(() -> encoder.writeOutbound(new Socks5CommandResponse() {
            @Override
            public DecoderResult decoderResult() {
                return DecoderResult.SUCCESS;
            }

            @Override
            public void setDecoderResult(DecoderResult result) {
            }

            @Override
            public SocksVersion version() {
                return SocksVersion.SOCKS5;
            }

            @Override
            public Socks5CommandStatus status() {
                return Socks5CommandStatus.SUCCESS;
            }

            @Override
            public Socks5AddressType bndAddrType() {
                return Socks5AddressType.DOMAIN;
            }

            @Override
            public String bndAddr() {
                return Stream.generate(() -> "a").limit(256).collect(Collectors.joining());
            }

            @Override
            public int bndPort() {
                return 8080;
            }
        }))
                .isInstanceOf(EncoderException.class)
                .hasMessageContaining("Invalid field length");
        assertFalse(encoder.finish());
    }
}

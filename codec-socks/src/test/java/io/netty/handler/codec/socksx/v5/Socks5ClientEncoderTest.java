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
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Socks5ClientEncoderTest {

    @Test
    public void initialRequestEncodingMustAcceptMaxNumberOfAuthMethods() {
        EmbeddedChannel encoder = new EmbeddedChannel(Socks5ClientEncoder.DEFAULT);
        assertTrue(encoder.writeOutbound(
            new DefaultSocks5InitialRequest(Socks5CommonTestUtils.generateList(Socks5AuthMethod.PASSWORD, 255))));
        ByteBuf buf = encoder.readOutbound();
        assertNotNull(buf);
        buf.release();
        assertFalse(encoder.finish());
    }

    @Test
    public void initialRequestEncodingMustRejectTooManyAuthMethods() {
        final EmbeddedChannel encoder = new EmbeddedChannel(Socks5ClientEncoder.DEFAULT);
        assertThatThrownBy(
            new ThrowableAssert.ThrowingCallable() {
                @Override
                public void call() throws Throwable {
                    encoder.writeOutbound(
                        new DefaultSocks5InitialRequest(Socks5CommonTestUtils.generateList(
                            Socks5AuthMethod.PASSWORD, 256)));
                }
            }
        ).isInstanceOf(EncoderException.class)
                .hasMessageContaining("Invalid field length");
        assertFalse(encoder.finish());
    }

    @Test
    public void passwordAuthRequestEncodingMustAcceptMaxLengthUsername() {
        EmbeddedChannel encoder = new EmbeddedChannel(Socks5ClientEncoder.DEFAULT);

        // max length username
        assertTrue(encoder.writeOutbound(
                new DefaultSocks5PasswordAuthRequest("user", "pass") {
                    @Override
                    public String username() {
                        return Socks5CommonTestUtils.generateString("a", 255);
                    }
                }
        ));
        ByteBuf buf = encoder.readOutbound();
        assertNotNull(buf);
        buf.release();

        // max length password
        assertTrue(encoder.writeOutbound(
                new DefaultSocks5PasswordAuthRequest("user", "pass") {
                    @Override
                    public String password() {
                        return Socks5CommonTestUtils.generateString("a", 255);
                    }
                }
        ));
        buf = encoder.readOutbound();
        assertNotNull(buf);
        buf.release();

        assertFalse(encoder.finish());
    }

    @Test
    public void passwordAuthRequestEncodingMustRejectTooLongUsernameOrPassword() {
        final EmbeddedChannel encoder = new EmbeddedChannel(Socks5ClientEncoder.DEFAULT);

        // too long username
        assertThatThrownBy(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                encoder.writeOutbound(
                    new DefaultSocks5PasswordAuthRequest("user", "pass") {
                        @Override
                        public String username() {
                            return Socks5CommonTestUtils.generateString("a", 256);
                        }
                    }
                );
            }
        })
                .isInstanceOf(EncoderException.class)
                .hasMessageContaining("Invalid field length");

        // too long password
        assertThatThrownBy(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                encoder.writeOutbound(
                    new DefaultSocks5PasswordAuthRequest("user", "pass") {
                        @Override
                        public String password() {
                            return Socks5CommonTestUtils.generateString("a", 256);
                        }
                    }
                );
            }
        })
                .isInstanceOf(EncoderException.class)
                .hasMessageContaining("Invalid field length");

        assertFalse(encoder.finish());
    }

    @Test
    public void commandRequestEncodingMustAcceptMaxLengthDstAddr() {
        EmbeddedChannel encoder = new EmbeddedChannel(Socks5ClientEncoder.DEFAULT);
        String dstAddr = "aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa" +
            ".aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa" +
            ".aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa.aaa";
        assertThat(dstAddr).hasSize(255);
        assertTrue(encoder.writeOutbound(new DefaultSocks5CommandRequest(
                Socks5CommandType.CONNECT, Socks5AddressType.DOMAIN,
                dstAddr, 8080)));
        ByteBuf buf = encoder.readOutbound();
        assertNotNull(buf);
        buf.release();
        assertFalse(encoder.finish());
    }

    @Test
    public void commandRequestEncodingMustAcceptNullDstAddr() {
        EmbeddedChannel encoder = new EmbeddedChannel(Socks5ClientEncoder.DEFAULT);
        assertTrue(encoder.writeOutbound(new Socks5CommandRequest() {
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
            public Socks5CommandType type() {
                return Socks5CommandType.CONNECT;
            }

            @Override
            public Socks5AddressType dstAddrType() {
                return Socks5AddressType.DOMAIN;
            }

            @Override
            public String dstAddr() {
                return null;
            }

            @Override
            public int dstPort() {
                return 8080;
            }
        }));
        ByteBuf buf = encoder.readOutbound();
        assertNotNull(buf);
        buf.release();
        assertFalse(encoder.finish());
    }

    @Test
    public void commandRequestEncodingMustRejectTooLongDstAddr() {
        final EmbeddedChannel encoder = new EmbeddedChannel(Socks5ClientEncoder.DEFAULT);
        assertThatThrownBy(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                encoder.writeOutbound(new Socks5CommandRequest() {
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
                    public Socks5CommandType type() {
                        return Socks5CommandType.CONNECT;
                    }

                    @Override
                    public Socks5AddressType dstAddrType() {
                        return Socks5AddressType.DOMAIN;
                    }

                    @Override
                    public String dstAddr() {
                        return Socks5CommonTestUtils.generateString("a", 256);
                    }

                    @Override
                    public int dstPort() {
                        return 8080;
                    }
                });
            }
        })
                .isInstanceOf(EncoderException.class)
                .hasMessageContaining("Invalid field length");
        assertFalse(encoder.finish());
    }
}

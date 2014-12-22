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

package io.netty.handler.codec.socksx.v5;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.internal.StringUtil;

import java.util.List;
import java.util.RandomAccess;

/**
 * Encodes a client-side {@link Socks5Message} into a {@link ByteBuf}.
 */
@Sharable
public class Socks5ClientEncoder extends MessageToByteEncoder<Socks5Message> {

    public static final Socks5ClientEncoder DEFAULT = new Socks5ClientEncoder();

    private final Socks5AddressEncoder addressEncoder;

    /**
     * Creates a new instance with the default {@link Socks5AddressEncoder}.
     */
    protected Socks5ClientEncoder() {
        this(Socks5AddressEncoder.DEFAULT);
    }

    /**
     * Creates a new instance with the specified {@link Socks5AddressEncoder}.
     */
    public Socks5ClientEncoder(Socks5AddressEncoder addressEncoder) {
        if (addressEncoder == null) {
            throw new NullPointerException("addressEncoder");
        }

        this.addressEncoder = addressEncoder;
    }

    /**
     * Returns the {@link Socks5AddressEncoder} of this encoder.
     */
    protected final Socks5AddressEncoder addressEncoder() {
        return addressEncoder;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Socks5Message msg, ByteBuf out) throws Exception {
        if (msg instanceof Socks5InitialRequest) {
            encodeAuthMethodRequest((Socks5InitialRequest) msg, out);
        } else if (msg instanceof Socks5PasswordAuthRequest) {
            encodePasswordAuthRequest((Socks5PasswordAuthRequest) msg, out);
        } else if (msg instanceof Socks5CommandRequest) {
            encodeCommandRequest((Socks5CommandRequest) msg, out);
        } else {
            throw new EncoderException("unsupported message type: " + StringUtil.simpleClassName(msg));
        }
    }

    private static void encodeAuthMethodRequest(Socks5InitialRequest msg, ByteBuf out) {
        out.writeByte(msg.version().byteValue());

        final List<Socks5AuthMethod> authMethods = msg.authMethods();
        final int numAuthMethods = authMethods.size();
        out.writeByte(numAuthMethods);

        if (authMethods instanceof RandomAccess) {
            for (int i = 0; i < numAuthMethods; i ++) {
                out.writeByte(authMethods.get(i).byteValue());
            }
        } else {
            for (Socks5AuthMethod a: authMethods) {
                out.writeByte(a.byteValue());
            }
        }
    }

    private static void encodePasswordAuthRequest(Socks5PasswordAuthRequest msg, ByteBuf out) {
        out.writeByte(0x01);

        final String username = msg.username();
        out.writeByte(username.length());
        ByteBufUtil.writeAscii(out, username);

        final String password = msg.password();
        out.writeByte(password.length());
        ByteBufUtil.writeAscii(out, password);
    }

    private void encodeCommandRequest(Socks5CommandRequest msg, ByteBuf out) throws Exception {
        out.writeByte(msg.version().byteValue());
        out.writeByte(msg.type().byteValue());
        out.writeByte(0x00);

        final Socks5AddressType dstAddrType = msg.dstAddrType();
        out.writeByte(dstAddrType.byteValue());
        addressEncoder.encodeAddress(dstAddrType, msg.dstAddr(), out);
        out.writeShort(msg.dstPort());
    }
}

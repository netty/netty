/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.haproxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;

import java.util.List;

import static io.netty.handler.codec.haproxy.HAProxyConstants.*;

/**
 * Encodes an HAProxy proxy protocol message
 *
 * @see <a href="http://www.haproxy.org/download/1.8/doc/proxy-protocol.txt">Proxy Protocol Specification</a>
 */
@Sharable
public final class HAProxyMessageEncoder extends MessageToByteEncoder<HAProxyMessage> {

    private static final int V2_VERSION_BITMASK = 0x02 << 4;

    // Length for source/destination addresses for the UNIX family must be 108 bytes each.
    static final int UNIX_ADDRESS_BYTES_LENGTH = 108;
    static final int TOTAL_UNIX_ADDRESS_BYTES_LENGTH = UNIX_ADDRESS_BYTES_LENGTH * 2;

    public static final HAProxyMessageEncoder INSTANCE = new HAProxyMessageEncoder();

    private HAProxyMessageEncoder() {
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, HAProxyMessage msg, ByteBuf out) throws Exception {
        if (msg.protocolVersion() == HAProxyProtocolVersion.V1) {
            encodeV1(msg, out);
        } else if (msg.protocolVersion() == HAProxyProtocolVersion.V2) {
            encodeV2(msg, out);
        } else {
            throw new HAProxyProtocolException("Unsupported version: " + msg.protocolVersion());
        }
    }

    private static void encodeV1(HAProxyMessage msg, ByteBuf out) {
        final String protocol = msg.proxiedProtocol().name();
        StringBuilder sb = new StringBuilder(108)
                .append("PROXY ").append(protocol).append(' ')
                .append(msg.sourceAddress()).append(' ')
                .append(msg.destinationAddress()).append(' ')
                .append(msg.sourcePort()).append(' ').append(msg.destinationPort()).append("\r\n");
        out.writeCharSequence(sb.toString(), CharsetUtil.US_ASCII);
    }

    private static void encodeV2(HAProxyMessage msg, ByteBuf out) {
        out.writeBytes(BINARY_PREFIX);
        out.writeByte(V2_VERSION_BITMASK | msg.command().byteValue());
        out.writeByte(msg.proxiedProtocol().byteValue());

        switch (msg.proxiedProtocol().addressFamily()) {
            case AF_IPv4:
            case AF_IPv6:
                byte[] sourceAddress = NetUtil.createByteArrayFromIpAddressString(msg.sourceAddress());
                byte[] destinationAddress = NetUtil.createByteArrayFromIpAddressString(msg.destinationAddress());
                out.writeShort(sourceAddress.length + destinationAddress.length + 4 + msg.tlvNumBytes());
                out.writeBytes(sourceAddress);
                out.writeBytes(destinationAddress);
                out.writeShort(msg.sourcePort());
                out.writeShort(msg.destinationPort());
                encodeTlvs(msg.tlvs(), out);
                break;
            case AF_UNIX:
                out.writeShort(TOTAL_UNIX_ADDRESS_BYTES_LENGTH + msg.tlvNumBytes());
                byte[] srcAddressBytes = msg.sourceAddress().getBytes(CharsetUtil.US_ASCII);
                out.writeBytes(srcAddressBytes);
                out.writeBytes(new byte[UNIX_ADDRESS_BYTES_LENGTH - srcAddressBytes.length]);
                byte[] dstAddressBytes = msg.destinationAddress().getBytes(CharsetUtil.US_ASCII);
                out.writeBytes(dstAddressBytes);
                out.writeBytes(new byte[UNIX_ADDRESS_BYTES_LENGTH - dstAddressBytes.length]);
                encodeTlvs(msg.tlvs(), out);
                break;
            case AF_UNSPEC:
                out.writeShort(0);
                break;
            default:
                throw new HAProxyProtocolException("unexpected addrFamily");
        }
    }

    private static void encodeTlv(HAProxyTLV haProxyTLV, ByteBuf out) {
        if (haProxyTLV instanceof HAProxySSLTLV) {
            HAProxySSLTLV ssltlv = (HAProxySSLTLV) haProxyTLV;
            out.writeByte(haProxyTLV.typeByteValue());
            out.writeShort(ssltlv.contentNumBytes());
            out.writeByte(ssltlv.client());
            out.writeInt(ssltlv.verify());
            encodeTlvs(ssltlv.encapsulatedTLVs(), out);
        } else {
            out.writeByte(haProxyTLV.typeByteValue());
            ByteBuf value = haProxyTLV.content();
            int readableBytes = value.readableBytes();
            out.writeShort(readableBytes);
            out.writeBytes(value.readSlice(readableBytes));
        }
    }

    private static void encodeTlvs(List<HAProxyTLV> haProxyTLVs, ByteBuf out) {
        for (int i = 0; i < haProxyTLVs.size(); i++) {
            encodeTlv(haProxyTLVs.get(i), out);
        }
    }
}

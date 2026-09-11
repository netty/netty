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
 * @see <a href="https://www.haproxy.org/download/1.8/doc/proxy-protocol.txt">Proxy Protocol Specification</a>
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
        switch (msg.protocolVersion()) {
            case V1:
                encodeV1(msg, out);
                break;
            case V2:
                encodeV2(msg, out);
                break;
            default:
                throw new HAProxyProtocolException("Unsupported version: " + msg.protocolVersion());
        }
    }

    private static void encodeV1(HAProxyMessage msg, ByteBuf out) {
        out.writeBytes(TEXT_PREFIX);
        out.writeByte((byte) ' ');
        out.writeCharSequence(msg.proxiedProtocol().name(), CharsetUtil.US_ASCII);
        out.writeByte((byte) ' ');
        out.writeCharSequence(msg.sourceAddress(), CharsetUtil.US_ASCII);
        out.writeByte((byte) ' ');
        out.writeCharSequence(msg.destinationAddress(), CharsetUtil.US_ASCII);
        out.writeByte((byte) ' ');
        out.writeCharSequence(String.valueOf(msg.sourcePort()), CharsetUtil.US_ASCII);
        out.writeByte((byte) ' ');
        out.writeCharSequence(String.valueOf(msg.destinationPort()), CharsetUtil.US_ASCII);
        out.writeByte((byte) '\r');
        out.writeByte((byte) '\n');
    }

    private static void encodeV2(HAProxyMessage msg, ByteBuf out) {
        out.writeBytes(BINARY_PREFIX);
        out.writeByte(V2_VERSION_BITMASK | msg.command().byteValue());
        out.writeByte(msg.proxiedProtocol().byteValue());

        switch (msg.proxiedProtocol().addressFamily()) {
            case AF_IPv4:
            case AF_IPv6:
                byte[] srcAddrBytes = NetUtil.createByteArrayFromIpAddressString(msg.sourceAddress());
                byte[] dstAddrBytes = NetUtil.createByteArrayFromIpAddressString(msg.destinationAddress());
                // srcAddrLen + dstAddrLen + 4 (srcPort + dstPort) + numTlvBytes
                out.writeShort(srcAddrBytes.length + dstAddrBytes.length + 4 + msg.tlvNumBytes());
                out.writeBytes(srcAddrBytes);
                out.writeBytes(dstAddrBytes);
                out.writeShort(msg.sourcePort());
                out.writeShort(msg.destinationPort());
                encodeTlvs(msg.tlvs(), out);
                break;
            case AF_UNIX:
                out.writeShort(TOTAL_UNIX_ADDRESS_BYTES_LENGTH + msg.tlvNumBytes());
                int srcAddrBytesWritten = out.writeCharSequence(msg.sourceAddress(), CharsetUtil.US_ASCII);
                out.writeZero(UNIX_ADDRESS_BYTES_LENGTH - srcAddrBytesWritten);
                int dstAddrBytesWritten = out.writeCharSequence(msg.destinationAddress(), CharsetUtil.US_ASCII);
                out.writeZero(UNIX_ADDRESS_BYTES_LENGTH - dstAddrBytesWritten);
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
            encodeChildTlvs(ssltlv.encapsulatedTLVs(), out);
        } else {
            out.writeByte(haProxyTLV.typeByteValue());
            ByteBuf value = haProxyTLV.content();
            int readableBytes = value.readableBytes();
            out.writeShort(readableBytes);
            // Do not use readSlice(...) (or similar) here as it would mutate the reader index of the
            // content, which is observable by the caller (e.g. HAProxyMessage.tlvs() exposes the same
            // TLV instance both as a top-level entry and, if it is a direct child of a PP2_TYPE_SSL TLV,
            // flattened into the top-level list) and could result in the same TLV being encoded
            // correctly the first time and as empty content thereafter.
            out.writeBytes(value, value.readerIndex(), readableBytes);
        }
    }

    /**
     * Encodes a genuine tree of encapsulated TLVs, e.g. the direct children of a {@link HAProxySSLTLV}.
     * Unlike {@link HAProxyMessage#tlvs()}, this list is not a flattened view, so every element must be
     * encoded.
     */
    private static void encodeChildTlvs(List<HAProxyTLV> haProxyTLVs, ByteBuf out) {
        for (int i = 0; i < haProxyTLVs.size(); i++) {
            encodeTlv(haProxyTLVs.get(i), out);
        }
    }

    /**
     * Encodes the top-level TLV list as returned by {@link HAProxyMessage#tlvs()}. That list flattens the
     * direct children of any {@link HAProxySSLTLV} into it for convenience, so those flattened entries must
     * be skipped here: they are already encoded (recursively) as part of their parent
     * {@link HAProxySSLTLV}. Encoding them again would both duplicate their bytes on the wire and make the
     * declared TLV length (see {@link HAProxyMessage#tlvNumBytes()}) not match the number of bytes actually
     * written.
     */
    private static void encodeTlvs(List<HAProxyTLV> haProxyTLVs, ByteBuf out) {
        for (int i = 0; i < haProxyTLVs.size(); i += 1 + HAProxyMessage.flattenedChildren(haProxyTLVs, i)) {
            encodeTlv(haProxyTLVs.get(i), out);
        }
    }
}

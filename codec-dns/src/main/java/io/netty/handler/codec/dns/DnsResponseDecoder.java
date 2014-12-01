/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.CharsetUtil;
import java.util.List;

/**
 * DnsResponseDecoder accepts {@link DatagramPacket} and encodes to
 * {@link DnsResponse}. This class also contains methods for decoding parts of
 * {@link DnsResponse}s such as questions and resource records.
 */
@ChannelHandler.Sharable
public class DnsResponseDecoder extends MessageToMessageDecoder<DatagramPacket> {

    public DnsResponseDecoder() {
        super(DatagramPacket.class);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket packet, List<Object> out) {
        out.add(decode(ctx, packet));
    }

    /**
     * Decode a {@link DatagramPacket} into a DnsResponse.
     *
     * @param ctx The channel context
     * @param packet The packet
     * @return A DnsResponse
     */
    public DnsResponse decode(ChannelHandlerContext ctx, DatagramPacket packet) {
        ByteBuf buf = packet.content();

        int id = buf.readUnsignedShort();

        DnsResponse response = new DnsResponse(id, packet.sender());
        DnsResponseHeader header = response.header();
        int flags = buf.readUnsignedShort();
        header.setType(flags >> 15);
        header.setOpcode(flags >> 11 & 0xf);
        header.setRecursionDesired((flags >> 8 & 1) == 1);
        header.setAuthoritativeAnswer((flags >> 10 & 1) == 1);
        header.setTruncated((flags >> 9 & 1) == 1);
        header.setRecursionAvailable((flags >> 7 & 1) == 1);
        header.setZ(flags >> 4 & 0x7);
        header.setResponseCode(DnsResponseCode.valueOf(flags & 0xf));

        int questions = buf.readUnsignedShort();
        int answers = buf.readUnsignedShort();
        int authorities = buf.readUnsignedShort();
        int additionals = buf.readUnsignedShort();

        for (int i = 0; i < questions; i++) {
            response.addQuestion(decodeQuestion(buf));
        }
        if (header.responseCode() != DnsResponseCode.NOERROR) {
            // response code for error
            return response;
        }
        boolean release = true;
        try {
            for (int i = 0; i < answers; i++) {
                response.addAnswer(decodeResource(buf));
            }
            for (int i = 0; i < authorities; i++) {
                response.addAuthorityResource(decodeResource(buf));
            }
            for (int i = 0; i < additionals; i++) {
                response.addAdditionalResource(decodeResource(buf));
            }
            release = false;
            return response;
        } finally {
            if (release) {
                // We need to release te DnsResources in case of an Exception as we called retain() on the buffer.
                releaseDnsResources(response.answers());
                releaseDnsResources(response.authorityResources());
                releaseDnsResources(response.additionalResources());
            }
        }
    }

    private static void releaseDnsResources(List<DnsEntry> resources) {
        int size = resources.size();
        for (int i = 0; i < size; i++) {
            DnsEntry resource = resources.get(i);
            if (resource instanceof ByteBufHolder) {
                ((ByteBufHolder) resource).release();
            }
        }
    }

    /**
     * Retrieves a domain name given a buffer containing a DNS packet. If the
     * name contains a pointer, the position of the buffer will be set to
     * directly after the pointer's index after the name has been read.
     *
     * @param buf the byte buffer containing the DNS packet
     * @return the domain name for an entry
     */
    static String readName(ByteBuf buf) {
        int position = -1;
        int checked = 0;
        int length = buf.writerIndex();
        StringBuilder name = new StringBuilder();
        for (int len = buf.readUnsignedByte(); buf.isReadable() && len != 0; len = buf.readUnsignedByte()) {
            boolean pointer = (len & 0xc0) == 0xc0;
            if (pointer) {
                if (position == -1) {
                    position = buf.readerIndex() + 1;
                }
                buf.readerIndex((len & 0x3f) << 8 | buf.readUnsignedByte());
                // check for loops
                checked += 2;
                if (checked >= length) {
                    throw new DnsDecoderException(DnsResponseCode.FORMERROR,
                            "Name contains a loop:" + name);
                }
            } else {
                if (len > 63) {
                    throw new DnsDecoderException(DnsResponseCode.BADNAME, "Octet length " + len);
                }
                name.append(buf.toString(buf.readerIndex(), len, CharsetUtil.UTF_8)).append('.');
                buf.skipBytes(len);
            }
        }
        if (position != -1) {
            buf.readerIndex(position);
        }
        if (name.length() == 0) {
            return "";
        }
        if (name.length() > 253) {
            // See http://blogs.msdn.com/b/oldnewthing/archive/2012/04/12/10292868.aspx for why
            throw new DnsDecoderException(DnsResponseCode.BADNAME, "Name > 253 characters in length: " + name);
        }

        return name.substring(0, name.length() - 1);
    }

    /**
     * Decodes a question, given a DNS packet in a byte buffer.
     *
     * @param buf the byte buffer containing the DNS packet
     * @return a decoded {@link DnsQuestion}
     */
    private static DnsQuestion decodeQuestion(ByteBuf buf) {
        String name = readName(buf);
        DnsType type = DnsType.valueOf(buf.readUnsignedShort());
        DnsClass qClass = DnsClass.valueOf(buf.readUnsignedShort());
        return new DnsQuestion(name, type, qClass);
    }

    /**
     * Decodes a resource record, given a DNS packet in a byte buffer.
     *
     * @param buf the byte buffer containing the DNS packet
     * @return a {@link DnsResource} record containing response data
     */
    private DnsEntry decodeResource(ByteBuf buf) {
        String name = readName(buf);
        DnsType type = DnsType.valueOf(buf.readUnsignedShort());
        DnsClass aClass = DnsClass.valueOf(buf.readUnsignedShort());
        long ttl = buf.readUnsignedInt();
        int len = buf.readUnsignedShort();
        return type.decode(name, aClass, ttl, buf, len);
    }
}

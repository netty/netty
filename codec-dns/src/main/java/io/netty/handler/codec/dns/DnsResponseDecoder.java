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
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.CharsetUtil;

import java.util.List;

/**
 * DnsResponseDecoder accepts {@link io.netty.channel.socket.DatagramPacket} and encodes to
 * {@link DnsResponse}. This class also contains methods for decoding parts of
 * DnsResponses such as questions and resource records.
 */
@ChannelHandler.Sharable
public class DnsResponseDecoder extends MessageToMessageDecoder<DatagramPacket> {

    /**
     * Retrieves a domain name given a buffer containing a DNS packet. If the
     * name contains a pointer, the position of the buffer will be set to
     * directly after the pointer's index after the name has been read.
     *
     * @param buf
     *            the byte buffer containing the DNS packet
     * @return the domain name for an entry
     */
    private static String readName(ByteBuf buf) {
        int position = -1;
        StringBuilder name = new StringBuilder();
        for (int len = buf.readUnsignedByte(); buf.isReadable() && len != 0; len = buf.readUnsignedByte()) {
            boolean pointer = (len & 0xc0) == 0xc0;
            if (pointer) {
                if (position == -1) {
                    position = buf.readerIndex() + 1;
                }
                buf.readerIndex((len & 0x3f) << 8 | buf.readUnsignedByte());
            } else {
                name.append(buf.toString(buf.readerIndex(), len, CharsetUtil.UTF_8)).append('.');
                buf.skipBytes(len);
            }
        }
        if (position != -1) {
            buf.readerIndex(position);
        }
        if (name.length() == 0) {
            return null;
        }
        return name.substring(0, name.length() - 1);
    }

    /**
     * Decodes a question, given a DNS packet in a byte buffer.
     *
     * @param buf
     *            the byte buffer containing the DNS packet
     * @return a decoded {@link DnsQuestion}
     */
    private static DnsQuestion decodeQuestion(ByteBuf buf) {
        String name = readName(buf);
        int type = buf.readUnsignedShort();
        int qClass = buf.readUnsignedShort();
        return new DnsQuestion(name, type, qClass);
    }

    /**
     * Decodes a resource record, given a DNS packet in a byte buffer.
     *
     * @param buf
     *            the byte buffer containing the DNS packet
     * @return a {@link DnsResource} record containing response data
     */
    private static DnsResource decodeResource(ByteBuf buf) {
        String name = readName(buf);
        int type = buf.readUnsignedShort();
        int aClass = buf.readUnsignedShort();
        long ttl = buf.readUnsignedInt();
        int len = buf.readUnsignedShort();

        int readerIndex = buf.readerIndex();
        ByteBuf payload = buf.duplicate().setIndex(readerIndex, readerIndex + len);
        buf.readerIndex(readerIndex + len);
        return new DnsResource(name, type, aClass, ttl, payload);
    }

    /**
     * Decodes a DNS response header, given a DNS packet in a byte buffer.
     *
     * @param parent
     *            the parent {@link DnsResponse} to this header
     * @param buf
     *            the byte buffer containing the DNS packet
     * @return a {@link DnsResponseHeader} containing the response's header
     *         information
     */
    private static DnsResponseHeader decodeHeader(DnsResponse parent, ByteBuf buf) {
        int id = buf.readUnsignedShort();
        DnsResponseHeader header = new DnsResponseHeader(parent, id);
        int flags = buf.readUnsignedShort();
        header.setType(flags >> 15);
        header.setOpcode(flags >> 11 & 0xf);
        header.setRecursionDesired((flags >> 8 & 1) == 1);
        header.setAuthoritativeAnswer((flags >> 10 & 1) == 1);
        header.setTruncated((flags >> 9 & 1) == 1);
        header.setRecursionAvailable((flags >> 7 & 1) == 1);
        header.setZ(flags >> 4 & 0x7);
        header.setResponseCode(DnsResponseCode.valueOf(flags & 0xf));
        header.setReadQuestions(buf.readUnsignedShort());
        header.setReadAnswers(buf.readUnsignedShort());
        header.setReadAuthorityResources(buf.readUnsignedShort());
        header.setReadAdditionalResources(buf.readUnsignedShort());
        return header;
    }

    /**
     * Decodes a response from a {@link io.netty.channel.socket.DatagramPacket} containing a
     * {@link io.netty.buffer.ByteBuf} with a DNS packet. Responses are sent from a DNS server
     * to a client in response to a query. This method writes the decoded
     * response to the specified {@link java.util.List} to be handled by a specialized
     * message handler.
     *
     * @param ctx
     *            the {@link io.netty.channel.ChannelHandlerContext} this
     *            {@link DnsResponseDecoder} belongs to
     * @param packet
     *            the message being decoded, a {@link io.netty.channel.socket.DatagramPacket} containing
     *            a DNS packet
     * @param out
     *            the {@link java.util.List} to which decoded messages should be added
     * @throws Exception
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket packet, List<Object> out) throws Exception {
        ByteBuf buf = packet.content().retain();
        DnsResponse response = new DnsResponse(packet.sender());
        DnsResponseHeader header = decodeHeader(response, buf);
        response.setHeader(header);
        for (int i = 0; i < header.getReadQuestions(); i++) {
            response.addQuestion(decodeQuestion(buf));
        }
        if (header.getResponseCode() != DnsResponseCode.NOERROR) {
            // response code for error
            out.add(response);
            return;
        }
        for (int i = 0; i < header.getReadAnswers(); i++) {
            response.addAnswer(decodeResource(buf));
        }
        for (int i = 0; i < header.getReadAuthorityResources(); i++) {
            response.addAuthorityResource(decodeResource(buf));
        }
        for (int i = 0; i < header.getReadAdditionalResources(); i++) {
            response.addAdditionalResource(decodeResource(buf));
        }
        out.add(response);
    }
}

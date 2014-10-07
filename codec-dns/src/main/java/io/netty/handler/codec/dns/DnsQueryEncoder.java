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
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;

import java.nio.charset.Charset;
import java.util.List;

/**
 * DnsQueryEncoder accepts {@link DnsQuery} and encodes to {@link ByteBuf}. This
 * class also contains methods for encoding parts of DnsQuery's such as the
 * header and questions.
 */
@ChannelHandler.Sharable
public class DnsQueryEncoder extends MessageToMessageEncoder<DnsQuery> {

    @Override
    protected void encode(ChannelHandlerContext ctx, DnsQuery query, List<Object> out) throws Exception {
        ByteBuf buf = ctx.alloc().buffer();
        encodeHeader(query.header(), buf);
        List<DnsQuestion> questions = query.questions();
        for (DnsQuestion question : questions) {
            encodeQuestion(question, CharsetUtil.US_ASCII, buf);
        }
        for (DnsResource resource: query.additionalResources()) {
            encodeResource(resource, CharsetUtil.US_ASCII, buf);
        }
        out.add(new DatagramPacket(buf, query.recipient(), null));
    }

    /**
     * Encodes the information in a {@link DnsHeader} and writes it to the
     * specified {@link ByteBuf}. The header is always 12 bytes long.
     *
     * @param header
     *            the query header being encoded
     * @param buf
     *            the buffer the encoded data should be written to
     */
    private static void encodeHeader(DnsHeader header, ByteBuf buf) {
        buf.writeShort(header.id());
        int flags = 0;
        flags |= header.type() << 15;
        flags |= header.opcode() << 14;
        flags |= header.isRecursionDesired() ? 1 << 8 : 0;
        buf.writeShort(flags);
        buf.writeShort(header.questionCount());
        buf.writeShort(0); // answerCount
        buf.writeShort(0); // authorityResourceCount
        buf.writeShort(header.additionalResourceCount());
    }

    /**
     * Encodes the information in a {@link DnsQuestion} and writes it to the
     * specified {@link ByteBuf}.
     *
     * @param question
     *            the question being encoded
     * @param charset
     *            charset names are encoded in
     * @param buf
     *            the buffer the encoded data should be written to
     */
    private static void encodeQuestion(DnsQuestion question, Charset charset, ByteBuf buf) {
        String[] parts = StringUtil.split(question.name(), '.');
        for (String part: parts) {
            final int partLen = part.length();
            if (partLen == 0) {
                continue;
            }
            buf.writeByte(partLen);
            buf.writeBytes(part.getBytes(charset));
        }
        buf.writeByte(0); // marks end of name field
        buf.writeShort(question.type().intValue());
        buf.writeShort(question.dnsClass().intValue());
    }

    private static void encodeResource(DnsResource resource, Charset charset, ByteBuf buf) {
        String[] parts = StringUtil.split(resource.name(), '.');
        for (String part: parts) {
            final int partLen = part.length();
            if (partLen == 0) {
                continue;
            }
            buf.writeByte(partLen);
            buf.writeBytes(part.getBytes(charset));
        }
        buf.writeByte(0); // marks end of name field

        buf.writeShort(resource.type().intValue());
        buf.writeShort(resource.dnsClass().intValue());
        buf.writeInt((int) resource.timeToLive());

        ByteBuf content = resource.content();
        int contentLen = content.readableBytes();

        buf.writeShort(contentLen);
        buf.writeBytes(content, content.readerIndex(), contentLen);
    }
}

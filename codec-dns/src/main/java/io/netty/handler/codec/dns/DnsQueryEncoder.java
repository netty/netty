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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.CharsetUtil;

import java.nio.charset.Charset;
import java.util.List;

/**
 * DnsQueryEncoder accepts {@link DnsQuery} and encodes to {@link ByteBuf}. This
 * class also contains methods for encoding parts of DnsQuery's such as the
 * header and questions.
 */
public class DnsQueryEncoder extends MessageToByteEncoder<DnsQuery> {

    /**
     * Encodes the information in a {@link DnsQueryHeader} and writes it to the
     * specified {@link ByteBuf}. The header is always 12 bytes long.
     *
     * @param header
     *            the query header being encoded
     * @param buf
     *            the buffer the encoded data should be written to
     */
    private static void encodeHeader(DnsQueryHeader header, ByteBuf buf) {
        buf.writeShort(header.getId());
        int flags = 0;
        flags |= header.getType() << 15;
        flags |= header.getOpcode() << 14;
        flags |= header.isRecursionDesired() ? 1 << 8 : 0;
        buf.writeShort(flags);
        buf.writeShort(header.questionCount());
        buf.writeShort(header.answerCount()); // Must be 0
        buf.writeShort(header.authorityResourceCount()); // Must be 0
        buf.writeShort(header.additionalResourceCount()); // Must be 0
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
        String[] parts = question.name().split("\\.");
        for (int i = 0; i < parts.length; i++) {
            buf.writeByte(parts[i].length());
            buf.writeBytes(charset.encode(parts[i]));
        }
        buf.writeByte(0); // marks end of name field
        buf.writeShort(question.type());
        buf.writeShort(question.dnsClass());
    }

    /**
     * Encodes a query and writes it to a {@link ByteBuf}.
     *
     * @param query
     *            the {@link DnsQuery} being encoded
     * @param buf
     *            the {@link ByteBuf} the query will be written to
     */
    protected static void encodeQuery(DnsQuery query, ByteBuf buf) {
        encodeHeader(query.getHeader(), buf);
        List<DnsQuestion> questions = query.getQuestions();
        for (DnsQuestion question : questions) {
            encodeQuestion(question, CharsetUtil.UTF_8, buf);
        }
    }

    /**
     * Encodes a query and writes it to a {@link ByteBuf}. Queries are sent to a
     * DNS server and a response will be returned from the server. The encoded
     * ByteBuf is written to the specified {@link ByteBuf}.
     *
     * @param ctx
     *            the {@link ChannelHandlerContext} this {@link DnsQueryEncoder}
     *            belongs to
     * @param query
     *            the query being encoded
     * @param out
     *            the {@link ByteBuf} to which encoded messages should be
     *            written
     * @throws Exception
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, DnsQuery query, ByteBuf out) throws Exception {
        encodeQuery(query, out);
    }

}

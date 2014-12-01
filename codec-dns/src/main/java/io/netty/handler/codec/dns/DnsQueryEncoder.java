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
import static io.netty.handler.codec.dns.NameWriter.NAME_WRITER_KEY;
import io.netty.util.Attribute;
import io.netty.util.CharsetUtil;
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
        out.add(encode(ctx, query));
    }

    /**
     * Encode a {@link DnsQuery} into a {@link DatagramPacket}.
     * @param ctx The channel context
     * @param query The query
     * @return a {@link DatagramPacket}
     * @throws Exception
     */
    public static DatagramPacket encode(ChannelHandlerContext ctx, DnsQuery query) {
        ByteBuf buf = ctx.alloc().buffer();
        encodeHeader(query.header(), buf);

        // Get the NameWriter - it can be stateful if doing compression,
        // so look it up, don't hold it as a field, so we can use @Sharable
        Attribute<NameWriter> nw = ctx.attr(NAME_WRITER_KEY);
        NameWriter nameWriter = nw.get();
        if (nameWriter == null) {
            nameWriter = NameWriter.DEFAULT;
        }

        for (DnsQuestion question : query) {
            question.writeTo(nameWriter, buf, CharsetUtil.UTF_8);
        }
        for (DnsEntry resource: query.additionalResources()) {
            resource.writeTo(nameWriter, buf, CharsetUtil.US_ASCII);
        }
        return new DatagramPacket(buf, query.recipient(), null);
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
}

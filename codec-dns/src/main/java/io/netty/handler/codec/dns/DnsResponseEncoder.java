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
package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageEncoder;
import static io.netty.handler.codec.dns.NameWriter.NAME_WRITER_KEY;
import io.netty.util.Attribute;
import io.netty.util.CharsetUtil;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Encodes DNS responses into DatagramPackets for use when writing a DNS server.
 */
@ChannelHandler.Sharable
public class DnsResponseEncoder extends MessageToMessageEncoder<DnsResponse> {

    private static final int FLAGS_QR = 15;
    private static final int FLAGS_Opcode = 11;
    private static final int FLAGS_AA = 10;
    private static final int FLAGS_TC = 9;
    private static final int FLAGS_RD = 8;
    private static final int FLAGS_RA = 7;
    private static final int FLAGS_Z = 4;

    public DnsResponseEncoder() {
        super(DnsResponse.class);
    }

    private void encodeQuestion(NameWriter nameWriter, DnsQuestion question, Charset charset, ByteBuf buf) {
        nameWriter.writeName(question.name(), buf, charset);
        buf.writeShort(question.type().intValue());
        buf.writeShort(question.dnsClass().intValue());
    }

    private int flip(int flags, int index, boolean is) {
        int i = 1 << index;
        if (is) {
            flags |= i;
        } else {
            flags &= i ^ 0xFFFF;
        }
        return flags;
    }

    public DatagramPacket encode(ChannelHandlerContext ctx, DnsResponse msg) {
        ByteBuf buf = ctx.alloc().buffer();
        buf.writeShort(msg.header().id());
        DnsResponseHeader h = msg.header();
        int flags = h.responseCode().code();
        flags |= h.opcode() << FLAGS_Opcode;
        flags |= h.type() << FLAGS_QR;
        flags = flip(flags, FLAGS_RD, h.isRecursionDesired());
        flags = flip(flags, FLAGS_RA, h.isRecursionAvailable());
        flags = flip(flags, FLAGS_AA, h.isAuthoritativeAnswer());
        flags = flip(flags, FLAGS_TC, h.isTruncated());
        flags |= h.z() << FLAGS_Z;
        buf.writeShort(flags);
        buf.writeShort(h.questionCount());
        buf.writeShort(h.answerCount());
        buf.writeShort(h.authorityResourceCount());
        buf.writeShort(h.additionalResourceCount());

        // Get the NameWriter - it can be stateful if doing compression,
        // so look it up, don't hold it as a field, so we can use @Sharable
        Attribute<NameWriter> nw = ctx.attr(NAME_WRITER_KEY);
        NameWriter nameWriter = nw.get();
        if (nameWriter == null) {
            nameWriter = NameWriter.DEFAULT;
        }

        for (DnsQuestion q : msg.questions()) {
            encodeQuestion(nameWriter, q, CharsetUtil.UTF_8, buf);
        }
        if (h.responseCode() != DnsResponseCode.NOERROR) {
            DatagramPacket packet = new DatagramPacket(buf, msg.sender());
            return packet;
        }
        for (DnsEntry r : msg.answers()) {
            r.writeTo(nameWriter, buf, CharsetUtil.UTF_8);
        }
        for (DnsEntry r : msg.authorityResources()) {
            r.writeTo(nameWriter, buf, CharsetUtil.UTF_8);
        }
        for (DnsEntry a : msg.additionalResources()) {
            a.writeTo(nameWriter, buf, CharsetUtil.UTF_8);
        }
        DatagramPacket packet = new DatagramPacket(buf, msg.sender());
        return packet;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, DnsResponse msg, List<Object> out) {
        out.add(encode(ctx, msg));
    }
}

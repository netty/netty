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
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.CharsetUtil;
import java.util.List;

/**
 *
 */
public class DnsQueryDecoder extends MessageToMessageDecoder<DatagramPacket> {

    public DnsQueryDecoder() {
        super(DatagramPacket.class);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) throws Exception {
        out.add(decode(ctx, msg));
    }

    public DnsQuery decode(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
        ByteBuf buf = msg.content();
        int id = buf.readUnsignedShort();
        DnsQuery query = new DnsQuery(id, msg.sender());
        DnsQueryHeader header = new DnsQueryHeader(query, id);
        int flags = buf.readUnsignedShort();
        header.setType(flags >> 15);
        header.setOpcode(flags >> 11 & 0xf);
        header.setRecursionDesired((flags >> 8 & 1) == 1);
        header.setZ(flags >> 4 & 0x7);
        int questionCount = buf.readUnsignedShort();
        int answerCount = buf.readUnsignedShort(); //should be 0
        int authorityCount = buf.readUnsignedShort(); //should be 0
        int additionalCount = buf.readUnsignedShort(); //should be 0
        for (int i = 0; i < questionCount; i++) {
            StringBuilder content = new StringBuilder();
            for (;;) {
                int length = buf.readByte();
                if (length == 0) {
                    break;
                }
                String part = buf.toString(buf.readerIndex(), length, CharsetUtil.UTF_8);
                if (content.length() != 0) {
                    content.append('.');
                }
                content.append(part);
                int next = buf.readerIndex() + length;
                buf.readerIndex(next);
            }
            DnsType type = DnsType.valueOf(buf.readShort());
            DnsClass dnsClass = DnsClass.valueOf(buf.readShort());
            DnsQuestion question = new DnsQuestion(content.toString(), type, dnsClass);
            query.addQuestion(question);
        }
        return query;
    }
}

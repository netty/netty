/*
 * Copyright 2015 The Netty Project
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
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.internal.UnstableApi;

import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Decodes a {@link DatagramPacket} into a {@link DatagramDnsResponse}.
 */
@UnstableApi
@ChannelHandler.Sharable
public class DatagramDnsResponseDecoder extends MessageToMessageDecoder<DatagramPacket> {

    private final DnsRecordDecoder recordDecoder;

    /**
     * Creates a new decoder with {@linkplain DnsRecordDecoder#DEFAULT the default record decoder}.
     */
    public DatagramDnsResponseDecoder() {
        this(DnsRecordDecoder.DEFAULT);
    }

    /**
     * Creates a new decoder with the specified {@code recordDecoder}.
     */
    public DatagramDnsResponseDecoder(DnsRecordDecoder recordDecoder) {
        this.recordDecoder = checkNotNull(recordDecoder, "recordDecoder");
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket packet, List<Object> out) throws Exception {
        final ByteBuf buf = packet.content();

        final DnsResponse response = newResponse(packet, buf);
        boolean success = false;
        try {
            final int questionCount = buf.readUnsignedShort();
            final int answerCount = buf.readUnsignedShort();
            final int authorityRecordCount = buf.readUnsignedShort();
            final int additionalRecordCount = buf.readUnsignedShort();

            decodeQuestions(response, buf, questionCount);
            decodeRecords(response, DnsSection.ANSWER, buf, answerCount);
            decodeRecords(response, DnsSection.AUTHORITY, buf, authorityRecordCount);
            decodeRecords(response, DnsSection.ADDITIONAL, buf, additionalRecordCount);

            out.add(response);
            success = true;
        } finally {
            if (!success) {
                response.release();
            }
        }
    }

    private static DnsResponse newResponse(DatagramPacket packet, ByteBuf buf) {
        final int id = buf.readUnsignedShort();

        final int flags = buf.readUnsignedShort();
        if (flags >> 15 == 0) {
            throw new CorruptedFrameException("not a response");
        }

        final DnsResponse response = new DatagramDnsResponse(
            packet.sender(),
            packet.recipient(),
            id,
            DnsOpCode.valueOf((byte) (flags >> 11 & 0xf)), DnsResponseCode.valueOf((byte) (flags & 0xf)));

        response.setRecursionDesired((flags >> 8 & 1) == 1);
        response.setAuthoritativeAnswer((flags >> 10 & 1) == 1);
        response.setTruncated((flags >> 9 & 1) == 1);
        response.setRecursionAvailable((flags >> 7 & 1) == 1);
        response.setZ(flags >> 4 & 0x7);
        return response;
    }

    private void decodeQuestions(DnsResponse response, ByteBuf buf, int questionCount) throws Exception {
        for (int i = questionCount; i > 0; i --) {
            response.addRecord(DnsSection.QUESTION, recordDecoder.decodeQuestion(buf));
        }
    }

    private void decodeRecords(
            DnsResponse response, DnsSection section, ByteBuf buf, int count) throws Exception {
        for (int i = count; i > 0; i --) {
            final DnsRecord r = recordDecoder.decodeRecord(buf);
            if (r == null) {
                // Truncated response
                break;
            }

            response.addRecord(section, r);
        }
    }
}

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
package io.netty.handler.codec.dns.doh;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.base64.Base64Dialect;
import io.netty.handler.codec.dns.DefaultDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordDecoder;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.util.CharsetUtil;

import java.net.SocketAddress;
import java.net.URL;
import java.util.List;

/**
 * Decode {@link DnsQuery} from {@link FullHttpRequest}
 * or {@link DnsResponse} from {@link FullHttpResponse}.
 */
public final class DoHDecoder extends MessageToMessageDecoder<HttpObject> {

    private final DnsResponseDecoder<SocketAddress> dnsResponseDecoder;
    private final DnsRecordDecoder recordDecoder = DnsRecordDecoder.DEFAULT;

    public DoHDecoder() {
        this.dnsResponseDecoder = new DnsResponseDecoder<SocketAddress>(recordDecoder) {
            @Override
            protected DnsResponse newResponse(SocketAddress sender, SocketAddress recipient, int id,
                                              DnsOpCode opCode, DnsResponseCode responseCode) {
                return new DefaultDnsResponse(id, opCode, responseCode);
            }
        };
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, HttpObject msg, List<Object> out) throws Exception {

        if (msg instanceof FullHttpResponse) {
            FullHttpResponse fullHttpResponse = (FullHttpResponse) msg;
            int statusCode = fullHttpResponse.status().code();

            if (!(statusCode >= 200 && statusCode <= 299)) {
                throw new DecoderException("Request failed, received HTTP Response Code: " + statusCode);
            }

            out.add(dnsResponseDecoder.decode(ctx.channel().remoteAddress(), ctx.channel().localAddress(),
                    fullHttpResponse.content()));
        } else if (msg instanceof FullHttpRequest) {

            FullHttpRequest fullHttpRequest = (FullHttpRequest) msg;

            /*
             * If Method is GET, we'll take Base64-Encoded DNS Query (URL-SAFE) from
             * query string under `dns` variable and decode it. Then we'll pass the ByteBuf
             * of data to DnsQueryBuilder which will create DnsQuery from it.
             *
             * If Method is POST, we'll simply pass the ByteBuf of data to dnsQueryBuilder
             * which will create DnsQuery from it.
             *
             * Since RFC-8484 [https://tools.ietf.org/html/rfc8484] only specifies GET and POST method,
             * all other HTTP Requests will throw an UnsupportedMessageTypeException.
             */
            if (fullHttpRequest.method() == HttpMethod.GET) {
                ByteBuf byteBuf = ctx.alloc().buffer();
                byteBuf.writeCharSequence(
                        new URL("https://" + fullHttpRequest.headers().get(HttpHeaderNames.HOST) +
                        fullHttpRequest.uri()).getQuery().substring(4), CharsetUtil.UTF_8
                );

                ByteBuf dnsQueryBuf = Base64.decode(byteBuf, Base64Dialect.URL_SAFE);
                byteBuf.release(); // Release ByteBuf since we're done with decoding.

                DnsQuery query = dnsQueryBuilder(dnsQueryBuf);
                out.add(query);
            } else if (fullHttpRequest.method() == HttpMethod.POST) {
                DnsQuery query = dnsQueryBuilder(fullHttpRequest.content());
                out.add(query);
            } else {
                throw new UnsupportedMessageTypeException(
                        "HTTP Method is: " + fullHttpRequest.method().name() + ", but only GET and POST is allowed");
            }
        } else {
            // We don't support anything other than FullHttpRequest or FullHttpResponse
            throw new UnsupportedMessageTypeException("HttpObject is not FullHttpRequest or FullHttpResponse");
        }
    }

    private DnsQuery dnsQueryBuilder(ByteBuf buf) throws Exception {
        final int id = buf.readUnsignedShort();

        final int flags = buf.readUnsignedShort();
        if (flags >> 15 == 1) {
            throw new CorruptedFrameException("not a query");
        }

        DnsQuery query = new DefaultDnsQuery(id, DnsOpCode.valueOf((byte) (flags >> 11 & 0xf)));
        query.setRecursionDesired((flags >> 8 & 1) == 1);
        query.setZ(flags >> 4 & 0x7);

        int questionCount = buf.readUnsignedShort();
        int answerCount = buf.readUnsignedShort();
        int authorityRecordCount = buf.readUnsignedShort();
        int additionalRecordCount = buf.readUnsignedShort();

        decodeQuestions(query, buf, questionCount);
        decodeRecords(query, DnsSection.ANSWER, buf, answerCount);
        decodeRecords(query, DnsSection.AUTHORITY, buf, authorityRecordCount);
        decodeRecords(query, DnsSection.ADDITIONAL, buf, additionalRecordCount);

        return query;
    }

    private void decodeQuestions(DnsQuery query, ByteBuf buf, int questionCount) throws Exception {
        for (int i = questionCount; i > 0; i--) {
            query.addRecord(DnsSection.QUESTION, recordDecoder.decodeQuestion(buf));
        }
    }

    private void decodeRecords(DnsQuery query, DnsSection section, ByteBuf buf, int count) throws Exception {
        for (int i = count; i > 0; i--) {
            final DnsRecord r = recordDecoder.decodeRecord(buf);
            if (r == null) {
                // Truncated response
                break;
            }

            query.addRecord(section, r);
        }
    }
}

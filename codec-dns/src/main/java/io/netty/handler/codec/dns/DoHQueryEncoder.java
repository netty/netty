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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.HttpConversionUtil;

import java.net.URL;
import java.util.List;

public class DoHQueryEncoder extends MessageToMessageEncoder<DnsQuery> {

    private final DnsQueryEncoder encoder;
    private final boolean isHTTP2;
    private final URL url;

    public DoHQueryEncoder(URL url) {
        this(DnsRecordEncoder.DEFAULT, false, url);
    }

    public DoHQueryEncoder(boolean isHTTP2, URL url) {
        this(DnsRecordEncoder.DEFAULT, isHTTP2, url);
    }

    public DoHQueryEncoder(DnsRecordEncoder recordEncoder, boolean isHTTP2, URL url) {
        this.encoder = new DnsQueryEncoder(recordEncoder);
        this.isHTTP2 = isHTTP2;
        this.url = url;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, DnsQuery msg, List<Object> out) throws Exception {
        ByteBuf byteBuf = ctx.alloc().buffer();
        encoder.encode(msg, byteBuf);

        FullHttpRequest fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                url.getPath(), byteBuf);
        fullHttpRequest.headers()
                .add(HttpHeaderNames.HOST, url.getHost())
                .add(HttpHeaderNames.CONTENT_TYPE, "application/dns-message")
                .add(HttpHeaderNames.ACCEPT, "application/dns-message")
                .add(HttpHeaderNames.CONTENT_LENGTH, byteBuf.readableBytes());

        if (isHTTP2) {
            fullHttpRequest.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(),
                    HttpScheme.HTTPS.name());
        }

        out.add(fullHttpRequest);
    }
}

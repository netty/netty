/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs.transport;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.handler.codec.sockjs.protocol.MessageFrame;
import io.netty.handler.codec.sockjs.protocol.OpenFrame;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;
import static io.netty.handler.codec.sockjs.transport.HttpResponseBuilder.*;
import static io.netty.handler.codec.sockjs.util.TestChannels.removeLastInboundMessageHandlers;
import static io.netty.util.CharsetUtil.*;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static io.netty.handler.codec.sockjs.util.SockJsAsserts.*;

import java.io.IOException;

import org.junit.Test;

public class HtmlFileTransportTest {

    @Test
    public void writeMissingCallback() {
        final String url = "/test/htmlfile?c=";
        final EmbeddedChannel ch = newHtmlFileChannel(url);
        ch.writeInbound(new DefaultHttpRequest(HTTP_1_1, GET, url));
        final HttpResponse response = ch.readOutbound();
        assertThat(response.status(), equalTo(INTERNAL_SERVER_ERROR));
        assertThat(response.headers().getAndConvert(CONTENT_TYPE), equalTo(CONTENT_TYPE_PLAIN));
        assertNoCache(response);
        assertChannelFinished(ch);
    }

    @Test
    public void write() throws IOException {
        final String url = "/test/htmlfile?c=%63allback";
        final EmbeddedChannel ch = newHtmlFileChannel("/test/htmlfile?c=%63allback");
        ch.writeInbound(new DefaultHttpRequest(HTTP_1_1, GET, url));
        ch.writeOutbound(new OpenFrame());

        final HttpResponse response = ch.readOutbound();
        assertThat(response.status(), equalTo(OK));
        assertThat(response.headers().getAndConvert(CONTENT_TYPE), equalTo(CONTENT_TYPE_HTML));
        assertNoCache(response);

        final HttpContent headerChunk = ch.readOutbound();
        assertThat(headerChunk.content().readableBytes(), is(greaterThan(1024)));
        final String header = headerChunk.content().toString(UTF_8);
        assertThat(header, containsString("var c = parent.callback"));
        headerChunk.release();

        final HttpContent chunk = ch.readOutbound();
        assertThat(chunk.content().toString(UTF_8), equalTo("<script>\np(\"o\");\n</script>\r\n"));
        chunk.release();

        ch.write(new MessageFrame("x"));
        final HttpContent messageContent = ch.readOutbound();
        assertThat(messageContent.content().toString(UTF_8), equalTo("<script>\np(\"a[\\\"x\\\"]\");\n</script>\r\n"));
        messageContent.release();
        assertChannelFinished(ch);
    }

    private static EmbeddedChannel newHtmlFileChannel(final String path) {
        return newStreamingChannel(SockJsConfig.withPrefix("/test").cookiesNeeded().build(), path);
    }

    private static EmbeddedChannel newStreamingChannel(final SockJsConfig config, final String path) {
        final HttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, path);
        final HtmlFileTransport transport = new HtmlFileTransport(config, request);
        return removeLastInboundMessageHandlers(new EmbeddedChannel(transport));
    }

}

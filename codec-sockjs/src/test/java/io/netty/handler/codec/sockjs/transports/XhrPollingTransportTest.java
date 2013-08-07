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
package io.netty.handler.codec.sockjs.transports;

import static io.netty.handler.codec.sockjs.SockJSTestUtil.verifyDefaultResponseHeaders;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.sockjs.Config;
import io.netty.handler.codec.sockjs.protocol.OpenFrame;
import io.netty.util.CharsetUtil;

import org.junit.Test;

public class XhrPollingTransportTest {

    @Test (expected = NullPointerException.class)
    public void constructWithNullConfig() {
        new XhrPollingTransport(null, mock(FullHttpRequest.class));
    }

    @Test (expected = NullPointerException.class)
    public void constructWithNullRequest() {
        new XhrPollingTransport(Config.prefix("/test").build(), null);
    }

    @Test
    public void flush() {
        final Config config = Config.prefix("/test").cookiesNeeded().build();
        final XhrPollingTransport transport = new XhrPollingTransport(config, request("", HttpVersion.HTTP_1_1));
        final EmbeddedChannel channel = new EmbeddedChannel(transport);
        channel.writeOutbound(new OpenFrame());
        final FullHttpResponse response = (FullHttpResponse) channel.readOutbound();
        assertThat(response.getStatus(), equalTo(HttpResponseStatus.OK));
        assertThat(response.content().toString(CharsetUtil.UTF_8), equalTo("o" + '\n'));
        assertThat(response.getProtocolVersion(), equalTo(HttpVersion.HTTP_1_1));
        verifyDefaultResponseHeaders(response, Transports.CONTENT_TYPE_JAVASCRIPT);
        channel.finish();
    }

    private FullHttpRequest request(final String body, HttpVersion httpVersion) {
        final DefaultFullHttpRequest r = new DefaultFullHttpRequest(httpVersion, HttpMethod.GET, "/test");
        if (body != null) {
            r.content().writeBytes(Unpooled.copiedBuffer(body, CharsetUtil.UTF_8));
        }
        return r;
    }

}

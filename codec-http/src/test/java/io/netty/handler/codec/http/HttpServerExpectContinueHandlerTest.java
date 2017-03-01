/*
 * Copyright 2017 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class HttpServerExpectContinueHandlerTest {

    @Test
    public void shouldRespondToExpectedHeader() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpServerExpectContinueHandler() {
            @Override
            protected HttpResponse acceptMessage(HttpRequest request) {
                HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE);
                response.headers().set("foo", "bar");
                return response;
            }
        });
        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        HttpUtil.set100ContinueExpected(request, true);

        channel.writeInbound(request);
        HttpResponse response = channel.readOutbound();

        assertThat(response.status(), is(HttpResponseStatus.CONTINUE));
        assertThat(response.headers().get("foo"), is("bar"));
        ReferenceCountUtil.release(response);

        HttpRequest processedRequest = channel.readInbound();
        assertFalse(processedRequest.headers().contains(HttpHeaderNames.EXPECT));
        ReferenceCountUtil.release(processedRequest);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void shouldAllowCustomResponses() {
        EmbeddedChannel channel = new EmbeddedChannel(
            new HttpServerExpectContinueHandler() {
                @Override
                protected HttpResponse acceptMessage(HttpRequest request) {
                    return null;
                }

                @Override
                protected HttpResponse rejectResponse(HttpRequest request) {
                    return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                            HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE);
                }
            }
        );

        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        HttpUtil.set100ContinueExpected(request, true);

        channel.writeInbound(request);
        HttpResponse response = channel.readOutbound();

        assertThat(response.status(), is(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE));
        ReferenceCountUtil.release(response);

        // request was swallowed
        assertTrue(channel.inboundMessages().isEmpty());
        assertFalse(channel.finishAndReleaseAll());
    }
}

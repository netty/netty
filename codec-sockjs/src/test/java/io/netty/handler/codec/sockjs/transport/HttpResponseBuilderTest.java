/*
 * Copyright 2014 The Netty Project
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

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.sockjs.protocol.OpenFrame;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.util.ReferenceCountUtil.release;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class HttpResponseBuilderTest {

    @Test
    public void buildWithContentString() {
        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/something");
        final FullHttpResponse response = HttpResponseBuilder.responseFor(request)
                .status(HttpResponseStatus.OK)
                .content("payload")
                .buildFullResponse();
        assertThat(response.content().refCnt(), is(1));
        release(response);
        assertThat(response.content().refCnt(), is(0));
    }

    @Test
    public void buildWithContentByteBuf() {
        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/something");
        final FullHttpResponse response = HttpResponseBuilder.responseFor(request)
                .status(HttpResponseStatus.OK)
                .content(Unpooled.copiedBuffer("payload", CharsetUtil.UTF_8))
                .buildFullResponse();
        assertThat(response.content().refCnt(), is(1));
        release(response);
        assertThat(response.content().refCnt(), is(0));
    }

    @Test
    public void buildWithContentOpenFrame() {
        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/something");
        final FullHttpResponse response = HttpResponseBuilder.responseFor(request)
                .status(HttpResponseStatus.OK)
                .content(new OpenFrame().content())
                .buildFullResponse();
        assertThat(response.content().refCnt(), is(1));
        release(response);
        assertThat(response.content().refCnt(), is(1));
    }

    @Test (expected = IllegalArgumentException.class)
    public void buildWithNoContent() {
        final DefaultFullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/something");
        HttpResponseBuilder.responseFor(request).status(HttpResponseStatus.OK).buildFullResponse();
    }
}

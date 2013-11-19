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
package io.netty.handler.codec.sockjs.protocol;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.buffer.Unpooled.unreleasableBuffer;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.sockjs.transport.Transports;
import io.netty.util.CharsetUtil;

public final class Greeting {

    private static final String GREETING = "Welcome to SockJS!";
    private static final ByteBuf CONTENT = unreleasableBuffer(copiedBuffer(GREETING + '\n', CharsetUtil.UTF_8));

    private Greeting() {
    }

    public static boolean matches(final String path) {
        if (path == null) {
            return false;
        }
        return path.isEmpty() || "/".equals(path);
    }

    public static FullHttpResponse response(final HttpRequest request) throws Exception {
        final FullHttpResponse response = new DefaultFullHttpResponse(request.getProtocolVersion(), OK, CONTENT);
        response.headers().set(CONTENT_TYPE, Transports.CONTENT_TYPE_PLAIN);
        return response;
    }

}

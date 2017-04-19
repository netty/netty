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
package io.netty.handler.codec.sockjs.handler;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.CharsetUtil;

import static io.netty.buffer.Unpooled.*;
import static io.netty.handler.codec.sockjs.transport.HttpResponseBuilder.*;

final class Greeting {

    private static final String GREETING = "Welcome to SockJS!";
    private static final ByteBuf CONTENT = unreleasableBuffer(copiedBuffer(GREETING + '\n', CharsetUtil.UTF_8));

    private Greeting() {
    }

    public static boolean matches(final String path) {
        return path != null && path.isEmpty() || "/".equals(path);
    }

    public static FullHttpResponse response(final HttpRequest request) {
        return responseFor(request)
                .ok()
                .content(CONTENT.duplicate())
                .contentType(CONTENT_TYPE_PLAIN)
                .buildFullResponse();
    }

}

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

import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.handler.codec.sockjs.transport.Transports;

import java.util.Random;

public final class Info {
    private static final Random RANDOM = new Random();

    private Info() {
    }

    public static boolean matches(final String path) {
        return path.startsWith("/info");
    }

    public static FullHttpResponse response(final SockJsConfig config, final HttpRequest request) throws Exception {
        final FullHttpResponse response = createResponse(request);
        Transports.setNoCacheHeaders(response);
        Transports.writeContent(response, infoContent(config), "application/json; charset=UTF-8");
        return response;
    }

    private static FullHttpResponse createResponse(final HttpRequest request) {
        return new DefaultFullHttpResponse(request.getProtocolVersion(), HttpResponseStatus.OK);
    }

    private static String infoContent(final SockJsConfig config) {
        final StringBuilder sb = new StringBuilder("{\"websocket\": ").append(config.isWebSocketEnabled());
        sb.append(", \"origins\": [\"*:*\"]");
        sb.append(", \"cookie_needed\": ").append(config.areCookiesNeeded());
        sb.append(", \"entropy\": ").append(RANDOM.nextInt(Integer.MAX_VALUE) + 1);
        sb.append('}');
        return sb.toString();
    }
}

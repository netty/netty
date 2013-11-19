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

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.util.CharsetUtil.UTF_8;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.sockjs.SockJsConfig;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Formatter;
import java.util.regex.Pattern;

/**
 * IFrame is a way to get around problems in browsers where the streaming protocols do not support
 * cross domain communication.
 *
 * The SockJS client library can in these cases issue a request with a path starting
 * with '/iframe'. The class will respond with a iframe that contains SockJS JavaScript
 * which is then able to do cross domain calls.
 */
final class Iframe {

    private static final Pattern PATH_PATTERN = Pattern.compile(".*/iframe[0-9-.a-z_]*.html");
    private static final long ONE_YEAR = 31536000000L;
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
        }
    };
    private static final ThreadLocal<MessageDigest> MESSAGE_DIGEST = new ThreadLocal<MessageDigest>() {
        @Override
        protected MessageDigest initialValue() {
            try {
                return MessageDigest.getInstance("MD5");
            } catch (final NoSuchAlgorithmException e) {
                throw new IllegalStateException("Could not create a new MD5 instance", e);
            }
        }
    };

    private Iframe() {
    }

    public static boolean matches(final String path) {
        return path.startsWith("/iframe");
    }

    public static FullHttpResponse response(final SockJsConfig config, final HttpRequest request) throws Exception {
        final QueryStringDecoder qsd = new QueryStringDecoder(request.getUri());
        final String path = qsd.path();

        if (!PATH_PATTERN.matcher(path).matches()) {
            return createResponse(request, NOT_FOUND, copiedBuffer("Not found", UTF_8));
        }

        if (request.headers().contains(HttpHeaders.Names.IF_NONE_MATCH)) {
            final FullHttpResponse response = createResponse(request, NOT_MODIFIED);
            response.headers().set(HttpHeaders.Names.SET_COOKIE, "JSESSIONID=dummy; path=/");
            return response;
        } else {
            final String content = createContent(config.sockJsUrl());
            final FullHttpResponse response = createResponse(request, OK, copiedBuffer(content, UTF_8));
            response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=UTF-8");
            response.headers().set(HttpHeaders.Names.CACHE_CONTROL, "max-age=31536000, public");
            response.headers().set(HttpHeaders.Names.EXPIRES, generateExpires());
            final String etag = '\"' + generateMd5(content) + '\"';
            response.headers().set(HttpHeaders.Names.ETAG, etag);
            return response;
        }
    }

    private static String generateExpires() {
        return DATE_FORMAT.get().format(new Date(System.currentTimeMillis() + ONE_YEAR));
    }

    private static FullHttpResponse createResponse(final HttpRequest request, final HttpResponseStatus status) {
        return new DefaultFullHttpResponse(request.getProtocolVersion(), status);
    }

    private static FullHttpResponse createResponse(final HttpRequest request, final HttpResponseStatus status,
            final ByteBuf content) {
        return new DefaultFullHttpResponse(request.getProtocolVersion(), status, content);
    }

    private static String createContent(final String url) {
        return "<!DOCTYPE html>\n" +
        "<html>\n" +
        "<head>\n" +
        "  <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\" />\n" +
        "  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" +
        "  <script>\n" +
        "    document.domain = document.domain;\n" +
        "    _sockjs_onload = function(){SockJS.bootstrap_iframe();};\n" +
        "  </script>\n" +
        "  <script src=\"" + url + "\"></script>\n" +
        "</head>\n" +
        "<body>\n" +
        "  <h2>Don't panic!</h2>\n" +
        "  <p>This is a SockJS hidden iframe. It's used for cross domain magic.</p>\n" +
        "</body>\n" +
        "</html>";
    }

    private static String generateMd5(final String value) throws Exception {
        final byte[] digest = MESSAGE_DIGEST.get().digest(value.getBytes(UTF_8));
        Formatter formatter = null;
        try {
            formatter = new Formatter();
            for (byte b : digest) {
                formatter.format("%02x", b);
            }
            return formatter.toString().toLowerCase();
        } finally {
            if (formatter != null) {
                formatter.close();
            }
        }
    }

}

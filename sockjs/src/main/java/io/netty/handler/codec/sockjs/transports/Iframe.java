/*
 * Copyright 2012 The Netty Project
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

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.sockjs.Config;
import io.netty.util.CharsetUtil;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Formatter;
import java.util.regex.Pattern;

public final class Iframe {

    private static final Pattern PATH_PATTERN = Pattern.compile(".*/iframe[0-9-.a-z_]*.html");

    private Iframe() {
    }

    public static boolean matches(final String path) {
        return path.startsWith("/iframe");
    }

    public static FullHttpResponse response(final Config config, final HttpRequest request) throws Exception {
        final QueryStringDecoder qsd = new QueryStringDecoder(request.getUri());
        final String path = qsd.path();

        if (!PATH_PATTERN.matcher(path).matches()) {
            final FullHttpResponse response = createResponse(request, HttpResponseStatus.NOT_FOUND);
            response.content().writeBytes(Unpooled.copiedBuffer("Not found", CharsetUtil.UTF_8));
            return response;
        }

        if (request.headers().contains(HttpHeaders.Names.IF_NONE_MATCH)) {
            final FullHttpResponse response = createResponse(request, HttpResponseStatus.NOT_MODIFIED);
            response.headers().set(HttpHeaders.Names.SET_COOKIE, "JSESSIONID=dummy; path=/");
            return response;
        } else {
            final FullHttpResponse response = createResponse(request, HttpResponseStatus.OK);
            response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=UTF-8");
            response.headers().set(HttpHeaders.Names.CACHE_CONTROL, "max-age=31536000, public");
            response.headers().set(HttpHeaders.Names.EXPIRES, generateExpires());
            final String content = createContent(config.sockjsUrl());
            final String etag = "\"" + generateMd5(content) + "\"";
            response.headers().set(HttpHeaders.Names.ETAG, etag);
            response.content().writeBytes(Unpooled.copiedBuffer(content, CharsetUtil.UTF_8));
            return response;
        }
    }

    private static String generateExpires() {
        final long oneYear = 365 * 24 * 60 * 60 * 1000;
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
        return simpleDateFormat.format(new Date(System.currentTimeMillis() + oneYear));
    }

    private static FullHttpResponse createResponse(final HttpRequest request, final HttpResponseStatus status) {
        return new DefaultFullHttpResponse(request.getProtocolVersion(), status);
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
        final byte[] bytesToBeEncrypted = value.getBytes("UTF-8");
        final MessageDigest md = MessageDigest.getInstance("MD5");
        final byte[] digest = md.digest(bytesToBeEncrypted);
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

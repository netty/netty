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

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.sockjs.SockJsConfig;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Formatter;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpHeaders.Names.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaders.Names.ETAG;
import static io.netty.handler.codec.http.HttpHeaders.Names.EXPIRES;
import static io.netty.handler.codec.http.HttpHeaders.Names.IF_NONE_MATCH;
import static io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;
import static io.netty.handler.codec.sockjs.transport.HttpResponseBuilder.*;
import static io.netty.util.CharsetUtil.*;

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
        return path != null && path.startsWith("/iframe");
    }

    public static HttpResponse response(final SockJsConfig config, final HttpRequest request) throws Exception {
        final QueryStringDecoder qsd = new QueryStringDecoder(request.uri());
        if (!PATH_PATTERN.matcher(qsd.path()).matches()) {
            return responseFor(request)
                    .notFound()
                    .content("Not found")
                    .contentType(CONTENT_TYPE_PLAIN)
                    .buildFullResponse();
        }

        if (request.headers().contains(IF_NONE_MATCH)) {
            return responseFor(request)
                    .notModified()
                    .header(SET_COOKIE, "JSESSIONID=dummy; path=/")
                    .buildResponse();
        } else {
            final String content = createContent(config.sockJsUrl());
            return responseFor(request)
                    .ok()
                    .content(content)
                    .contentType(CONTENT_TYPE_HTML)
                    .header(CACHE_CONTROL, "max-age=31536000, public")
                    .header(EXPIRES, generateExpires())
                    .header(ETAG, '\"' + generateMd5(content) + '\"')
                    .buildFullResponse();
        }
    }

    private static String generateExpires() {
        return DATE_FORMAT.get().format(new Date(System.currentTimeMillis() + ONE_YEAR));
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

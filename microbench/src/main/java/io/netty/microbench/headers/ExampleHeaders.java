/*
 * Copyright 2015 The Netty Project
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
package io.netty.microbench.headers;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class ExampleHeaders {

    public enum HeaderExample {
        THREE,
        FIVE,
        SIX,
        EIGHT,
        ELEVEN,
        TWENTYTWO,
        THIRTY
    }

    public static final Map<HeaderExample, Map<String, String>> EXAMPLES =
            new EnumMap<HeaderExample, Map<String, String>>(HeaderExample.class);

    static {
        Map<String, String> header = new HashMap<String, String>();
        header.put(":method", "GET");
        header.put(":scheme", "https");
        header.put(":path", "/index.html");
        EXAMPLES.put(HeaderExample.THREE, header);

        // Headers used by Norman's HTTP benchmarks with wrk
        header = new HashMap<String, String>();
        header.put("Method", "GET");
        header.put("Path", "/plaintext");
        header.put("Host", "localhost");
        header.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        header.put("Connection", "keep-alive");
        EXAMPLES.put(HeaderExample.FIVE, header);

        header = new HashMap<String, String>();
        header.put(":authority", "127.0.0.1:33333");
        header.put(":method", "POST");
        header.put(":path", "/grpc.testing.TestService/UnaryCall");
        header.put(":scheme", "http");
        header.put("content-type", "application/grpc");
        header.put("te", "trailers");
        EXAMPLES.put(HeaderExample.SIX, header);

        header = new HashMap<String, String>();
        header.put(":method", "POST");
        header.put(":scheme", "http");
        header.put(":path", "/google.pubsub.v2.PublisherService/CreateTopic");
        header.put(":authority", "pubsub.googleapis.com");
        header.put("grpc-timeout", "1S");
        header.put("content-type", "application/grpc+proto");
        header.put("grpc-encoding", "gzip");
        header.put("authorization", "Bearer y235.wef315yfh138vh31hv93hv8h3v");
        EXAMPLES.put(HeaderExample.EIGHT, header);

        header = new HashMap<String, String>();
        header.put(":host", "twitter.com");
        header.put(":method", "GET");
        header.put(":path", "/");
        header.put(":scheme", "https");
        header.put(":version", "HTTP/1.1");
        header.put("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        header.put("accept-encoding", "gzip, deflate, sdch");
        header.put("accept-language", "en-US,en;q=0.8");
        header.put("cache-control", "max-age=0");
        header.put("cookie", "noneofyourbusiness");
        header.put("user-agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko)");
        EXAMPLES.put(HeaderExample.ELEVEN, header);

        header = new HashMap<String, String>();
        header.put("cache-control", "no-cache, no-store, must-revalidate, pre-check=0, post-check=0");
        header.put("content-encoding", "gzip");
        header.put("content-security-policy", "default-src https:; connect-src https:;");
        header.put("content-type", "text/html;charset=utf-8");
        header.put("date", "Wed, 22 Apr 2015 00:40:28 GMT");
        header.put("expires", "Tue, 31 Mar 1981 05:00:00 GMT");
        header.put("last-modified", "Wed, 22 Apr 2015 00:40:28 GMT");
        header.put("ms", "ms");
        header.put("pragma", "no-cache");
        header.put("server", "tsa_b");
        header.put("set-cookie", "noneofyourbusiness");
        header.put("status", "200 OK");
        header.put("strict-transport-security", "max-age=631138519");
        header.put("version", "HTTP/1.1");
        header.put("x-connection-hash", "e176fe40accc1e2c613a34bc1941aa98");
        header.put("x-content-type-options", "nosniff");
        header.put("x-frame-options", "SAMEORIGIN");
        header.put("x-response-time", "22");
        header.put("x-transaction", "a54142ede693444d9");
        header.put("x-twitter-response-tags", "BouncerCompliant");
        header.put("x-ua-compatible", "IE=edge,chrome=1");
        header.put("x-xss-protection", "1; mode=block");
        EXAMPLES.put(HeaderExample.TWENTYTWO, header);

        header = new HashMap<String, String>();
        header.put("Cache-Control", "no-cache");
        header.put("Content-Encoding", "gzip");
        header.put("Content-Security-Policy", "default-src *; script-src assets-cdn.github.com ...");
        header.put("Content-Type", "text/html; charset=utf-8");
        header.put("Date", "Fri, 10 Apr 2015 02:15:38 GMT");
        header.put("Server", "GitHub.com");
        header.put("Set-Cookie", "_gh_sess=eyJzZXNza...; path=/; secure; HttpOnly");
        header.put("Status", "200 OK");
        header.put("Strict-Transport-Security", "max-age=31536000; includeSubdomains; preload");
        header.put("Transfer-Encoding", "chunked");
        header.put("Vary", "X-PJAX");
        header.put("X-Content-Type-Options", "nosniff");
        header.put("X-Frame-Options", "deny");
        header.put("X-GitHub-Request-Id", "1");
        header.put("X-GitHub-Session-Id", "1");
        header.put("X-GitHub-User", "buchgr");
        header.put("X-Request-Id", "28f245e02fc872dcf7f31149e52931dd");
        header.put("X-Runtime", "0.082529");
        header.put("X-Served-By", "b9c2a233f7f3119b174dbd8be2");
        header.put("X-UA-Compatible", "IE=Edge,chrome=1");
        header.put("X-XSS-Protection", "1; mode=block");
        header.put("Via", "http/1.1 ir50.fp.bf1.yahoo.com (ApacheTrafficServer)");
        header.put("Content-Language", "en");
        header.put("Connection", "keep-alive");
        header.put("Pragma", "no-cache");
        header.put("Expires", "Sat, 01 Jan 2000 00:00:00 GMT");
        header.put("X-Moose", "majestic");
        header.put("x-ua-compatible", "IE=edge");
        header.put("CF-Cache-Status", "HIT");
        header.put("CF-RAY", "6a47f4f911e3-");
        EXAMPLES.put(HeaderExample.THIRTY, header);
    }

    private ExampleHeaders() {
    }
}

/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http2;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.AsciiString;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import static io.netty.util.internal.StringUtil.isNullOrEmpty;

@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class Http2RequestTargetConversionBenchmark extends AbstractMicrobenchmark {

    @Param
    public RequestTargetType requestTargetType;

    private HttpRequest request;

    @Setup
    public void setup() {
        request = new DefaultHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.GET,
                requestTargetType.requestTarget,
                new DefaultHttpHeaders(),
                false);
        request.headers().set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), HttpScheme.HTTP.name());
    }

    @Benchmark
    public void newConversion(Blackhole bh) {
        bh.consume(HttpConversionUtil.toHttp2Headers(request, false));
    }

    @Benchmark
    public void oldUriConversion(Blackhole bh) {
        bh.consume(oldToHttp2Headers(request));
    }

    public enum RequestTargetType {
        ORIGIN("/orders/123/items?expand=details"),
        ABSOLUTE("http://example.com/orders/123/items?expand=details#section"),
        ABSOLUTE_NO_PATH("http://example.com?next=/home#section"),
        ABSOLUTE_NO_AUTHORITY("http://?x=1#frag"),
        SCHEME_ONLY_ABSOLUTE_PATH("http:/orders/123/items?expand=details");

        final String requestTarget;

        RequestTargetType(String requestTarget) {
            this.requestTarget = requestTarget;
        }
    }

    private static Http2Headers oldToHttp2Headers(final HttpRequest request) {
        HttpHeaders inHeaders = request.headers();
        Http2Headers out = new DefaultHttp2Headers(false, inHeaders.size());
        String host = inHeaders.getAsString(HttpHeaderNames.HOST);
        if (HttpUtil.isOriginForm(request.uri()) || HttpUtil.isAsteriskForm(request.uri())) {
            out.path(new AsciiString(request.uri()));
            oldSetHttp2Scheme(inHeaders, URI.create(""), out);
        } else {
            URI requestTargetUri = URI.create(request.uri());
            out.path(oldToHttp2Path(requestTargetUri));
            host = isNullOrEmpty(host) ? requestTargetUri.getAuthority() : host;
            oldSetHttp2Scheme(inHeaders, requestTargetUri, out);
        }
        HttpConversionUtil.setHttp2Authority(host, out);
        out.method(request.method().asciiName());
        HttpConversionUtil.toHttp2Headers(inHeaders, out);
        return out;
    }

    private static AsciiString oldToHttp2Path(final URI uri) {
        StringBuilder pathBuilder = new StringBuilder();
        if (!isNullOrEmpty(uri.getRawPath())) {
            pathBuilder.append(uri.getRawPath());
        }
        if (!isNullOrEmpty(uri.getRawQuery())) {
            pathBuilder.append('?');
            pathBuilder.append(uri.getRawQuery());
        }
        if (!isNullOrEmpty(uri.getRawFragment())) {
            pathBuilder.append('#');
            pathBuilder.append(uri.getRawFragment());
        }
        String path = pathBuilder.toString();
        return path.isEmpty() ? new AsciiString("/") : new AsciiString(path);
    }

    private static void oldSetHttp2Scheme(final HttpHeaders in, final URI uri, final Http2Headers out) {
        String value = uri.getScheme();
        if (!isNullOrEmpty(value)) {
            out.scheme(new AsciiString(value));
            return;
        }

        CharSequence cValue = in.get(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text());
        if (cValue != null) {
            out.scheme(AsciiString.of(cValue));
            return;
        }

        if (uri.getPort() == HttpScheme.HTTPS.port()) {
            out.scheme(HttpScheme.HTTPS.name());
        } else if (uri.getPort() == HttpScheme.HTTP.port()) {
            out.scheme(HttpScheme.HTTP.name());
        } else {
            throw new IllegalArgumentException(
                    ":scheme must be specified. see https://tools.ietf.org/html/rfc7540#section-8.1.2.3");
        }
    }
}

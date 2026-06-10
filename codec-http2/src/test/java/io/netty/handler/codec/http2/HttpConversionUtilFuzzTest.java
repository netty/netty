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

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;

import static io.netty.util.internal.StringUtil.isNullOrEmpty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "JAZZER_FUZZ", matches = "1")
public class HttpConversionUtilFuzzTest {

    @FuzzTest(maxDuration = "30s")
    public void currentConversionMatchesOldUriBasedConversion(final FuzzedDataProvider data) {
        String requestTarget = data.consumeString(128);
        HttpRequest msg = new DefaultHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.GET,
                requestTarget,
                new DefaultHttpHeaders(),
                false);
        msg.headers().set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), HttpScheme.HTTP.name());

        Http2Headers oldHeaders;
        try {
            oldHeaders = oldToHttp2Headers(msg);
        } catch (IllegalArgumentException e) {
            return;
        }

        Http2Headers newHeaders = HttpConversionUtil.toHttp2Headers(msg, false);
        if (!oldHeaders.path().equals(newHeaders.path())) {
            assertTrue(isKnownPathCompatibilityException(requestTarget), requestTarget);
        }
        assertEquals(oldHeaders.scheme(), newHeaders.scheme());
        assertEquals(oldHeaders.authority(), newHeaders.authority());
        assertEquals(oldHeaders.method(), newHeaders.method());
    }

    private static boolean isKnownPathCompatibilityException(final String requestTarget) {
        // The old URI-based oracle only diverges on a few legacy RFC 2396-style forms:
        // 1) Opaque scheme-specific targets like "x:y" or "x:foo" where URI path becomes "/".
        // 2) Absolute-path targets with a scheme but no authority like "x:/path", where the old
        //    URI oracle strips the scheme while the new path logic preserves the raw path.
        // 3) Absolute-form targets with no path slash, where Vert.x parsePath returns "/" before
        //    parseQuery is appended.
        // 4) Malformed fragment-before-query targets like "#?", where Vert.x-shaped parsePath keeps
        //    '#' in the path but URI treats the following '?' as fragment data.
        // 5) Empty query/fragment delimiters like "?#", which URI drops while Vert.x-shaped parsing
        //    keeps the delimiters as raw path/query syntax.
        return isOpaqueSchemeSpecificPart(requestTarget) || isSchemeOnlyAbsolutePath(requestTarget)
                || isAbsoluteFormWithoutPathSlash(requestTarget) || hasFragmentBeforeQuery(requestTarget)
                || hasEmptyQueryAndFragmentDelimiters(requestTarget);
    }

    private static boolean isOpaqueSchemeSpecificPart(final String requestTarget) {
        int schemeEnd = requestTarget.indexOf(':');
        return HttpConversionUtil.isValidScheme(requestTarget, schemeEnd) && schemeEnd + 1 < requestTarget.length()
                && requestTarget.charAt(schemeEnd + 1) != '/';
    }

    private static boolean isSchemeOnlyAbsolutePath(final String requestTarget) {
        int schemeEnd = requestTarget.indexOf(':');
        return HttpConversionUtil.isValidScheme(requestTarget, schemeEnd) && schemeEnd + 1 < requestTarget.length()
                && requestTarget.charAt(schemeEnd + 1) == '/'
                && !HttpConversionUtil.hasSchemeAndAuthority(requestTarget)
                && (schemeEnd + 2 >= requestTarget.length() || requestTarget.charAt(schemeEnd + 2) != '/');
    }

    private static boolean isAbsoluteFormWithoutPathSlash(final String requestTarget) {
        int schemeEnd = requestTarget.indexOf("://");
        if (!HttpConversionUtil.hasSchemeAndAuthority(requestTarget)) {
            return false;
        }
        int authorityStart = schemeEnd + 3;
        int pathStart = requestTarget.indexOf('/', authorityStart);
        int delimiter = HttpConversionUtil.queryOrFragmentStart(requestTarget, authorityStart);
        return pathStart == -1 || (delimiter != -1 && delimiter < pathStart);
    }

    private static boolean hasFragmentBeforeQuery(final String requestTarget) {
        int fragmentStart = requestTarget.indexOf('#');
        int queryStart = requestTarget.indexOf('?');
        return fragmentStart != -1 && queryStart != -1 && fragmentStart < queryStart;
    }

    private static boolean hasEmptyQueryAndFragmentDelimiters(final String requestTarget) {
        return requestTarget.endsWith("?#");
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

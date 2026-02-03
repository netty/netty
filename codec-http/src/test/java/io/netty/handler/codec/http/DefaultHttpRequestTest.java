/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.util.AsciiString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.SplittableRandom;
import java.util.stream.Stream;

import static io.netty.handler.codec.http.HttpHeadersTestUtils.of;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultHttpRequestTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost/\r\n",
            "/r\r\n?q=1",
            "http://localhost/\r\n?q=1",
            "/r\r\n/?q=1",
            "http://localhost/\r\n/?q=1",
            "/r\r\n",
            "http://localhost/ HTTP/1.1\r\n\r\nPOST /p HTTP/1.1\r\n\r\n",
            "/r HTTP/1.1\r\n\r\nPOST /p HTTP/1.1\r\n\r\n",
            "/ path",
            "/path ",
            " /path",
            "http://localhost/ ",
            " http://localhost/",
            "http://local host/",
    })
    void constructorMustRejectIllegalUrisByDefault(String uri) {
        assertThrows(IllegalArgumentException.class, () ->
                new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri));
    }

    public static Stream<String> validUris() {
        String pdigit = "123456789";
        String digit = '0' + pdigit;
        String digitcolon = digit + ':';
        String alpha = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String alphanum = alpha + digit;
        String alphanumdot = alphanum + '.';
        String unreserved = alphanumdot + "-_~";
        String subdelims = "$&%=!+,;'()";
        String userinfochars = unreserved + subdelims + ':';
        String pathchars = unreserved + '/';
        String querychars = pathchars + subdelims + '?';
        return new SplittableRandom().longs(1000)
                .mapToObj(seed -> {
                    SplittableRandom rng = new SplittableRandom(seed);
                    String start;
                    String path;
                    String query;
                    String fragment;
                    if (rng.nextBoolean()) {
                        String scheme = rng.nextBoolean() ? "http://" : "HTTP://";
                        String userinfo = rng.nextBoolean() ? "" : pick(rng, userinfochars, 1, 8) + '@';
                        String host;
                        String port;
                        switch (rng.nextInt(3)) {
                            case 0:
                                host = pick(rng, alphanum, 1, 1) + pick(rng, alphanumdot, 1, 5);
                                break;
                            case 1:
                                host = pick(rng, pdigit, 1, 1) + pick(rng, digit, 0, 2) + '.' +
                                        pick(rng, pdigit, 1, 1) + pick(rng, digit, 0, 2) + '.' +
                                        pick(rng, pdigit, 1, 1) + pick(rng, digit, 0, 2) + '.' +
                                        pick(rng, pdigit, 1, 1) + pick(rng, digit, 0, 2);
                                break;
                            default:
                                host = '[' + pick(rng, digitcolon, 1, 8) + ']';
                                break;
                        }
                        if (rng.nextBoolean()) {
                            port = ':' + pick(rng, pdigit, 1, 1) + pick(rng, digit, 0, 4);
                        } else {
                            port = "";
                        }
                        start = scheme + userinfo + host + port;
                    } else {
                        start = "";
                    }
                    path = '/' + pick(rng, pathchars, 0, 8);
                    if (rng.nextBoolean()) {
                        query = '?' + pick(rng, querychars, 0, 8);
                    } else {
                        query = "";
                    }
                    if (rng.nextBoolean()) {
                        fragment = '#' + pick(rng, querychars, 0, 8);
                    } else {
                        fragment = "";
                    }
                    return start + path + query + fragment;
                });
    }

    private static String pick(SplittableRandom rng, String cs, int lowerBound, int upperBound) {
        int length = rng.nextInt(lowerBound, upperBound + 1);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(cs.charAt(rng.nextInt(cs.length())));
        }
        return sb.toString();
    }

    @ParameterizedTest
    @MethodSource("validUris")
    void constructorMustAcceptValidUris(String uri) {
        new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "GET ",
            " GET",
            "G ET",
            " GET ",
            "GET\r",
            "GET\n",
            "GET\r\n",
            "GE\rT",
            "GE\nT",
            "GE\r\nT",
            "\rGET",
            "\nGET",
            "\r\nGET",
            " \r\nGET",
            "\r \nGET",
            "\r\n GET",
            "\r\nGET ",
            "\nGET ",
            "\rGET ",
            "\r GET",
            " \rGET",
            "\nGET ",
            "\n GET",
            " \nGET",
            "GET \n",
            "GET \r",
            " GET\r",
            " GET\r",
            "GET \n",
            " GET\n",
            " GET\n",
            "GE\nT ",
            "GE\rT ",
            " GE\rT",
            " GE\rT",
            "GE\nT ",
            " GE\nT",
            " GE\nT",
    })
    void constructorMustRejectIllegalHttpMethodByDefault(String method) {
        assertThrows(IllegalArgumentException.class, () -> {
            new DefaultHttpRequest(HttpVersion.HTTP_1_0, new HttpMethod("GET") {
                @Override
                public AsciiString asciiName() {
                    return new AsciiString(method);
                }
            }, "/");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "GET",
            "POST",
            "PUT",
            "HEAD",
            "DELETE",
            "OPTIONS",
            "CONNECT",
            "TRACE",
            "PATCH",
            "QUERY"
    })
    void constructorMustAcceptAllHttpMethods(String method) {
        new DefaultHttpRequest(HttpVersion.HTTP_1_0, new HttpMethod("GET") {
            @Override
            public AsciiString asciiName() {
                return new AsciiString(method);
            }
        }, "/");

        new DefaultHttpRequest(HttpVersion.HTTP_1_0, new HttpMethod(method), "/");
    }

    @Test
    public void testHeaderRemoval() {
        HttpMessage m = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        HttpHeaders h = m.headers();

        // Insert sample keys.
        for (int i = 0; i < 1000; i ++) {
            h.set(of(String.valueOf(i)), AsciiString.EMPTY_STRING);
        }

        // Remove in reversed order.
        for (int i = 999; i >= 0; i --) {
            h.remove(of(String.valueOf(i)));
        }

        // Check if random access returns nothing.
        for (int i = 0; i < 1000; i ++) {
            assertNull(h.get(of(String.valueOf(i))));
        }

        // Check if sequential access returns nothing.
        assertTrue(h.isEmpty());
    }
}

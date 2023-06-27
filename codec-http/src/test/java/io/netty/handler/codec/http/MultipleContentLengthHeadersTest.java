/*
 * Copyright 2020 The Netty Project
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

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_INITIAL_BUFFER_SIZE;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_CHUNK_SIZE;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_HEADER_SIZE;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH;
import static io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_VALIDATE_HEADERS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;

public class MultipleContentLengthHeadersTest {

    static Collection<Object[]> parameters() {
        return Arrays.asList(new Object[][] {
                { false, false, false },
                { false, false, true },
                { false, true, false },
                { false, true, true },
                { true, false, false },
                { true, false, true },
                { true, true, false },
                { true, true, true }
        });
    }

    private static EmbeddedChannel newChannel(boolean allowDuplicateContentLengths) {
        HttpRequestDecoder decoder = new HttpRequestDecoder(
                DEFAULT_MAX_INITIAL_LINE_LENGTH,
                DEFAULT_MAX_HEADER_SIZE,
                DEFAULT_MAX_CHUNK_SIZE,
                DEFAULT_VALIDATE_HEADERS,
                DEFAULT_INITIAL_BUFFER_SIZE,
                allowDuplicateContentLengths);
        return new EmbeddedChannel(decoder);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testMultipleContentLengthHeadersBehavior(boolean allowDuplicateContentLengths,
                                                         boolean sameValue, boolean singleField) {
        EmbeddedChannel channel = newChannel(allowDuplicateContentLengths);
        String requestStr = setupRequestString(sameValue, singleField);
        assertThat(channel.writeInbound(Unpooled.copiedBuffer(requestStr, CharsetUtil.US_ASCII)), is(true));
        HttpRequest request = channel.readInbound();

        if (allowDuplicateContentLengths) {
            if (sameValue) {
                assertValid(request);
                List<String> contentLengths = request.headers().getAll(HttpHeaderNames.CONTENT_LENGTH);
                assertThat(contentLengths, contains("1"));
                LastHttpContent body = channel.readInbound();
                assertThat(body.content().readableBytes(), is(1));
                assertThat(body.content().readCharSequence(1, CharsetUtil.US_ASCII).toString(), is("a"));
            } else {
                assertInvalid(request);
            }
        } else {
            assertInvalid(request);
        }
        assertThat(channel.finish(), is(false));
    }

    private static String setupRequestString(boolean sameValue, boolean singleField) {
        String firstValue = "1";
        String secondValue = sameValue ? firstValue : "2";
        String contentLength;
        if (singleField) {
            contentLength = "Content-Length: " + firstValue + ", " + secondValue + "\r\n\r\n";
        } else {
            contentLength = "Content-Length: " + firstValue + "\r\n" +
                            "Content-Length: " + secondValue + "\r\n\r\n";
        }
        return "PUT /some/path HTTP/1.1\r\n" +
               contentLength +
               "ab";
    }

    @Test
    public void testDanglingComma() {
        EmbeddedChannel channel = newChannel(false);
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                            "Content-Length: 1,\r\n" +
                            "Connection: close\n\n" +
                            "ab";
        assertThat(channel.writeInbound(Unpooled.copiedBuffer(requestStr, CharsetUtil.US_ASCII)), is(true));
        HttpRequest request = channel.readInbound();
        assertInvalid(request);
        assertThat(channel.finish(), is(false));
    }

    private static void assertValid(HttpRequest request) {
        assertThat(request.decoderResult().isFailure(), is(false));
    }

    private static void assertInvalid(HttpRequest request) {
        assertThat(request.decoderResult().isFailure(), is(true));
        assertThat(request.decoderResult().cause(), instanceOf(IllegalArgumentException.class));
        assertThat(request.decoderResult().cause().getMessage(),
                   containsString("Multiple Content-Length values found"));
    }
}

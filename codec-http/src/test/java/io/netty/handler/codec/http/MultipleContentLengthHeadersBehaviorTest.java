/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpDecoderOption.MultipleContentLengthHeadersBehavior;
import io.netty.util.CharsetUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static io.netty.handler.codec.http.HttpDecoderOption.MULTIPLE_CONTENT_LENGTH_HEADERS_BEHAVIOR;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;

@RunWith(Parameterized.class)
public class MultipleContentLengthHeadersBehaviorTest {

    private final MultipleContentLengthHeadersBehavior behavior;
    private final boolean sameValue;
    private final boolean singleField;

    private EmbeddedChannel channel;
    private HttpRequest request;

    @Parameters
    public static Collection<Object[]> parameters() {
        return Arrays.asList(new Object[][] {
                { MultipleContentLengthHeadersBehavior.ALWAYS_REJECT, false, false },
                { MultipleContentLengthHeadersBehavior.ALWAYS_REJECT, false, true },
                { MultipleContentLengthHeadersBehavior.ALWAYS_REJECT, true, false },
                { MultipleContentLengthHeadersBehavior.ALWAYS_REJECT, true, true },
                { MultipleContentLengthHeadersBehavior.IF_DIFFERENT_REJECT_ELSE_DEDUPE, false, false },
                { MultipleContentLengthHeadersBehavior.IF_DIFFERENT_REJECT_ELSE_DEDUPE, false, true },
                { MultipleContentLengthHeadersBehavior.IF_DIFFERENT_REJECT_ELSE_DEDUPE, true, false },
                { MultipleContentLengthHeadersBehavior.IF_DIFFERENT_REJECT_ELSE_DEDUPE, true, true },
                { MultipleContentLengthHeadersBehavior.IF_DIFFERENT_REJECT_ELSE_ALLOW, false, false },
                { MultipleContentLengthHeadersBehavior.IF_DIFFERENT_REJECT_ELSE_ALLOW, false, true },
                { MultipleContentLengthHeadersBehavior.IF_DIFFERENT_REJECT_ELSE_ALLOW, true, false },
                { MultipleContentLengthHeadersBehavior.IF_DIFFERENT_REJECT_ELSE_ALLOW, true, true }
        });
    }

    public MultipleContentLengthHeadersBehaviorTest(
            MultipleContentLengthHeadersBehavior behavior, boolean sameValue, boolean singleField) {
        this.behavior = behavior;
        this.sameValue = sameValue;
        this.singleField = singleField;
    }

    @Before
    public void setUp() {
        HttpRequestDecoder decoder = HttpRequestDecoder.builder()
                                                       .option(MULTIPLE_CONTENT_LENGTH_HEADERS_BEHAVIOR, behavior)
                                                       .build();
        channel = new EmbeddedChannel(decoder);
    }

    @Test
    public void testMultipleContentLengthHeadersBehavior() {
        String requestStr = setupRequestString();
        assertThat(channel.writeInbound(Unpooled.copiedBuffer(requestStr, CharsetUtil.US_ASCII)), is(true));
        request = channel.readInbound();

        if (behavior == MultipleContentLengthHeadersBehavior.ALWAYS_REJECT) {
            assertInvalid(request);
        } else if (behavior == MultipleContentLengthHeadersBehavior.IF_DIFFERENT_REJECT_ELSE_DEDUPE) {
            if (sameValue) {
                assertValid(request);
                List<String> contentLengths = request.headers().getAll(HttpHeaderNames.CONTENT_LENGTH);
                assertThat(contentLengths, contains("1"));
            } else {
                assertInvalid(request);
            }
        } else if (behavior == MultipleContentLengthHeadersBehavior.IF_DIFFERENT_REJECT_ELSE_ALLOW) {
            if (sameValue) {
                assertValid(request);
                List<String> contentLengths = request.headers().getAll(HttpHeaderNames.CONTENT_LENGTH);
                if (singleField) {
                    assertThat(contentLengths, contains("1, 1"));
                } else {
                    assertThat(contentLengths, contains("1", "1"));
                }
            } else {
                assertInvalid(request);
            }
        }
        assertThat(channel.finish(), is(false));
    }

    private String setupRequestString() {
        String firstValue = "1";
        String secondValue = sameValue? firstValue : "2";
        String contentLength;
        if (singleField) {
            contentLength = "Content-Length: " + firstValue + ", " + secondValue + "\r\n\r\n";
        } else {
            contentLength = "Content-Length: " + firstValue + "\r\n" +
                            "Content-Length: " + secondValue + "\r\n\r\n";
        }
        return "PUT /some/path HTTP/1.1\r\n" +
               contentLength +
               "b";
    }

    @Test
    public void testDanglingComma() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                            "Content-Length: 1,\r\n" +
                            "Connection: close\n\n" +
                            "b";
        assertThat(channel.writeInbound(Unpooled.copiedBuffer(requestStr, CharsetUtil.US_ASCII)), is(true));
        request = channel.readInbound();
        assertInvalid(request);
        assertThat(channel.finish(), is(false));
    }

    private void assertValid(HttpRequest request) {
        assertThat(request.decoderResult().isFailure(), is(false));
        assertThat(channel.readInbound(), instanceOf(LastHttpContent.class));
    }

    private static void assertInvalid(HttpRequest request) {
        assertThat(request.decoderResult().isFailure(), is(true));
        assertThat(request.decoderResult().cause(), instanceOf(IllegalArgumentException.class));
        assertThat(request.decoderResult().cause().getMessage(), containsString("Multiple"));
        assertThat(request.decoderResult().cause().getMessage(), containsString("Content-Length headers found"));
    }
}

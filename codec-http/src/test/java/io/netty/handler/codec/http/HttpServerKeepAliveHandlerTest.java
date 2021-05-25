/*
 * Copyright 2016 The Netty Project
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

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.StringUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;

import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpHeaderValues.MULTIPART_MIXED;
import static io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpUtil.isContentLengthSet;
import static io.netty.handler.codec.http.HttpUtil.isKeepAlive;
import static io.netty.handler.codec.http.HttpUtil.setContentLength;
import static io.netty.handler.codec.http.HttpUtil.setKeepAlive;
import static io.netty.handler.codec.http.HttpUtil.setTransferEncodingChunked;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(Parameterized.class)
public class HttpServerKeepAliveHandlerTest {
    private static final String REQUEST_KEEP_ALIVE = "REQUEST_KEEP_ALIVE";
    private static final int NOT_SELF_DEFINED_MSG_LENGTH = 0;
    private static final int SET_RESPONSE_LENGTH = 1;
    private static final int SET_MULTIPART = 2;
    private static final int SET_CHUNKED = 4;

    private final boolean isKeepAliveResponseExpected;
    private final HttpVersion httpVersion;
    private final HttpResponseStatus responseStatus;
    private final String sendKeepAlive;
    private final int setSelfDefinedMessageLength;
    private final String setResponseConnection;
    private EmbeddedChannel channel;

    @Parameters
    public static Collection<Object[]> keepAliveProvider() {
        return Arrays.asList(new Object[][] {
                { true, HttpVersion.HTTP_1_0, OK, REQUEST_KEEP_ALIVE, SET_RESPONSE_LENGTH, KEEP_ALIVE },          //  0
                { true, HttpVersion.HTTP_1_0, OK, REQUEST_KEEP_ALIVE, SET_MULTIPART, KEEP_ALIVE },                //  1
                { false, HttpVersion.HTTP_1_0, OK, null, SET_RESPONSE_LENGTH, null },                             //  2
                { true, HttpVersion.HTTP_1_1, OK, REQUEST_KEEP_ALIVE, SET_RESPONSE_LENGTH, null },                //  3
                { false, HttpVersion.HTTP_1_1, OK, REQUEST_KEEP_ALIVE, SET_RESPONSE_LENGTH, CLOSE },              //  4
                { true, HttpVersion.HTTP_1_1, OK, REQUEST_KEEP_ALIVE, SET_MULTIPART, null },                      //  5
                { true, HttpVersion.HTTP_1_1, OK, REQUEST_KEEP_ALIVE, SET_CHUNKED, null },                        //  6
                { false, HttpVersion.HTTP_1_1, OK, null, SET_RESPONSE_LENGTH, null },                             //  7
                { false, HttpVersion.HTTP_1_0, OK, REQUEST_KEEP_ALIVE, NOT_SELF_DEFINED_MSG_LENGTH, null },       //  8
                { false, HttpVersion.HTTP_1_0, OK, null, NOT_SELF_DEFINED_MSG_LENGTH, null },                     //  9
                { false, HttpVersion.HTTP_1_1, OK, REQUEST_KEEP_ALIVE, NOT_SELF_DEFINED_MSG_LENGTH, null },       // 10
                { false, HttpVersion.HTTP_1_1, OK, null, NOT_SELF_DEFINED_MSG_LENGTH, null },                     // 11
                { false, HttpVersion.HTTP_1_0, OK, REQUEST_KEEP_ALIVE, SET_RESPONSE_LENGTH, null },               // 12
                { true, HttpVersion.HTTP_1_1, NO_CONTENT, REQUEST_KEEP_ALIVE, NOT_SELF_DEFINED_MSG_LENGTH, null}, // 13
                { false, HttpVersion.HTTP_1_0, NO_CONTENT, null, NOT_SELF_DEFINED_MSG_LENGTH, null}               // 14
        });
    }

    public HttpServerKeepAliveHandlerTest(boolean isKeepAliveResponseExpected, HttpVersion httpVersion,
                                          HttpResponseStatus responseStatus, String sendKeepAlive,
                                          int setSelfDefinedMessageLength, CharSequence setResponseConnection) {
        this.isKeepAliveResponseExpected = isKeepAliveResponseExpected;
        this.httpVersion = httpVersion;
        this.responseStatus = responseStatus;
        this.sendKeepAlive = sendKeepAlive;
        this.setSelfDefinedMessageLength = setSelfDefinedMessageLength;
        this.setResponseConnection = setResponseConnection == null? null : setResponseConnection.toString();
    }

    @Before
    public void setUp() {
        channel = new EmbeddedChannel(new HttpServerKeepAliveHandler());
    }

    @Test
    public void test_KeepAlive() throws Exception {
        FullHttpRequest request = new DefaultFullHttpRequest(httpVersion, HttpMethod.GET, "/v1/foo/bar");
        setKeepAlive(request, REQUEST_KEEP_ALIVE.equals(sendKeepAlive));
        HttpResponse response = new DefaultFullHttpResponse(httpVersion, responseStatus);
        if (!StringUtil.isNullOrEmpty(setResponseConnection)) {
            response.headers().set(HttpHeaderNames.CONNECTION, setResponseConnection);
        }
        setupMessageLength(response);

        assertTrue(channel.writeInbound(request));
        Object requestForwarded = channel.readInbound();
        assertEquals(request, requestForwarded);
        ReferenceCountUtil.release(requestForwarded);
        channel.writeAndFlush(response);
        HttpResponse writtenResponse = channel.readOutbound();

        assertEquals(isKeepAliveResponseExpected, channel.isOpen(), "channel.isOpen");
        assertEquals(isKeepAliveResponseExpected, isKeepAlive(writtenResponse), "response keep-alive");
        ReferenceCountUtil.release(writtenResponse);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void testConnectionCloseHeaderHandledCorrectly() throws Exception {
        HttpResponse response = new DefaultFullHttpResponse(httpVersion, responseStatus);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        setupMessageLength(response);

        channel.writeAndFlush(response);
        HttpResponse writtenResponse = channel.readOutbound();

        assertFalse(channel.isOpen());
        ReferenceCountUtil.release(writtenResponse);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void testConnectionCloseHeaderHandledCorrectlyForVoidPromise() throws Exception {
        HttpResponse response = new DefaultFullHttpResponse(httpVersion, responseStatus);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        setupMessageLength(response);

        channel.writeAndFlush(response, channel.voidPromise());
        HttpResponse writtenResponse = channel.readOutbound();

        assertFalse(channel.isOpen());
        ReferenceCountUtil.release(writtenResponse);
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void test_PipelineKeepAlive() {
        FullHttpRequest firstRequest = new DefaultFullHttpRequest(httpVersion, HttpMethod.GET, "/v1/foo/bar");
        setKeepAlive(firstRequest, true);
        FullHttpRequest secondRequest = new DefaultFullHttpRequest(httpVersion, HttpMethod.GET, "/v1/foo/bar");
        setKeepAlive(secondRequest, REQUEST_KEEP_ALIVE.equals(sendKeepAlive));
        FullHttpRequest finalRequest = new DefaultFullHttpRequest(httpVersion, HttpMethod.GET, "/v1/foo/bar");
        setKeepAlive(finalRequest, false);
        FullHttpResponse response = new DefaultFullHttpResponse(httpVersion, responseStatus);
        FullHttpResponse informationalResp = new DefaultFullHttpResponse(httpVersion, HttpResponseStatus.PROCESSING);
        setKeepAlive(response, true);
        setContentLength(response, 0);
        setKeepAlive(informationalResp, true);

        assertTrue(channel.writeInbound(firstRequest, secondRequest, finalRequest));

        Object requestForwarded = channel.readInbound();
        assertEquals(firstRequest, requestForwarded);
        ReferenceCountUtil.release(requestForwarded);

        channel.writeAndFlush(response.retainedDuplicate());
        HttpResponse firstResponse = channel.readOutbound();
        assertTrue(channel.isOpen(), "channel.isOpen");
        assertTrue(isKeepAlive(firstResponse), "response keep-alive");
        ReferenceCountUtil.release(firstResponse);

        requestForwarded = channel.readInbound();
        assertEquals(secondRequest, requestForwarded);
        ReferenceCountUtil.release(requestForwarded);

        channel.writeAndFlush(informationalResp);
        HttpResponse writtenInfoResp = channel.readOutbound();
        assertTrue(channel.isOpen(), "channel.isOpen");
        assertTrue(isKeepAlive(writtenInfoResp), "response keep-alive");
        ReferenceCountUtil.release(writtenInfoResp);

        if (!StringUtil.isNullOrEmpty(setResponseConnection)) {
            response.headers().set(HttpHeaderNames.CONNECTION, setResponseConnection);
        } else {
            response.headers().remove(HttpHeaderNames.CONNECTION);
        }
        setupMessageLength(response);
        channel.writeAndFlush(response.retainedDuplicate());
        HttpResponse secondResponse = channel.readOutbound();
        assertEquals(isKeepAliveResponseExpected, channel.isOpen(), "channel.isOpen");
        assertEquals(isKeepAliveResponseExpected, isKeepAlive(secondResponse), "response keep-alive");
        ReferenceCountUtil.release(secondResponse);

        requestForwarded = channel.readInbound();
        assertEquals(finalRequest, requestForwarded);
        ReferenceCountUtil.release(requestForwarded);

        if (isKeepAliveResponseExpected) {
            channel.writeAndFlush(response);
            HttpResponse finalResponse = channel.readOutbound();
            assertFalse(channel.isOpen(), "channel.isOpen");
            assertFalse(isKeepAlive(finalResponse), "response keep-alive");
        }
        ReferenceCountUtil.release(response);
        assertFalse(channel.finishAndReleaseAll());
    }

    private void setupMessageLength(HttpResponse response) {
        switch (setSelfDefinedMessageLength) {
        case NOT_SELF_DEFINED_MSG_LENGTH:
            if (isContentLengthSet(response)) {
                response.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
            }
            break;
        case SET_RESPONSE_LENGTH:
            setContentLength(response, 0);
            break;
        case SET_CHUNKED:
            setTransferEncodingChunked(response, true);
            break;
        case SET_MULTIPART:
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, MULTIPART_MIXED.toUpperCase());
            break;
        default:
            throw new IllegalArgumentException("selfDefinedMessageLength: " + setSelfDefinedMessageLength);
        }
    }
}

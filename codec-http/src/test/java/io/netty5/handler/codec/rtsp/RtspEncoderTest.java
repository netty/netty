/*
 * Copyright 2015 The Netty Project
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
package io.netty5.handler.codec.rtsp;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.http.DefaultFullHttpRequest;
import io.netty5.handler.codec.http.DefaultFullHttpResponse;
import io.netty5.handler.codec.http.DefaultHttpRequest;
import io.netty5.handler.codec.http.DefaultHttpResponse;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.FullHttpResponse;
import io.netty5.handler.codec.http.HttpRequest;
import io.netty5.handler.codec.http.HttpResponse;
import org.junit.jupiter.api.Test;

import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test cases for RTSP encoder.
 */
public class RtspEncoderTest {

    /**
     * Test of a SETUP request, with no body.
     */
    @Test
    public void testSendSetupRequest() {
        HttpRequest request = new DefaultHttpRequest(RtspVersions.RTSP_1_0,
               RtspMethods.SETUP,
               "rtsp://172.10.20.30:554/d3abaaa7-65f2-42b4-8d6b-379f492fcf0f");
        request.headers().add(RtspHeaderNames.TRANSPORT,
               "MP2T/DVBC/UDP;unicast;client=01234567;source=172.10.20.30;" +
               "destination=1.1.1.1;client_port=6922");
        request.headers().add(RtspHeaderNames.CSEQ, "1");

        EmbeddedChannel ch = new EmbeddedChannel(new RtspEncoder());
        ch.writeOutbound(request);

        try (Buffer buf = ch.readOutbound()) {
            String actual = buf.toString(UTF_8);
            assertThat(actual).startsWith(
                    "SETUP rtsp://172.10.20.30:554/d3abaaa7-65f2-42b4-8d6b-379f492fcf0f RTSP/1.0\r\n");
            assertThat(actual.lines()).contains(
                    "transport: MP2T/DVBC/UDP;unicast;client=01234567;source=172.10.20.30;" +
                    "destination=1.1.1.1;client_port=6922",
                    "cseq: 1"
            );
            assertThat(actual).endsWith("\r\n\r\n");
        }
    }

    /**
     * Test of a GET_PARAMETER request, with body.
     */
    @Test
    public void testSendGetParameterRequest() {
        byte[] content = ("stream_state\r\n"
                        + "position\r\n"
                        + "scale\r\n").getBytes(UTF_8);

        FullHttpRequest request = new DefaultFullHttpRequest(
                RtspVersions.RTSP_1_0,
                RtspMethods.GET_PARAMETER,
                "rtsp://172.10.20.30:554", preferredAllocator().allocate(content.length));
        request.headers().add(RtspHeaderNames.SESSION, "2547019973447939919");
        request.headers().add(RtspHeaderNames.CSEQ, "3");
        request.headers().add(RtspHeaderNames.CONTENT_LENGTH, String.valueOf(content.length));
        request.headers().add(RtspHeaderNames.CONTENT_TYPE, "text/parameters");
        request.payload().writeBytes(content);

        EmbeddedChannel ch = new EmbeddedChannel(new RtspEncoder());
        ch.writeOutbound(request);

        try (Buffer buf = ch.readOutbound()) {
            String[] actual = buf.toString(UTF_8).split("\r\n\r\n");
            String head = actual[0];
            String body = actual[1];
            assertEquals(2, actual.length);
            assertThat(head).startsWith("GET_PARAMETER rtsp://172.10.20.30:554 RTSP/1.0\r\n");
            assertThat(head.lines()).contains(
                    "session: 2547019973447939919",
                    "cseq: 3",
                    "content-length: 31",
                    "content-type: text/parameters"
            );
            assertThat(body).isEqualTo("stream_state\r\n" +
                                       "position\r\n" +
                                       "scale\r\n");
        }
    }

    /**
     * Test of a 200 OK response, without body.
     */
    @Test
    public void testSend200OkResponseWithoutBody() {
        HttpResponse response = new DefaultHttpResponse(RtspVersions.RTSP_1_0,
                RtspResponseStatuses.OK);
        response.headers().add(RtspHeaderNames.SERVER, "Testserver");
        response.headers().add(RtspHeaderNames.CSEQ, "1");
        response.headers().add(RtspHeaderNames.SESSION, "2547019973447939919");

        EmbeddedChannel ch = new EmbeddedChannel(new RtspEncoder());
        ch.writeOutbound(response);

        try (Buffer buf = ch.readOutbound()) {
            String actual = buf.toString(UTF_8);
            assertThat(actual).startsWith("RTSP/1.0 200 OK\r\n");
            assertThat(actual.lines()).contains(
                    "server: Testserver",
                    "cseq: 1",
                    "session: 2547019973447939919"
            );
        }
    }

    /**
     * Test of a 200 OK response, with body.
     */
    @Test
    public void testSend200OkResponseWithBody() {
        byte[] content = ("position: 24\r\n"
                        + "stream_state: playing\r\n"
                        + "scale: 1.00\r\n").getBytes(UTF_8);

        FullHttpResponse response = new DefaultFullHttpResponse(
                RtspVersions.RTSP_1_0, RtspResponseStatuses.OK, preferredAllocator().allocate(content.length));
        response.headers().add(RtspHeaderNames.SERVER, "Testserver");
        response.headers().add(RtspHeaderNames.SESSION, "2547019973447939919");
        response.headers().add(RtspHeaderNames.CONTENT_TYPE,
                "text/parameters");
        response.headers().add(RtspHeaderNames.CONTENT_LENGTH,
                "" + content.length);
        response.headers().add(RtspHeaderNames.CSEQ, "3");
        response.payload().writeBytes(content);

        EmbeddedChannel ch = new EmbeddedChannel(new RtspEncoder());
        ch.writeOutbound(response);

        try (Buffer buf = ch.readOutbound()) {
            String[] actual = buf.toString(UTF_8).split("\r\n\r\n");
            String head = actual[0];
            String body = actual[1];
            assertThat(head).startsWith("RTSP/1.0 200 OK\r\n");
            assertThat(head.lines()).contains(
                    "server: Testserver",
                    "session: 2547019973447939919",
                    "content-type: text/parameters",
                    "content-length: 50",
                    "cseq: 3"
            );
            assertThat(body).isEqualTo("position: 24\r\n" +
                                       "stream_state: playing\r\n" +
                                       "scale: 1.00\r\n");
        }
    }
}

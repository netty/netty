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
package io.netty.handler.codec.rtsp;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectAggregator;

import org.junit.Test;

/**
 * Test cases for RTSP decoder.
 */
public class RtspDecoderTest {

    /**
     * There was a problem when an ANNOUNCE request was issued by the server,
     * i.e. entered through the response decoder. First the decoder failed to
     * parse the ANNOUNCE request, then it stopped receiving any more
     * responses. This test verifies that the issue is solved.
     */
    @Test
    public void testReceiveAnnounce() {
        byte[] data1 = ("ANNOUNCE rtsp://172.20.184.218:554/d3abaaa7-65f2-"
                      + "42b4-8d6b-379f492fcf0f RTSP/1.0\r\n"
                      + "CSeq: 2\r\n"
                      + "Session: 2777476816092819869\r\n"
                      + "x-notice: 5402 \"Session Terminated by Server\" "
                      + "event-date=20150514T075303Z\r\n"
                      + "Range: npt=0\r\n\r\n").getBytes();

        byte[] data2 = ("RTSP/1.0 200 OK\r\n" +
                        "Server: Orbit2x\r\n" +
                        "CSeq: 172\r\n" +
                        "Session: 2547019973447939919\r\n" +
                        "\r\n").getBytes();

        EmbeddedChannel ch = new EmbeddedChannel(new RtspDecoder(),
                                            new HttpObjectAggregator(1048576));
        ch.writeInbound(Unpooled.wrappedBuffer(data1),
                        Unpooled.wrappedBuffer(data2));

        HttpObject res1 = ch.readInbound();
        System.out.println(res1);
        assertNotNull(res1);
        assertTrue(res1 instanceof FullHttpRequest);
        ((FullHttpRequest) res1).release();

        HttpObject res2 = ch.readInbound();
        System.out.println(res2);
        assertNotNull(res2);
        assertTrue(res2 instanceof FullHttpResponse);
        ((FullHttpResponse) res2).release();
    }
}

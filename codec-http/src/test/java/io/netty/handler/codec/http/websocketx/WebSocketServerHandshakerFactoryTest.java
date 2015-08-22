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

package io.netty.handler.codec.http.websocketx;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;

import static org.junit.Assert.*;

public class WebSocketServerHandshakerFactoryTest {

    @Test
    public void testUnsupportedVersion() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel();
        WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ch);
        ch.runPendingTasks();
        Object msg = ch.readOutbound();

        if (!(msg instanceof FullHttpResponse)) {
            fail("Got wrong response " + msg);
        }
        FullHttpResponse response = (FullHttpResponse) msg;

        assertEquals(HttpResponseStatus.UPGRADE_REQUIRED, response.status());
        assertEquals(WebSocketVersion.V13.toHttpHeaderValue(),
                response.headers().get(HttpHeaderNames.SEC_WEBSOCKET_VERSION));
        assertTrue(HttpUtil.isContentLengthSet(response));
        assertEquals(0, HttpUtil.getContentLength(response));

        ReferenceCountUtil.release(response);
        assertFalse(ch.finish());
    }
}

/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.codec.socksx.v5;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderResult;
import org.junit.Test;

import static org.junit.Assert.*;

public class Socks5InitialRequestDecoderTest {
    @Test
    public void testUnpackingCausesDecodeFail() {
        EmbeddedChannel e = new EmbeddedChannel(new Socks5InitialRequestDecoder());
        assertFalse(e.writeInbound(Unpooled.wrappedBuffer(new byte[]{5, 2, 0})));
        assertTrue(e.writeInbound(Unpooled.wrappedBuffer(new byte[]{1})));
        Object o = e.readInbound();

        assertTrue(o instanceof DefaultSocks5InitialRequest);
        DefaultSocks5InitialRequest req = (DefaultSocks5InitialRequest) o;
        assertSame(req.decoderResult(), DecoderResult.SUCCESS);
        assertFalse(e.finish());
    }
}

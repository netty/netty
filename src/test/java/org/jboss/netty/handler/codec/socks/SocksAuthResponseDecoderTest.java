/*
 * Copyright 2012 The Netty Project
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
package org.jboss.netty.handler.codec.socks;

import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.junit.Test;

import static org.junit.Assert.*;

public class SocksAuthResponseDecoderTest {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SocksAuthResponseDecoderTest.class);

    private static void testSocksAuthResponseDecoderWithDifferentParams(SocksMessage.AuthStatus authStatus)
            throws Exception{
        logger.debug("Testing SocksAuthResponseDecoder with authStatus: "+ authStatus);
        SocksAuthResponse msg = new SocksAuthResponse(authStatus);
        SocksAuthResponseDecoder decoder = new SocksAuthResponseDecoder();
        DecoderEmbedder<SocksAuthResponse> embedder = new DecoderEmbedder<SocksAuthResponse>(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        msg = embedder.poll();
        assertSame(msg.getAuthStatus(), authStatus);
        assertNull(embedder.poll());
    }

    @Test
    public void testSocksCmdResponseDecoder() throws Exception {
        for (SocksMessage.AuthStatus authStatus: SocksMessage.AuthStatus.values()) {
                testSocksAuthResponseDecoderWithDifferentParams(authStatus);
        }
    }
}

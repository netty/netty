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
package io.netty.codec.socks;

import io.netty.channel.embedded.EmbeddedByteChannel;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SocksCmdResponseDecoderTest {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SocksCmdResponseDecoderTest.class);

    private void testSocksCmdResponseDecoderWithDifferentParams(SocksMessage.CmdStatus cmdStatus, SocksMessage.AddressType addressType){
        logger.debug("Testing cmdStatus: " + cmdStatus + " addressType: " + addressType);
        SocksResponse msg = new SocksCmdResponse(cmdStatus, addressType);
        SocksCmdResponseDecoder decoder = new SocksCmdResponseDecoder();
        EmbeddedByteChannel embedder = new EmbeddedByteChannel(decoder);
        SocksCommonTestUtils.writeMessageIntoEmbedder(embedder, msg);
        if (addressType == SocksMessage.AddressType.UNKNOWN){
            assertTrue(embedder.readInbound() instanceof UnknownSocksResponse);
        } else {
            msg = (SocksCmdResponse) embedder.readInbound();
            assertTrue(((SocksCmdResponse) msg).getCmdStatus().equals(cmdStatus));
        }
        assertNull(embedder.readInbound());
    }

    @Test
    public void testSocksCmdResponseDecoder(){
        for (SocksMessage.CmdStatus cmdStatus: SocksMessage.CmdStatus.values()){
            for (SocksMessage.AddressType addressType: SocksMessage.AddressType.values()){
                testSocksCmdResponseDecoderWithDifferentParams(cmdStatus, addressType);
            }
        }
    }
}

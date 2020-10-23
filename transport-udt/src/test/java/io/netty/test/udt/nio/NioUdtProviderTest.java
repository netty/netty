/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.test.udt.nio;

import io.netty.channel.udt.UdtServerChannel;
import io.netty.channel.udt.nio.NioUdtByteAcceptorChannel;
import io.netty.channel.udt.nio.NioUdtByteConnectorChannel;
import io.netty.channel.udt.nio.NioUdtByteRendezvousChannel;
import io.netty.channel.udt.nio.NioUdtProvider;
import io.netty.channel.udt.nio.NioUdtMessageAcceptorChannel;
import io.netty.channel.udt.nio.NioUdtMessageConnectorChannel;
import io.netty.channel.udt.nio.NioUdtMessageRendezvousChannel;

import org.junit.Test;

import static org.junit.Assert.*;

public class NioUdtProviderTest extends AbstractUdtTest {

    /**
     * verify factory
     */
    @Test
    public void provideFactory() {
        NioUdtByteAcceptorChannel nioUdtByteAcceptorChannel
                = (NioUdtByteAcceptorChannel) NioUdtProvider.BYTE_ACCEPTOR.newChannel();
        NioUdtByteConnectorChannel nioUdtByteConnectorChannel
                = (NioUdtByteConnectorChannel) NioUdtProvider.BYTE_CONNECTOR.newChannel();
        NioUdtByteRendezvousChannel nioUdtByteRendezvousChannel
                = (NioUdtByteRendezvousChannel) NioUdtProvider.BYTE_RENDEZVOUS.newChannel();
        NioUdtMessageAcceptorChannel nioUdtMessageAcceptorChannel
                = (NioUdtMessageAcceptorChannel) NioUdtProvider.MESSAGE_ACCEPTOR.newChannel();
        NioUdtMessageConnectorChannel nioUdtMessageConnectorChannel
                = (NioUdtMessageConnectorChannel) NioUdtProvider.MESSAGE_CONNECTOR.newChannel();
        NioUdtMessageRendezvousChannel nioUdtMessageRendezvousChannel
                = (NioUdtMessageRendezvousChannel) NioUdtProvider.MESSAGE_RENDEZVOUS.newChannel();

        // bytes
        assertNotNull(nioUdtByteAcceptorChannel);
        assertNotNull(nioUdtByteConnectorChannel);
        assertNotNull(nioUdtByteRendezvousChannel);

        // message
        assertNotNull(nioUdtMessageAcceptorChannel);
        assertNotNull(nioUdtMessageConnectorChannel);
        assertNotNull(nioUdtMessageRendezvousChannel);

        // channel
        assertNotNull(NioUdtProvider.channelUDT(nioUdtByteAcceptorChannel));
        assertNotNull(NioUdtProvider.channelUDT(nioUdtByteConnectorChannel));
        assertNotNull(NioUdtProvider.channelUDT(nioUdtByteRendezvousChannel));
        assertNotNull(NioUdtProvider.channelUDT(nioUdtMessageAcceptorChannel));
        assertNotNull(NioUdtProvider.channelUDT(nioUdtMessageConnectorChannel));
        assertNotNull(NioUdtProvider.channelUDT(nioUdtMessageRendezvousChannel));

        // acceptor types
        assertTrue(NioUdtProvider.BYTE_ACCEPTOR.newChannel() instanceof UdtServerChannel);
        assertTrue(NioUdtProvider.MESSAGE_ACCEPTOR.newChannel() instanceof UdtServerChannel);
    }
}

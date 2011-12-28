/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.channel.socket.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import io.netty.channel.Channel;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPipelineFactory;
import io.netty.channel.ChannelState;
import io.netty.channel.Channels;
import io.netty.channel.UpstreamChannelStateEvent;
import io.netty.channel.socket.ServerSocketChannelConfig;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests HTTP tunnel server channels
 */
@RunWith(JMock.class)
public class HttpTunnelServerChannelTest {

    JUnit4Mockery mockContext = new JUnit4Mockery();

    private HttpTunnelServerChannel virtualChannel;

    private UpstreamEventCatcher upstreamEvents;

    private FakeServerSocketChannelFactory realChannelFactory;

    @Before
    public void setUp() throws Exception {
        realChannelFactory = new FakeServerSocketChannelFactory();
        realChannelFactory.sink = new FakeChannelSink();

        HttpTunnelServerChannelFactory factory =
                new HttpTunnelServerChannelFactory(realChannelFactory);
        virtualChannel = factory.newChannel(createVirtualChannelPipeline());
    }

    private ChannelPipeline createVirtualChannelPipeline() {
        ChannelPipeline pipeline = Channels.pipeline();
        upstreamEvents = new UpstreamEventCatcher();
        pipeline.addLast(UpstreamEventCatcher.NAME, upstreamEvents);
        return pipeline;
    }

    @Test
    public void testGetLocalAddress_delegatedToRealChannel() {
        realChannelFactory.createdChannel.localAddress =
                InetSocketAddress.createUnresolved("mycomputer", 80);
        SocketAddress returned = virtualChannel.getLocalAddress();
        assertSame(realChannelFactory.createdChannel.localAddress, returned);
    }

    @Test
    public void testGetRemoteAddress_returnsNull() {
        assertNull(virtualChannel.getRemoteAddress());
    }

    @Test
    public void testIsBound_delegatedToRealChannel() {
        realChannelFactory.createdChannel.bound = true;
        assertTrue(virtualChannel.isBound());
        realChannelFactory.createdChannel.bound = false;
        assertFalse(virtualChannel.isBound());
    }

    @Test
    public void testConstruction_firesOpenEvent() {
        assertTrue(upstreamEvents.events.size() > 0);
        checkIsUpstreamChannelStateEvent(upstreamEvents.events.poll(),
                virtualChannel, ChannelState.OPEN, Boolean.TRUE);
    }

    @Test
    public void testChannelBoundEventFromReal_replicatedOnVirtual() {
        upstreamEvents.events.clear();
        InetSocketAddress boundAddr =
                InetSocketAddress.createUnresolved("mycomputer", 12345);
        Channels.fireChannelBound(realChannelFactory.createdChannel, boundAddr);
        assertEquals(1, upstreamEvents.events.size());
        checkIsUpstreamChannelStateEvent(upstreamEvents.events.poll(),
                virtualChannel, ChannelState.BOUND, boundAddr);
    }

    @Test
    public void testChannelUnboundEventFromReal_replicatedOnVirtual() {
        upstreamEvents.events.clear();
        Channels.fireChannelUnbound(realChannelFactory.createdChannel);
        assertEquals(1, upstreamEvents.events.size());
        checkIsUpstreamChannelStateEvent(upstreamEvents.events.poll(),
                virtualChannel, ChannelState.BOUND, null);
    }

    @Test
    public void testChannelClosedEventFromReal_replicatedOnVirtual() {
        upstreamEvents.events.clear();
        Channels.fireChannelClosed(realChannelFactory.createdChannel);
        assertEquals(1, upstreamEvents.events.size());
        checkIsUpstreamChannelStateEvent(upstreamEvents.events.poll(),
                virtualChannel, ChannelState.OPEN, Boolean.FALSE);
    }

    @Test
    public void testHasConfiguration() {
        assertNotNull(virtualChannel.getConfig());
    }

    @Test
    public void testChangePipelineFactoryDoesNotAffectRealChannel() {
        ChannelPipelineFactory realPipelineFactory =
                realChannelFactory.createdChannel.getConfig()
                        .getPipelineFactory();
        ChannelPipelineFactory virtualPipelineFactory =
                mockContext.mock(ChannelPipelineFactory.class);
        virtualChannel.getConfig().setPipelineFactory(virtualPipelineFactory);
        assertSame(virtualPipelineFactory, virtualChannel.getConfig()
                .getPipelineFactory());

        // channel pipeline factory is a special case: we do not want it set on the configuration
        // of the underlying factory
        assertSame(realPipelineFactory, realChannelFactory.createdChannel
                .getConfig().getPipelineFactory());
    }

    @Test
    public void testChangingBacklogAffectsRealChannel() {
        virtualChannel.getConfig().setBacklog(1234);
        assertEquals(1234, realChannelFactory.createdChannel.getConfig()
                .getBacklog());
    }

    @Test
    public void testChangingConnectTimeoutMillisAffectsRealChannel() {
        virtualChannel.getConfig().setConnectTimeoutMillis(54321);
        assertEquals(54321, realChannelFactory.createdChannel.getConfig()
                .getConnectTimeoutMillis());
    }

    @Test
    public void testChangingPerformancePreferencesAffectsRealChannel() {
        final ServerSocketChannelConfig mockConfig =
                mockContext.mock(ServerSocketChannelConfig.class);
        realChannelFactory.createdChannel.config = mockConfig;
        mockContext.checking(new Expectations() {
            {
                one(mockConfig).setPerformancePreferences(100, 200, 300);
            }
        });
        virtualChannel.getConfig().setPerformancePreferences(100, 200, 300);
        mockContext.assertIsSatisfied();
    }

    @Test
    public void testChangingReceiveBufferSizeAffectsRealChannel() {
        virtualChannel.getConfig().setReceiveBufferSize(10101);
        assertEquals(10101, realChannelFactory.createdChannel.getConfig()
                .getReceiveBufferSize());
    }

    @Test
    public void testChangingReuseAddressAffectsRealChannel() {
        virtualChannel.getConfig().setReuseAddress(true);
        assertEquals(true, realChannelFactory.createdChannel.getConfig()
                .isReuseAddress());
    }

    @Test
    public void testSetChannelPipelineFactoryViaOption() {
        final ServerSocketChannelConfig mockConfig =
                mockContext.mock(ServerSocketChannelConfig.class);
        realChannelFactory.createdChannel.config = mockConfig;

        mockContext.checking(new Expectations() {
            {
                never(mockConfig);
            }
        });

        ChannelPipelineFactory factory =
                mockContext.mock(ChannelPipelineFactory.class);
        virtualChannel.getConfig().setOption("pipelineFactory", factory);
        assertSame(factory, virtualChannel.getConfig().getPipelineFactory());
    }

    @Test
    public void testSetOptionAffectsRealChannel() {
        final ServerSocketChannelConfig mockConfig =
                mockContext.mock(ServerSocketChannelConfig.class);
        realChannelFactory.createdChannel.config = mockConfig;

        mockContext.checking(new Expectations() {
            {
                one(mockConfig).setOption("testOption", "testValue");
            }
        });

        virtualChannel.getConfig().setOption("testOption", "testValue");
    }

    private void checkIsUpstreamChannelStateEvent(ChannelEvent ev,
            Channel expectedChannel, ChannelState expectedState,
            Object expectedValue) {
        assertTrue(ev instanceof UpstreamChannelStateEvent);
        UpstreamChannelStateEvent checkedEv = (UpstreamChannelStateEvent) ev;
        assertSame(expectedChannel, checkedEv.getChannel());
        assertEquals(expectedState, checkedEv.getState());
        assertEquals(expectedValue, checkedEv.getValue());
    }

}

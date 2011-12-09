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

import io.netty.channel.ChannelPipeline;
import io.netty.channel.Channels;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests HTTP tunnel accepted channel sinks
 */
@RunWith(JMock.class)
public class HttpTunnelAcceptedChannelSinkTest {

    private static final String TUNNEL_ID = "1";

    private final JUnit4Mockery mockContext = new JUnit4Mockery();

    ServerMessageSwitchDownstreamInterface messageSwitch;

    private HttpTunnelAcceptedChannelSink sink;

    private FakeSocketChannel channel;

    private UpstreamEventCatcher upstreamCatcher;

    @Before
    public void setUp() throws Exception {
        messageSwitch =
                mockContext.mock(ServerMessageSwitchDownstreamInterface.class);
        sink =
                new HttpTunnelAcceptedChannelSink(messageSwitch, TUNNEL_ID,
                        new HttpTunnelAcceptedChannelConfig());
        ChannelPipeline pipeline = Channels.pipeline();
        upstreamCatcher = new UpstreamEventCatcher();
        pipeline.addLast(UpstreamEventCatcher.NAME, upstreamCatcher);
        channel = new FakeSocketChannel(null, null, pipeline, sink);
        upstreamCatcher.events.clear();
    }

    @Test
    public void testSendInvalidDataType() {
        Channels.write(channel, new Object());
        assertEquals(1, upstreamCatcher.events.size());
        NettyTestUtils.checkIsExceptionEvent(upstreamCatcher.events.poll());
    }

    @Test
    public void testUnbind() {
        mockContext.checking(new Expectations() {
            {
                one(messageSwitch).serverCloseTunnel(TUNNEL_ID);
            }
        });
        Channels.unbind(channel);
    }

    @Test
    public void testDisconnect() {
        mockContext.checking(new Expectations() {
            {
                one(messageSwitch).serverCloseTunnel(TUNNEL_ID);
            }
        });

        Channels.disconnect(channel);
    }
}

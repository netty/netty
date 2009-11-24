package org.jboss.netty.channel.socket.httptunnel;


import static org.junit.Assert.*;

import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ServerSocketChannel;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author iain.mcginniss@onedrum.com
 */
@RunWith(JMock.class)
public class HttpTunnelServerChannelFactoryTest {

    private final JUnit4Mockery mockContext = new JUnit4Mockery();
    ServerSocketChannelFactory realChannelFactory;
    private HttpTunnelServerChannelFactory factory;

    ServerSocketChannel realChannel;

    @Before
    public void setUp() throws Exception {
        realChannelFactory = mockContext.mock(ServerSocketChannelFactory.class);
        factory = new HttpTunnelServerChannelFactory(realChannelFactory);
        ChannelPipeline pipeline = Channels.pipeline();
        realChannel = new FakeServerSocketChannel(factory, pipeline, new FakeChannelSink());
    }

    @Test
    public void testNewChannel() {
        mockContext.checking(new Expectations() {{
            one(realChannelFactory).newChannel(with(any(ChannelPipeline.class))); will(returnValue(realChannel));
        }});
        ChannelPipeline pipeline = Channels.pipeline();
        HttpTunnelServerChannel newChannel = factory.newChannel(pipeline);
        assertNotNull(newChannel);
        assertSame(pipeline, newChannel.getPipeline());
    }

    @Test
    public void testNewChannel_forwardsWrappedFactoryFailure() {
        final ChannelException innerException = new ChannelException();
        mockContext.checking(new Expectations() {{
            one(realChannelFactory).newChannel(with(any(ChannelPipeline.class))); will(throwException(innerException));
        }});

        try {
            factory.newChannel(Channels.pipeline());
            fail("Expected ChannelException");
        } catch(ChannelException e) {
            assertSame(innerException, e);
        }
    }

//    @Test
//    public void testChannelCreation_withServerBootstrap() {
//        mockContext.checking(new Expectations() {{
//            one(realChannelFactory).newChannel(with(any(ChannelPipeline.class))); will(returnValue(realChannel));
//        }});
//
//        ServerBootstrap bootstrap = new ServerBootstrap(factory);
//        Channel newChannel = bootstrap.bind(new InetSocketAddress(80));
//        assertNotNull(newChannel);
//
//    }

}

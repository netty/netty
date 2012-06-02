package io.netty.channel.local;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelInboundHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerContext;
import io.netty.channel.DefaultEventExecutor;
import io.netty.channel.EventExecutor;
import io.netty.channel.EventLoop;
import io.netty.util.internal.QueueFactory;

import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class LocalTransportThreadModelTest {

    private static ServerBootstrap sb;
    private static LocalAddress ADDR;

    @BeforeClass
    public static void init() {
        // Configure a test server
        sb = new ServerBootstrap();
        sb.eventLoop(new LocalEventLoop(), new LocalEventLoop())
          .channel(new LocalServerChannel())
          .localAddress(LocalAddress.ANY)
          .childInitializer(new ChannelInitializer<LocalChannel>() {
              @Override
              public void initChannel(LocalChannel ch) throws Exception {
                  ch.pipeline().addLast(new ChannelInboundMessageHandlerAdapter<Object>() {
                    @Override
                    public void messageReceived(ChannelInboundHandlerContext<Object> ctx, Object msg) {
                        // Discard
                    }
                  });
              }
          });

        ADDR = (LocalAddress) sb.bind().syncUninterruptibly().channel().localAddress();
    }

    @AfterClass
    public static void destroy() {
        sb.shutdown();
    }

    @Test
    public void testSimple() throws Exception {
        EventLoop l = new LocalEventLoop(4, new PrefixThreadFactory("l"));
        EventExecutor e1 = new DefaultEventExecutor(4, new PrefixThreadFactory("e1"));
        EventExecutor e2 = new DefaultEventExecutor(4, new PrefixThreadFactory("e2"));
        TestHandler h1 = new TestHandler();
        TestHandler h2 = new TestHandler();
        TestHandler h3 = new TestHandler();

        Channel ch = new LocalChannel();
        // With no EventExecutor specified, h1 will be always invoked by EventLoop 'l'.
        ch.pipeline().addLast(h1);
        // h2 will be always invoked by EventExecutor 'e1'.
        ch.pipeline().addLast(e1, h2);
        // h3 will be always invoked by EventExecutor 'e2'.
        ch.pipeline().addLast(e2, h3);

        l.register(ch).sync().channel().connect(ADDR).sync();

        // Fire inbound events from all possible starting points.
        ch.pipeline().fireInboundBufferUpdated();
        ch.pipeline().context(h1).fireInboundBufferUpdated();
        ch.pipeline().context(h2).fireInboundBufferUpdated();
        ch.pipeline().context(h3).fireInboundBufferUpdated();
        // Fire outbound events from all possible starting points.
        ch.pipeline().flush();
        ch.pipeline().context(h3).flush();
        ch.pipeline().context(h2).flush();
        ch.pipeline().context(h1).flush().sync();

        String currentName = Thread.currentThread().getName();

        // Events should never be handled from the current thread.
        Assert.assertFalse(h1.inboundThreadNames.contains(currentName));
        Assert.assertFalse(h2.inboundThreadNames.contains(currentName));
        Assert.assertFalse(h3.inboundThreadNames.contains(currentName));
        Assert.assertFalse(h1.outboundThreadNames.contains(currentName));
        Assert.assertFalse(h2.outboundThreadNames.contains(currentName));
        Assert.assertFalse(h3.outboundThreadNames.contains(currentName));

        // Assert that events were handled by the correct executor.
        for (String name: h1.inboundThreadNames) {
            Assert.assertTrue(name.startsWith("l-"));
        }
        for (String name: h2.inboundThreadNames) {
            Assert.assertTrue(name.startsWith("e1-"));
        }
        for (String name: h3.inboundThreadNames) {
            Assert.assertTrue(name.startsWith("e2-"));
        }
        for (String name: h1.outboundThreadNames) {
            Assert.assertTrue(name.startsWith("l-"));
        }
        for (String name: h2.outboundThreadNames) {
            Assert.assertTrue(name.startsWith("e1-"));
        }
        for (String name: h3.outboundThreadNames) {
            Assert.assertTrue(name.startsWith("e2-"));
        }

        // Assert that the events for the same handler were handled by the same thread.
        Set<String> names = new HashSet<String>();
        names.addAll(h1.inboundThreadNames);
        names.addAll(h1.outboundThreadNames);
        Assert.assertEquals(1, names.size());

        names.clear();
        names.addAll(h2.inboundThreadNames);
        names.addAll(h2.outboundThreadNames);
        Assert.assertEquals(1, names.size());

        names.clear();
        names.addAll(h3.inboundThreadNames);
        names.addAll(h3.outboundThreadNames);
        Assert.assertEquals(1, names.size());

        // Count the number of events
        Assert.assertEquals(1, h1.inboundThreadNames.size());
        Assert.assertEquals(2, h2.inboundThreadNames.size());
        Assert.assertEquals(3, h3.inboundThreadNames.size());
        Assert.assertEquals(3, h1.outboundThreadNames.size());
        Assert.assertEquals(2, h2.outboundThreadNames.size());
        Assert.assertEquals(1, h3.outboundThreadNames.size());
    }

    private static class TestHandler extends ChannelHandlerAdapter<Object, Object> {

        private final Queue<String> inboundThreadNames = QueueFactory.createQueue();
        private final Queue<String> outboundThreadNames = QueueFactory.createQueue();

        @Override
        public ChannelBufferHolder<Object> newInboundBuffer(
                ChannelInboundHandlerContext<Object> ctx) throws Exception {
            return ChannelBufferHolders.messageBuffer();
        }

        @Override
        public ChannelBufferHolder<Object> newOutboundBuffer(
                ChannelOutboundHandlerContext<Object> ctx) throws Exception {
            return ChannelBufferHolders.messageBuffer();
        }

        @Override
        public void inboundBufferUpdated(
                ChannelInboundHandlerContext<Object> ctx) throws Exception {
            ctx.inbound().messageBuffer().clear();
            inboundThreadNames.add(Thread.currentThread().getName());
            ctx.fireInboundBufferUpdated();
        }

        @Override
        public void flush(ChannelOutboundHandlerContext<Object> ctx,
                ChannelFuture future) throws Exception {
            ctx.outbound().messageBuffer().clear();
            outboundThreadNames.add(Thread.currentThread().getName());
            ctx.flush(future);
        }
    }

    private static class PrefixThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger id = new AtomicInteger();

        public PrefixThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName(prefix + '-' + id.incrementAndGet());
            return t;
        }
    }
}

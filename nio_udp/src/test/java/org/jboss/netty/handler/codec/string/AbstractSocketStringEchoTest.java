/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.codec.string;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.util.ExecutorUtil;
import org.jboss.netty.util.TestUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public abstract class AbstractSocketStringEchoTest {

    static final Random random = new Random();
    static final String[] data = new String[1024];

    private static ExecutorService executor;

    static {
        for (int i = 0; i < data.length; i ++) {
            int eLen = random.nextInt(512);
            char[] e = new char[eLen];
            for (int j = 0; j < eLen; j ++) {
                e[j] = (char) ('a' + random.nextInt(26));
            }

            data[i] = new String(e);
        }
    }

    @BeforeClass
    public static void init() {
        executor = Executors.newCachedThreadPool();
    }

    @AfterClass
    public static void destroy() {
        ExecutorUtil.terminate(executor);
    }

    protected abstract ChannelFactory newServerSocketChannelFactory(Executor executor);
    protected abstract ChannelFactory newClientSocketChannelFactory(Executor executor);

    @Test
    public void testStringEcho() throws Throwable {
        ServerBootstrap sb = new ServerBootstrap(newServerSocketChannelFactory(executor));
        ClientBootstrap cb = new ClientBootstrap(newClientSocketChannelFactory(executor));

        EchoHandler sh = new EchoHandler();
        EchoHandler ch = new EchoHandler();

        sb.getPipeline().addLast("framer", new DelimiterBasedFrameDecoder(512, Delimiters.lineDelimiter()));
        sb.getPipeline().addLast("decoder", new StringDecoder("ISO-8859-1"));
        sb.getPipeline().addBefore("decoder", "encoder", new StringEncoder("ISO-8859-1"));
        sb.getPipeline().addAfter("decoder", "handler", sh);

        cb.getPipeline().addLast("framer", new DelimiterBasedFrameDecoder(512, Delimiters.lineDelimiter()));
        cb.getPipeline().addLast("decoder", new StringDecoder("ISO-8859-1"));
        cb.getPipeline().addBefore("decoder", "encoder", new StringEncoder("ISO-8859-1"));
        cb.getPipeline().addAfter("decoder", "handler", ch);

        Channel sc = sb.bind(new InetSocketAddress(0));
        int port = ((InetSocketAddress) sc.getLocalAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(TestUtil.getLocalHost(), port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());

        Channel cc = ccf.getChannel();
        for (String element : data) {
            String delimiter = random.nextBoolean() ? "\r\n" : "\n";
            cc.write(element + delimiter);
        }

        while (ch.counter < data.length) {
            if (sh.exception.get() != null) {
                break;
            }
            if (ch.exception.get() != null) {
                break;
            }

            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // Ignore.
            }
        }

        while (sh.counter < data.length) {
            if (sh.exception.get() != null) {
                break;
            }
            if (ch.exception.get() != null) {
                break;
            }

            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // Ignore.
            }
        }

        sh.channel.close().awaitUninterruptibly();
        ch.channel.close().awaitUninterruptibly();
        sc.close().awaitUninterruptibly();

        if (sh.exception.get() != null && !(sh.exception.get() instanceof IOException)) {
            throw sh.exception.get();
        }
        if (ch.exception.get() != null && !(ch.exception.get() instanceof IOException)) {
            throw ch.exception.get();
        }
        if (sh.exception.get() != null) {
            throw sh.exception.get();
        }
        if (ch.exception.get() != null) {
            throw ch.exception.get();
        }
    }

    @ChannelPipelineCoverage("one")
    private class EchoHandler extends SimpleChannelHandler {
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        volatile int counter;

        EchoHandler() {
            super();
        }

        @Override
        public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
                throws Exception {
            channel = e.getChannel();
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception {

            String m = (String) e.getMessage();
            assertEquals(data[counter], m);

            if (channel.getParent() != null) {
                String delimiter = random.nextBoolean() ? "\r\n" : "\n";
                channel.write(m + delimiter);
            }

            counter ++;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
                throws Exception {
            if (exception.compareAndSet(null, e.getCause())) {
                e.getChannel().close();
            }
        }
    }
}

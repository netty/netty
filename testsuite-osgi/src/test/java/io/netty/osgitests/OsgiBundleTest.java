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

package io.netty.osgitests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.ops4j.pax.exam.CoreOptions.frameworkProperty;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.CoreOptions.wrappedBundle;
import static org.osgi.framework.Constants.FRAMEWORK_BOOTDELEGATION;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.Constants;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.internal.PlatformDependent;
import junit.framework.AssertionFailedError;

@RunWith(PaxExam.class)
public class OsgiBundleTest {
    private static final Pattern SLASH = Pattern.compile("/", Pattern.LITERAL);
    private static final String DEPENCIES_LINE = "# dependencies";
    private static final String GROUP = "io.netty";
    private static final Collection<String> BUNDLES;

    static {
        final Set<String> artifacts = new HashSet<String>();
        final File f = new File("target/classes/META-INF/maven/dependencies.properties");
        try {
            final BufferedReader r = new BufferedReader(new FileReader(f));
            try {
                boolean haveDeps = false;

                while (true) {
                    final String line = r.readLine();
                    if (line == null) {
                        // End-of-file
                        break;
                    }

                    // We need to ignore any lines up to the dependencies
                    // line, otherwise we would include ourselves.
                    if (DEPENCIES_LINE.equals(line)) {
                        haveDeps = true;
                    } else if (haveDeps && line.startsWith(GROUP)) {
                        final String[] split = SLASH.split(line);
                        if (split.length > 1) {
                            artifacts.add(split[1]);
                        }
                    }
                }
            } finally {
                r.close();
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }

        BUNDLES = artifacts;
    }

    @Configuration
    public final Option[] config() {
        final Collection<Option> options = new ArrayList<Option>();

        // Avoid boot delegating sun.misc which would fail testSimpleEcho()
        options.add(frameworkProperty(FRAMEWORK_BOOTDELEGATION).value("com.sun.*"));
        options.add(systemProperty("pax.exam.osgi.unresolved.fail").value("true"));
        options.addAll(Arrays.asList(junitBundles()));

        options.add(mavenBundle("com.barchart.udt", "barchart-udt-bundle").versionAsInProject());
        options.add(wrappedBundle(mavenBundle("org.rxtx", "rxtx").versionAsInProject()));

        for (String name : BUNDLES) {
            options.add(mavenBundle(GROUP, name).versionAsInProject());
        }

        return options.toArray(new Option[0]);
    }

    @Test
    public void testResolvedBundles() {
        // No-op, as we just want the bundles to be resolved. Just check if we tested something
        assertFalse("At least one bundle needs to be tested", BUNDLES.isEmpty());
    }

    @Test
    public void testSimpleEcho() throws InterruptedException {

        assertFalse(PlatformDependent.hasUnsafe());

        final BlockingQueue<Throwable> errors = new LinkedBlockingQueue<Throwable>();

        EventLoopGroup serverWorkers = new NioEventLoopGroup();

        ServerBootstrap server = new ServerBootstrap()
                .group(serverWorkers)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel c) throws Exception {
                        c.pipeline().addLast(
                                new LengthFieldPrepender(2),
                                new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2),
                                new StringEncoder(CharsetUtil.UTF_8),
                                new StringDecoder(CharsetUtil.UTF_8),
                                new ServerEchoHandler(errors));
                    }
                });

        Channel sc = server.bind(NetUtil.LOCALHOST, 0).sync().channel();

        final BlockingQueue<String> responses = new LinkedBlockingQueue<String>();

        EventLoopGroup clientWorkers = new NioEventLoopGroup();

        Bootstrap client = new Bootstrap()
                .group(clientWorkers)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel c) throws Exception {
                        c.pipeline().addLast(
                                new LengthFieldPrepender(2),
                                new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2),
                                new StringEncoder(CharsetUtil.UTF_8),
                                new StringDecoder(CharsetUtil.UTF_8),
                                new ClientEchoHandler(errors, responses));
                    }
                });

        Channel cc = client.connect(sc.localAddress()).sync().channel();

        cc.writeAndFlush("Test");

        assertEquals("Test", responses.poll(2, TimeUnit.SECONDS));

        cc.close().sync();
        sc.close().sync();

        serverWorkers.shutdownGracefully().sync();
        clientWorkers.shutdownGracefully().sync();

        Throwable t = errors.poll(500, TimeUnit.MILLISECONDS);

        if (t != null) {
            Throwable t2;
            while ((t2 = errors.poll()) != null) {
                t2.printStackTrace();
            }
            Error e = new AssertionFailedError("An error occurred in processing");
            e.initCause(t);
            throw e;
        }
    }

    private static class ServerEchoHandler extends SimpleChannelInboundHandler<String> {

        private final BlockingQueue<Throwable> errors;

        public ServerEchoHandler(BlockingQueue<Throwable> errors) {
            this.errors = errors;
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, String in) throws Exception {
            ctx.writeAndFlush(in);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx,
                Throwable cause) throws Exception {
            if (!errors.add(cause)) {
                System.out.println("UNABLE TO ADD A FAILURE");
                cause.printStackTrace();
            }
            ctx.close();
        }
    }

    private static class ClientEchoHandler extends SimpleChannelInboundHandler<String> {

        private final BlockingQueue<String> responses;
        private final BlockingQueue<Throwable> errors;

        public ClientEchoHandler(BlockingQueue<Throwable> errors,
            BlockingQueue<String> responses) {
            this.responses = responses;
            this.errors = errors;
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, String in) throws Exception {
            if (!responses.add(in)) {
                throw new IllegalStateException("Unable to immediately add the response to the queue");
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx,
                Throwable cause) throws Exception {
            if (!errors.add(cause)) {
                System.out.println("UNABLE TO ADD A FAILURE");
                cause.printStackTrace();
            }
            ctx.close();
        }
    }
}

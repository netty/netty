/*
 * Copyright 2013 The Netty Project
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
package org.jboss.netty.handler.codec.spdy;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.util.TestUtil;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

public class SpdyFrameDecoderTest {

    @Test
    public void testTooLargeHeaderNameOnSynStreamRequest() throws Exception {
        testTooLargeHeaderNameOnSynStreamRequest(SpdyVersion.SPDY_3);
        testTooLargeHeaderNameOnSynStreamRequest(SpdyVersion.SPDY_3_1);
    }

    private void testTooLargeHeaderNameOnSynStreamRequest(SpdyVersion spdyVersion) throws Exception {
        List<Integer> headerSizes = Arrays.asList(90, 900);
        for (int maxHeaderSize : headerSizes) { // 90 catches the header name, 900 the value
            SpdyHeadersFrame frame = new DefaultSpdySynStreamFrame(1, 0, (byte) 0);
            addHeader(frame, 100, 1000);
            CaptureHandler captureHandler = new CaptureHandler();
            ServerBootstrap sb = new ServerBootstrap(
                    newServerSocketChannelFactory(Executors.newCachedThreadPool()));
            ClientBootstrap cb = new ClientBootstrap(
                    newClientSocketChannelFactory(Executors.newCachedThreadPool()));

            sb.getPipeline().addLast("decoder", new SpdyFrameDecoder(spdyVersion, 10000, maxHeaderSize));
            sb.getPipeline().addLast("sessionHandler", new SpdySessionHandler(spdyVersion, true));
            sb.getPipeline().addLast("handler", captureHandler);

            cb.getPipeline().addLast("encoder", new SpdyFrameEncoder(spdyVersion));

            Channel sc = sb.bind(new InetSocketAddress(0));
            int port = ((InetSocketAddress) sc.getLocalAddress()).getPort();

            ChannelFuture ccf = cb.connect(new InetSocketAddress(TestUtil.getLocalHost(), port));
            assertTrue(ccf.awaitUninterruptibly().isSuccess());
            Channel cc = ccf.getChannel();

            sendAndWaitForFrame(cc, frame, captureHandler);

            assertNotNull("version " + spdyVersion.getVersion() + ", not null message",
                    captureHandler.message);
            String message = "version " + spdyVersion.getVersion() + ", should be SpdyHeadersFrame, was " +
                    captureHandler.message.getClass();
            assertTrue(
                    message,
                    captureHandler.message instanceof SpdyHeadersFrame);
            SpdyHeadersFrame writtenFrame = (SpdyHeadersFrame) captureHandler.message;

            assertTrue("should be truncated", writtenFrame.isTruncated());
            assertFalse("should not be invalid", writtenFrame.isInvalid());

            sc.close().awaitUninterruptibly();
            cb.shutdown();
            sb.shutdown();
            cb.releaseExternalResources();
            sb.releaseExternalResources();
        }
    }

    @Test
    public void testLargeHeaderNameOnSynStreamRequest() throws Exception {
        testLargeHeaderNameOnSynStreamRequest(SpdyVersion.SPDY_3);
        testLargeHeaderNameOnSynStreamRequest(SpdyVersion.SPDY_3_1);
    }

    private void testLargeHeaderNameOnSynStreamRequest(SpdyVersion spdyVersion) throws Exception {
        int maxHeaderSize = 8192;
        
        String expectedName = createString('h', 100);
        String expectedValue = createString('v', 5000);
        
        SpdyHeadersFrame frame = new DefaultSpdySynStreamFrame(1, 0, (byte) 0);
        SpdyHeaders headers = frame.headers();
        headers.add(expectedName, expectedValue);
        
        CaptureHandler captureHandler = new CaptureHandler();
        ServerBootstrap sb = new ServerBootstrap(
                newServerSocketChannelFactory(Executors.newCachedThreadPool()));
        ClientBootstrap cb = new ClientBootstrap(
                newClientSocketChannelFactory(Executors.newCachedThreadPool()));

        sb.getPipeline().addLast("decoder", new SpdyFrameDecoder(spdyVersion, 10000, maxHeaderSize));
        sb.getPipeline().addLast("sessionHandler", new SpdySessionHandler(spdyVersion, true));
        sb.getPipeline().addLast("handler", captureHandler);

        cb.getPipeline().addLast("encoder", new SpdyFrameEncoder(spdyVersion));

        Channel sc = sb.bind(new InetSocketAddress(0));
        int port = ((InetSocketAddress) sc.getLocalAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(TestUtil.getLocalHost(), port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());
        Channel cc = ccf.getChannel();

        sendAndWaitForFrame(cc, frame, captureHandler);

        assertNotNull("version " + spdyVersion.getVersion() + ", not null message",
                captureHandler.message);
        String message = "version " + spdyVersion.getVersion() + ", should be SpdyHeadersFrame, was " +
                captureHandler.message.getClass();
        assertTrue(message, captureHandler.message instanceof SpdyHeadersFrame);
        SpdyHeadersFrame writtenFrame = (SpdyHeadersFrame) captureHandler.message;

        assertFalse("should not be truncated", writtenFrame.isTruncated());
        assertFalse("should not be invalid", writtenFrame.isInvalid());
        
        String val = writtenFrame.headers().get(expectedName);
        assertEquals(expectedValue, val);

        sc.close().awaitUninterruptibly();
        cb.shutdown();
        sb.shutdown();
        cb.releaseExternalResources();
        sb.releaseExternalResources();
    }

    @Test
    public void testZlibHeaders() throws Exception {
        
        SpdyHeadersFrame frame = new DefaultSpdySynStreamFrame(1, 0, (byte) 0);
        SpdyHeaders headers = frame.headers();
        
        headers.add(createString('a', 100), createString('b', 100));        
        SpdyHeadersFrame actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertTrue(equals(frame.headers(), actual.headers()));

        headers.clear();        
        actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertTrue(frame.headers().isEmpty());
        assertTrue(equals(frame.headers(), actual.headers()));

        headers.clear();        
        actual = roundTrip(frame, 4096);
        assertFalse("should not be truncated", actual.isTruncated());
        assertTrue(frame.headers().isEmpty());
        assertTrue(equals(frame.headers(), actual.headers()));
        
        headers.clear();        
        actual = roundTrip(frame, 128);
        assertFalse("should not be truncated", actual.isTruncated());
        assertTrue(frame.headers().isEmpty());
        assertTrue(equals(frame.headers(), actual.headers()));
        
        headers.clear();        
        headers.add(createString('c', 100), createString('d', 5000));        
        actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertTrue(equals(frame.headers(), actual.headers()));

        headers.clear();        
        headers.add(createString('e', 5000), createString('f', 100));        
        actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertTrue(equals(frame.headers(), actual.headers()));
        
        headers.clear();        
        headers.add(createString('g', 100), createString('h', 5000));        
        actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertTrue(equals(frame.headers(), actual.headers()));
        
        headers.clear();        
        headers.add(createString('i', 100), createString('j', 5000));        
        actual = roundTrip(frame, 4096);
        assertTrue("should be truncated", actual.isTruncated());
        assertTrue("headers should be empty", actual.headers().isEmpty());

        headers.clear();        
        headers.add(createString('k', 5000), createString('l', 100));        
        actual = roundTrip(frame, 4096);
        assertTrue("should be truncated", actual.isTruncated());
        assertTrue("headers should be empty", actual.headers().isEmpty());

        headers.clear();        
        headers.add(createString('m', 100), createString('n', 1000));        
        headers.add(createString('m', 100), createString('n', 1000));        
        headers.add(createString('m', 100), createString('n', 1000));        
        headers.add(createString('m', 100), createString('n', 1000));        
        headers.add(createString('m', 100), createString('n', 1000));        
        actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertEquals(1, actual.headers().names().size());
        assertEquals(5, actual.headers().getAll(createString('m', 100)).size());

        headers.clear();        
        headers.add(createString('o', 1000), createString('p', 100));        
        headers.add(createString('o', 1000), createString('p', 100));        
        headers.add(createString('o', 1000), createString('p', 100));        
        headers.add(createString('o', 1000), createString('p', 100));        
        headers.add(createString('o', 1000), createString('p', 100));        
        actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertEquals(1, actual.headers().names().size());
        assertEquals(5, actual.headers().getAll(createString('o', 1000)).size());
        
        headers.clear();        
        headers.add(createString('q', 100), createString('r', 1000));        
        headers.add(createString('q', 100), createString('r', 1000));        
        headers.add(createString('q', 100), createString('r', 1000));        
        headers.add(createString('q', 100), createString('r', 1000));        
        headers.add(createString('q', 100), createString('r', 1000));        
        actual = roundTrip(frame, 4096);
        assertTrue("should be truncated", actual.isTruncated());
        assertEquals(0, actual.headers().names().size());
        
        headers.clear();        
        headers.add(createString('s', 1000), createString('t', 100));        
        headers.add(createString('s', 1000), createString('t', 100));        
        headers.add(createString('s', 1000), createString('t', 100));        
        headers.add(createString('s', 1000), createString('t', 100));        
        headers.add(createString('s', 1000), createString('t', 100));        
        actual = roundTrip(frame, 4096);
        assertFalse("should be truncated", actual.isTruncated());
        assertEquals(1, actual.headers().names().size());
        assertEquals(5, actual.headers().getAll(createString('s', 1000)).size());
    }

    @Test
    public void testZlibReuseEncoderDecoder() throws Exception {
        SpdyHeadersFrame frame = new DefaultSpdySynStreamFrame(1, 0, (byte) 0);
        SpdyHeaders headers = frame.headers();

        SpdyHeaderBlockEncoder encoder = SpdyHeaderBlockEncoder.newInstance(SpdyVersion.SPDY_3_1, 6, 15, 8);
        SpdyHeaderBlockDecoder decoder = SpdyHeaderBlockDecoder.newInstance(8192);

        headers.add(createString('a', 100), createString('b', 100));        
        SpdyHeadersFrame actual = roundTrip(encoder, decoder, frame);
        assertFalse("should not be truncated", actual.isTruncated());
        assertTrue(equals(frame.headers(), actual.headers()));

        encoder.end();
        decoder.end();
        decoder.reset();
        
        headers.clear();        
        actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertTrue(frame.headers().isEmpty());
        assertTrue(equals(frame.headers(), actual.headers()));

        encoder.end();
        decoder.end();
        decoder.reset();

        headers.clear();        
        headers.add(createString('e', 5000), createString('f', 100));        
        actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertTrue(equals(frame.headers(), actual.headers()));

        encoder.end();
        decoder.end();
        decoder.reset();

        headers.clear();        
        headers.add(createString('g', 100), createString('h', 5000));        
        actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertTrue(equals(frame.headers(), actual.headers()));

        encoder.end();
        decoder.end();
        decoder.reset();

        headers.clear();        
        headers.add(createString('m', 100), createString('n', 1000));        
        headers.add(createString('m', 100), createString('n', 1000));        
        headers.add(createString('m', 100), createString('n', 1000));        
        headers.add(createString('m', 100), createString('n', 1000));        
        headers.add(createString('m', 100), createString('n', 1000));        
        actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertEquals(1, actual.headers().names().size());
        assertEquals(5, actual.headers().getAll(createString('m', 100)).size());

        encoder.end();
        decoder.end();
        decoder.reset();

        headers.clear();        
        headers.add(createString('o', 1000), createString('p', 100));        
        headers.add(createString('o', 1000), createString('p', 100));        
        headers.add(createString('o', 1000), createString('p', 100));        
        headers.add(createString('o', 1000), createString('p', 100));        
        headers.add(createString('o', 1000), createString('p', 100));        
        actual = roundTrip(frame, 8192);
        assertFalse("should not be truncated", actual.isTruncated());
        assertEquals(1, actual.headers().names().size());
        assertEquals(5, actual.headers().getAll(createString('o', 1000)).size());
    }
    
    private SpdyHeadersFrame roundTrip(SpdyHeadersFrame frame, int maxHeaderSize) throws Exception {
        SpdyHeaderBlockEncoder encoder = SpdyHeaderBlockEncoder.newInstance(SpdyVersion.SPDY_3_1, 6, 15, 8);
        SpdyHeaderBlockDecoder decoder = SpdyHeaderBlockDecoder.newInstance(maxHeaderSize);
        return roundTrip(encoder, decoder, frame);
    }
    
    private SpdyHeadersFrame roundTrip(SpdyHeaderBlockEncoder encoder, SpdyHeaderBlockDecoder decoder, 
            SpdyHeadersFrame frame) throws Exception {
        ChannelBuffer encoded = encoder.encode(frame);
        
        SpdyHeadersFrame actual = new DefaultSpdySynStreamFrame(1, 0, (byte) 0);
        
        decoder.decode(encoded, actual);
        return actual;
    }
    
    private static boolean equals(SpdyHeaders h1, SpdyHeaders h2) {
        if (!h1.names().equals(h2.names())) return false;
        for (String name : h1.names()) {
            if (!h1.getAll(name).equals(h2.getAll(name))) {
                return false;
            }
        }
        return true;
    }
    
    private static void sendAndWaitForFrame(Channel cc, SpdyFrame frame, CaptureHandler handler) {
        cc.write(frame);
        long theFuture = System.currentTimeMillis() + 3000;
        while (handler.message == null && System.currentTimeMillis() < theFuture) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // Ignore.
            }
        }
    }

    private static void addHeader(SpdyHeadersFrame frame, int headerNameSize, int headerValueSize) {
        frame.headers().add("k", "v");
        String headerName = createString('h', headerNameSize);
        String headerValue = createString('h', headerValueSize);
        frame.headers().add(headerName, headerValue);
    }
    
    private static String createString(char c, int rep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rep; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    protected ChannelFactory newClientSocketChannelFactory(Executor executor) {
        return new NioClientSocketChannelFactory(executor, executor);
    }

    protected ChannelFactory newServerSocketChannelFactory(Executor executor) {
        return new NioServerSocketChannelFactory(executor, executor);
    }

    private static class CaptureHandler extends SimpleChannelUpstreamHandler {
        public volatile Object message;

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            message = e.getMessage();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
            e.getCause().printStackTrace();
            message = e.getCause();
        }
    }
}

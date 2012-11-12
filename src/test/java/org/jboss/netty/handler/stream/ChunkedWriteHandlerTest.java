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
package org.jboss.netty.handler.stream;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.handler.codec.embedder.EncoderEmbedder;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class ChunkedWriteHandlerTest {
    private static final byte[] BYTES = new byte[1024 * 64];
    private static final File TMP;

    static {
        for (int i = 0; i < BYTES.length; i++) {
            BYTES[i] = (byte) i;
        }

        FileOutputStream out = null;
        try {
            TMP = File.createTempFile("netty-chunk-", ".tmp");
            TMP.deleteOnExit();
            out = new FileOutputStream(TMP);
            out.write(BYTES);
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    // See #310
    @Test
    public void testChunkedStream() {
        check(new ChunkedStream(new ByteArrayInputStream(BYTES)));

        check(new ChunkedStream(new ByteArrayInputStream(BYTES)), new ChunkedStream(new ByteArrayInputStream(BYTES)), new ChunkedStream(new ByteArrayInputStream(BYTES)));

    }

    @Test
    public void testChunkedNioStream() {
        check(new ChunkedNioStream(Channels.newChannel(new ByteArrayInputStream(BYTES))));

        check(new ChunkedNioStream(Channels.newChannel(new ByteArrayInputStream(BYTES))), new ChunkedNioStream(Channels.newChannel(new ByteArrayInputStream(BYTES))), new ChunkedNioStream(Channels.newChannel(new ByteArrayInputStream(BYTES))));

    }


    // Test case which shows that there is not a bug like stated here:
    // http://stackoverflow.com/questions/10409241/why-is-close-channelfuturelistener-not-notified/10426305#comment14126161_10426305
    @Test
    public void testListenerNotifiedWhenIsEnd() {
        ChannelBuffer buffer = ChannelBuffers.copiedBuffer("Test", CharsetUtil.ISO_8859_1);
                
        ChunkedInput input = new ChunkedInput() {
            private boolean done;
            private final ChannelBuffer buffer = ChannelBuffers.copiedBuffer("Test", CharsetUtil.ISO_8859_1);
            
            public Object nextChunk() throws Exception {
                done = true;
                return buffer.duplicate();
            }
            
            public boolean isEndOfInput() throws Exception {
                return done;
            }
            
            public boolean hasNextChunk() throws Exception {
                return true;
            }
            
            public void close() throws Exception {
                
            }
        };
        
        final AtomicBoolean listenerNotified = new AtomicBoolean(false);
        final ChannelFutureListener listener = new ChannelFutureListener() {
            
            public void operationComplete(ChannelFuture future) throws Exception {
                listenerNotified.set(true);
            }
        };
        
        SimpleChannelDownstreamHandler testHandler = new SimpleChannelDownstreamHandler() {

            @Override
            public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
                super.writeRequested(ctx, e);
                
                e.getFuture().setSuccess();
            }
            
        };
        
        EncoderEmbedder<ChannelBuffer> handler = new EncoderEmbedder<ChannelBuffer>(new ChunkedWriteHandler(), testHandler) {
            @Override
            public boolean offer(Object input) {
                ChannelFuture future = org.jboss.netty.channel.Channels.write(getChannel(), input);
                future.addListener(listener);
                future.awaitUninterruptibly();
                return !isEmpty();
            }
            
        };
        
        assertTrue(handler.offer(input));
        assertTrue(handler.finish());
        
        // the listener should have been notified
        assertTrue(listenerNotified.get());
        
        assertEquals(buffer, handler.poll());
        assertNull(handler.poll());
        
    }
    
    @Test
    public void testChunkedFile() throws IOException {
        check(new ChunkedFile(TMP));

        check(new ChunkedFile(TMP), new ChunkedFile(TMP), new ChunkedFile(TMP));
    }

    @Test
    public void testChunkedNioFile() throws IOException {
        check(new ChunkedNioFile(TMP));

        check(new ChunkedNioFile(TMP), new ChunkedNioFile(TMP), new ChunkedNioFile(TMP));
    }

    private static void check(ChunkedInput... inputs) {
        EncoderEmbedder<ChannelBuffer> embedder = new EncoderEmbedder<ChannelBuffer>(new ChunkedWriteHandler());
        for (ChunkedInput input: inputs) {
            embedder.offer(input);
        }

        assertTrue(embedder.finish());

        int i = 0;
        int read = 0;
        for (;;) {
            ChannelBuffer buffer = embedder.poll();
            if (buffer == null) {
                break;
            }
            while (buffer.readable()) {
                assertEquals(BYTES[i++], buffer.readByte());
                read++;
                if (i == BYTES.length) {
                    i = 0;
                }
            }
        }

        assertEquals(BYTES.length * inputs.length, read);
    }
}

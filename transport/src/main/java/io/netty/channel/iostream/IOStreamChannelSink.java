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
package io.netty.channel.iostream;

import static io.netty.channel.Channels.*;

import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.util.concurrent.ExecutorService;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.AbstractChannelSink;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelState;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.MessageEvent;

/**
 * A {@link io.netty.channel.ChannelSink} implementation which reads from
 * an {@link java.io.InputStream} and writes to an {@link java.io.OutputStream}.
 */
public class IOStreamChannelSink extends AbstractChannelSink {

    private static class ReadRunnable implements Runnable {

        private final IOStreamChannelSink channelSink;

        public ReadRunnable(final IOStreamChannelSink channelSink) {
            this.channelSink = channelSink;
        }

        @Override
        public void run() {

            PushbackInputStream in = channelSink.inputStream;

            while (channelSink.channel.isOpen()) {

                byte[] buf;
                int readBytes;
                try {
                    int bytesToRead = in.available();
                    if (bytesToRead > 0) {
                        buf = new byte[bytesToRead];
                        readBytes = in.read(buf);
                    } else {
                        // peek into the stream if it was closed (value=-1)
                        int b = in.read();
                        if (b < 0) {
                            break;
                        }
                        // push back the byte which was read too much
                        in.unread(b);
                        continue;
                    }
                } catch (Throwable t) {
                    if (!channelSink.channel.getCloseFuture().isDone()) {
                        fireExceptionCaught(channelSink.channel, t);
                    }
                    break;
                }

                fireMessageReceived(channelSink.channel, ChannelBuffers.wrappedBuffer(buf, 0, readBytes));
            }

            // Clean up.
            close(channelSink.channel);
        }
    }

    private final ExecutorService executorService;

    private IOStreamChannel channel;

    public IOStreamChannelSink(final ExecutorService executorService) {
        this.executorService = executorService;
    }

    public boolean isConnected() {
        return inputStream != null && outputStream != null;
    }

    public IOStreamAddress getRemoteAddress() {
        return remoteAddress;
    }

    public boolean isBound() {
        return false;
    }

    public ChannelConfig getConfig() {
        return config;
    }

    public void setChannel(final IOStreamChannel channel) {
        this.channel = channel;
    }

    private IOStreamAddress remoteAddress;

    private OutputStream outputStream;

    private PushbackInputStream inputStream;

    private final ChannelConfig config = new DefaultChannelConfig();

    @Override
    public void eventSunk(final ChannelPipeline pipeline, final ChannelEvent e) throws Exception {

        final ChannelFuture future = e.getFuture();

        if (e instanceof ChannelStateEvent) {

            final ChannelStateEvent stateEvent = (ChannelStateEvent) e;
            final ChannelState state = stateEvent.getState();
            final Object value = stateEvent.getValue();

            switch (state) {

            case OPEN:
                if (Boolean.FALSE.equals(value)) {
                    outputStream = null;
                    inputStream = null;
                    ((IOStreamChannel) e.getChannel()).doSetClosed();
                }
                break;

            case BOUND:
                throw new UnsupportedOperationException();

            case CONNECTED:
                if (value != null) {
                    remoteAddress = (IOStreamAddress) value;
                    outputStream = remoteAddress.getOutputStream();
                    inputStream = new PushbackInputStream(remoteAddress.getInputStream());
                    executorService.execute(new ReadRunnable(this));
                    future.setSuccess();
                }
                break;

            case INTEREST_OPS:
                // TODO implement
                throw new UnsupportedOperationException();

            }

        } else if (e instanceof MessageEvent) {

            final MessageEvent event = (MessageEvent) e;
            if (event.getMessage() instanceof ChannelBuffer) {

                final ChannelBuffer buffer = (ChannelBuffer) event.getMessage();
                buffer.readBytes(outputStream, buffer.readableBytes());
                outputStream.flush();
                future.setSuccess();

            } else {
                throw new IllegalArgumentException("Only ChannelBuffer objects are supported to be written onto the IOStreamChannelSink! " + "Please check if the encoder pipeline is configured correctly.");
            }
        }
    }
}

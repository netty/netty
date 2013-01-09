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
package io.netty.channel.rxtx;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.TooManyListenersException;
import java.util.concurrent.Executor;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.AbstractChannelSink;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelSink;
import io.netty.channel.ChannelState;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.Channels;
import io.netty.channel.DefaultChannelFuture;
import io.netty.channel.MessageEvent;
import io.netty.channel.UpstreamMessageEvent;

/**
 * A {@link ChannelSink} implementation of the RXTX support for Netty.
 */
public class RxtxChannelSink extends AbstractChannelSink {

    private static class WriteRunnable implements Runnable {

        private final DefaultChannelFuture future;

        private final RxtxChannelSink channelSink;

        private final ChannelBuffer message;

        public WriteRunnable(final DefaultChannelFuture future, final RxtxChannelSink channelSink,
                             final ChannelBuffer message) {
            this.future = future;
            this.channelSink = channelSink;
            this.message = message;
        }

        @Override
        public void run() {
            try {

                channelSink.outputStream.write(message.array(), message.readerIndex(), message.readableBytes());
                channelSink.outputStream.flush();
                future.setSuccess();

            } catch (Exception e) {
                future.setFailure(e);
            }
        }
    }

    private static class ConnectRunnable implements Runnable {

        private final DefaultChannelFuture channelFuture;

        private final RxtxChannelSink channelSink;

        ConnectRunnable(final DefaultChannelFuture channelFuture, final RxtxChannelSink channelSink) {
            this.channelFuture = channelFuture;
            this.channelSink = channelSink;
        }

        @Override
        public void run() {

            if (channelSink.closed) {
                channelFuture.setFailure(new Exception("Channel is already closed."));
            } else {
                try {
                    connectInternal();
                    channelFuture.setSuccess();
                } catch (Exception e) {
                    channelFuture.setFailure(e);
                }
            }

        }

        private void connectInternal()
                throws NoSuchPortException, PortInUseException, UnsupportedCommOperationException, IOException,
                TooManyListenersException {

            final CommPort commPort;
            try {

                final CommPortIdentifier cpi =
                        CommPortIdentifier.getPortIdentifier(channelSink.remoteAddress.getDeviceAddress());
                commPort = cpi.open(this.getClass().getName(), 1000);

            } catch (NoSuchPortException e) {
                throw e;
            } catch (PortInUseException e) {
                throw e;
            }

            channelSink.serialPort = (SerialPort) commPort;
            channelSink.serialPort.addEventListener(new RXTXSerialPortEventListener(channelSink));
            channelSink.serialPort.notifyOnDataAvailable(true);
            channelSink.serialPort.setSerialPortParams(
                    channelSink.config.getBaudrate(),
                    channelSink.config.getDatabits().getValue(),
                    channelSink.config.getStopbits().getValue(),
                    channelSink.config.getParitybit().getValue()
            );

            channelSink.serialPort.setDTR(channelSink.config.isDtr());
            channelSink.serialPort.setRTS(channelSink.config.isRts());

            channelSink.outputStream = new BufferedOutputStream(channelSink.serialPort.getOutputStream());
            channelSink.inputStream = new BufferedInputStream(channelSink.serialPort.getInputStream());
        }
    }

    private static class DisconnectRunnable implements Runnable {

        private final DefaultChannelFuture channelFuture;

        private final RxtxChannelSink channelSink;

        public DisconnectRunnable(final DefaultChannelFuture channelFuture, final RxtxChannelSink channelSink) {
            this.channelFuture = channelFuture;
            this.channelSink = channelSink;
        }

        @Override
        public void run() {
            if (channelSink.closed) {
                channelFuture.setFailure(new Exception("Channel is already closed."));
            } else {
                try {
                    disconnectInternal();
                    channelSink.channel.doSetClosed();
                } catch (Exception e) {
                    channelFuture.setFailure(e);
                }
            }
        }

        private void disconnectInternal() throws Exception {

            Exception exception = null;

            try {
                if (channelSink.inputStream != null) {
                    channelSink.inputStream.close();
                }
            } catch (IOException e) {
                exception = e;
            }

            try {
                if (channelSink.outputStream != null) {
                    channelSink.outputStream.close();
                }
            } catch (IOException e) {
                exception = e;
            }

            if (channelSink.serialPort != null) {
                channelSink.serialPort.removeEventListener();
                channelSink.serialPort.close();
            }

            channelSink.inputStream = null;
            channelSink.outputStream = null;
            channelSink.serialPort = null;

            if (exception != null) {
                throw exception;
            }
        }
    }

    private final Executor executor;

    final RxtxChannelConfig config;

    RxtxChannel channel;

    public RxtxChannelSink(final Executor executor) {
        this.executor = executor;
        config = new RxtxChannelConfig();
    }

    public boolean isConnected() {
        return inputStream != null && outputStream != null;
    }

    public RxtxDeviceAddress getRemoteAddress() {
        return remoteAddress;
    }

    public boolean isBound() {
        return false;
    }

    public ChannelConfig getConfig() {
        return config;
    }

    public void setChannel(final RxtxChannel channel) {
        this.channel = channel;
    }

    private static class RXTXSerialPortEventListener implements SerialPortEventListener {

        private final RxtxChannelSink channelSink;

        public RXTXSerialPortEventListener(final RxtxChannelSink channelSink) {
            this.channelSink = channelSink;
        }

        @Override
        public void serialEvent(final SerialPortEvent event) {
            switch (event.getEventType()) {
                case SerialPortEvent.DATA_AVAILABLE:
                    try {
                        if (channelSink.inputStream != null && channelSink.inputStream.available() > 0) {
                            int available = channelSink.inputStream.available();
                            byte[] buffer = new byte[available];
                            int read = channelSink.inputStream.read(buffer);
                            if (read > 0) {
                                ChannelBuffer channelBuffer = ChannelBuffers.wrappedBuffer(buffer, 0, read);
                                UpstreamMessageEvent upstreamMessageEvent = new UpstreamMessageEvent(
                                        channelSink.channel,
                                        channelBuffer,
                                        channelSink.getRemoteAddress()
                                );
                                channelSink.channel.getPipeline().sendUpstream(upstreamMessageEvent);
                            }
                        }
                    } catch (IOException e) {
                        Channels.fireExceptionCaught(channelSink.channel, e);
                        channelSink.channel.close();
                    }
                    break;
            }
        }
    }

    RxtxDeviceAddress remoteAddress;

    BufferedOutputStream outputStream;

    BufferedInputStream inputStream;

    SerialPort serialPort;

    volatile boolean closed;

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
                        executor.execute(new DisconnectRunnable((DefaultChannelFuture) future, this));
                    }
                    break;

                case BOUND:
                    throw new UnsupportedOperationException();

                case CONNECTED:
                    if (value != null) {
                        remoteAddress = (RxtxDeviceAddress) value;
                        executor.execute(new ConnectRunnable((DefaultChannelFuture) future, this));
                    } else {
                        executor.execute(new DisconnectRunnable((DefaultChannelFuture) future, this));
                    }
                    break;

                case INTEREST_OPS:
                    throw new UnsupportedOperationException();

            }

        } else if (e instanceof MessageEvent) {

            final MessageEvent event = (MessageEvent) e;
            if (event.getMessage() instanceof ChannelBuffer) {
                executor.execute(
                        new WriteRunnable((DefaultChannelFuture) future, this, (ChannelBuffer) event.getMessage())
                );
            } else {
                throw new IllegalArgumentException(
                        "Only ChannelBuffer objects are supported to be written onto the RXTXChannelSink! "
                                + "Please check if the encoder pipeline is configured correctly."
                );
            }
        }
    }
}

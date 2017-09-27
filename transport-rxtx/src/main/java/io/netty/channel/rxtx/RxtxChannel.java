/*
 * Copyright 2013 The Netty Project
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
import gnu.io.SerialPort;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.oio.OioByteStreamChannel;

import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import static io.netty.channel.rxtx.RxtxChannelOption.BAUD_RATE;
import static io.netty.channel.rxtx.RxtxChannelOption.DATA_BITS;
import static io.netty.channel.rxtx.RxtxChannelOption.DTR;
import static io.netty.channel.rxtx.RxtxChannelOption.PARITY_BIT;
import static io.netty.channel.rxtx.RxtxChannelOption.READ_TIMEOUT;
import static io.netty.channel.rxtx.RxtxChannelOption.RTS;
import static io.netty.channel.rxtx.RxtxChannelOption.STOP_BITS;
import static io.netty.channel.rxtx.RxtxChannelOption.WAIT_TIME;

/**
 * A channel to a serial device using the RXTX library.
 *
 * @deprecated this transport will be removed in the next major version.
 */
@Deprecated
public class RxtxChannel extends OioByteStreamChannel {

    private static final RxtxDeviceAddress LOCAL_ADDRESS = new RxtxDeviceAddress("localhost");

    private final RxtxChannelConfig config;

    private boolean open = true;
    private RxtxDeviceAddress deviceAddress;
    private SerialPort serialPort;

    public RxtxChannel() {
        super(null);

        config = new DefaultRxtxChannelConfig(this);
    }

    @Override
    public RxtxChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new RxtxUnsafe();
    }

    @Override
    protected void doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        RxtxDeviceAddress remote = (RxtxDeviceAddress) remoteAddress;
        final CommPortIdentifier cpi = CommPortIdentifier.getPortIdentifier(remote.value());
        final CommPort commPort = cpi.open(getClass().getName(), 1000);
        commPort.enableReceiveTimeout(config().getOption(READ_TIMEOUT));
        deviceAddress = remote;

        serialPort = (SerialPort) commPort;
    }

    protected void doInit() throws Exception {
        serialPort.setSerialPortParams(
            config().getOption(BAUD_RATE),
            config().getOption(DATA_BITS).value(),
            config().getOption(STOP_BITS).value(),
            config().getOption(PARITY_BIT).value()
        );
        serialPort.setDTR(config().getOption(DTR));
        serialPort.setRTS(config().getOption(RTS));

        activate(serialPort.getInputStream(), serialPort.getOutputStream());
    }

    @Override
    public RxtxDeviceAddress localAddress() {
        return (RxtxDeviceAddress) super.localAddress();
    }

    @Override
    public RxtxDeviceAddress remoteAddress() {
        return (RxtxDeviceAddress) super.remoteAddress();
    }

    @Override
    protected RxtxDeviceAddress localAddress0() {
        return LOCAL_ADDRESS;
    }

    @Override
    protected RxtxDeviceAddress remoteAddress0() {
        return deviceAddress;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doClose() throws Exception {
        open = false;
        try {
           super.doClose();
        } finally {
            if (serialPort != null) {
                serialPort.removeEventListener();
                serialPort.close();
                serialPort = null;
            }
        }
    }

    @Override
    protected boolean isInputShutdown() {
        return !open;
    }

    @Override
    protected ChannelFuture shutdownInput() {
        return newFailedFuture(new UnsupportedOperationException("shutdownInput"));
    }

    private final class RxtxUnsafe extends AbstractUnsafe {
        @Override
        public void connect(
                final SocketAddress remoteAddress,
                final SocketAddress localAddress, final ChannelPromise promise) {
            if (!promise.setUncancellable() || !ensureOpen(promise)) {
                return;
            }

            try {
                final boolean wasActive = isActive();
                doConnect(remoteAddress, localAddress);

                int waitTime = config().getOption(WAIT_TIME);
                if (waitTime > 0) {
                    eventLoop().schedule(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                doInit();
                                safeSetSuccess(promise);
                                if (!wasActive && isActive()) {
                                    pipeline().fireChannelActive();
                                }
                            } catch (Throwable t) {
                                safeSetFailure(promise, t);
                                closeIfClosed();
                            }
                        }
                   }, waitTime, TimeUnit.MILLISECONDS);
                } else {
                    doInit();
                    safeSetSuccess(promise);
                    if (!wasActive && isActive()) {
                        pipeline().fireChannelActive();
                    }
                }
            } catch (Throwable t) {
                safeSetFailure(promise, t);
                closeIfClosed();
            }
        }
    }
}

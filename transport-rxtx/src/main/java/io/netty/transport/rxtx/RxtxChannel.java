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
package io.netty.transport.rxtx;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import io.netty.buffer.BufType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.socket.oio.AbstractOioByteChannel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.channels.NotYetConnectedException;

import static io.netty.transport.rxtx.RxtxChannelOptions.*;

/**
 * A channel to a serial device using the RXTX library.
 */
public class RxtxChannel extends AbstractOioByteChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(BufType.BYTE, true);

    private final ChannelConfig config;

    private RxtxDeviceAddress deviceAddress;
    private SerialPort serialPort;
    private InputStream in;
    private OutputStream out;

    public RxtxChannel() {
        super(null, null);

        config = new RxtxChannelConfig(this);
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isActive() {
        return in != null && out != null;
    }

    @Override
    protected int available() {
        try {
            return in.available();
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    protected int doReadBytes(ByteBuf buf) throws Exception {
        try {
            return buf.writeBytes(in, buf.writableBytes());
        } catch (SocketTimeoutException e) {
            return 0;
        }
    }

    @Override
    protected void doWriteBytes(ByteBuf buf) throws Exception {
        if (out == null) {
            throw new NotYetConnectedException();
        }
        buf.readBytes(out, buf.readableBytes());
    }

    @Override
    protected void doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        RxtxDeviceAddress remote = (RxtxDeviceAddress) remoteAddress;
        final CommPortIdentifier cpi =
                CommPortIdentifier.getPortIdentifier(remote.getDeviceAddress());
        final CommPort commPort = cpi.open(getClass().getName(), 1000);

        deviceAddress = remote;

        serialPort = (SerialPort) commPort;
        serialPort.setSerialPortParams(
            config().getOption(BAUD_RATE),
            config().getOption(DATA_BITS).value(),
            config().getOption(STOP_BITS).value(),
            config().getOption(PARITY_BIT).value()
        );
        serialPort.setDTR(config().getOption(DTR));
        serialPort.setRTS(config().getOption(RTS));

        out = serialPort.getOutputStream();
        in = serialPort.getInputStream();
    }

    @Override
    protected SocketAddress localAddress0() {
        return null;
    }

    @Override
    protected SocketAddress remoteAddress0() {
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
        IOException ex = null;

        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            ex = e;
        }

        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            ex = e;
        }

        if (serialPort != null) {
            serialPort.removeEventListener();
            serialPort.close();
        }

        in = null;
        out = null;
        serialPort = null;

        if (ex != null) {
            throw ex;
        }
    }
}

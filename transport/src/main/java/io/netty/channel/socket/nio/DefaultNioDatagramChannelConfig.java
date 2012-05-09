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
package io.netty.channel.socket.nio;

import io.netty.channel.ChannelException;
import io.netty.channel.socket.DefaultDatagramChannelConfig;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.util.internal.ConversionUtil;
import io.netty.util.internal.DetectionUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.channels.DatagramChannel;
import java.util.Enumeration;
import java.util.Map;

/**
 * The default {@link NioSocketChannelConfig} implementation.
 */
class DefaultNioDatagramChannelConfig extends DefaultDatagramChannelConfig
        implements NioDatagramChannelConfig {

    private static final InternalLogger logger =
            InternalLoggerFactory
                    .getInstance(DefaultNioDatagramChannelConfig.class);

    private volatile int writeBufferHighWaterMark = 64 * 1024;
    private volatile int writeBufferLowWaterMark = 32 * 1024;
    private volatile int writeSpinCount = 16;

    private final DatagramChannel channel;

    DefaultNioDatagramChannelConfig(DatagramChannel channel) {
        super(channel.socket());
        this.channel = channel;
    }

    @Override
    public void setOptions(Map<String, Object> options) {
        super.setOptions(options);
        if (getWriteBufferHighWaterMark() < getWriteBufferLowWaterMark()) {
            // Recover the integrity of the configuration with a sensible value.
            setWriteBufferLowWaterMark0(getWriteBufferHighWaterMark() >>> 1);
            if (logger.isWarnEnabled()) {
                // Notify the user about misconfiguration.
                logger.warn("writeBufferLowWaterMark cannot be greater than "
                        + "writeBufferHighWaterMark; setting to the half of the "
                        + "writeBufferHighWaterMark.");
            }

        }
    }

    @Override
    public boolean setOption(String key, Object value) {
        if (super.setOption(key, value)) {
            return true;
        }

        if (key.equals("writeBufferHighWaterMark")) {
            setWriteBufferHighWaterMark0(ConversionUtil.toInt(value));
        } else if (key.equals("writeBufferLowWaterMark")) {
            setWriteBufferLowWaterMark0(ConversionUtil.toInt(value));
        } else if (key.equals("writeSpinCount")) {
            setWriteSpinCount(ConversionUtil.toInt(value));
        } else {
            return false;
        }
        return true;
    }

    @Override
    public int getWriteBufferHighWaterMark() {
        return writeBufferHighWaterMark;
    }

    @Override
    public void setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        if (writeBufferHighWaterMark < getWriteBufferLowWaterMark()) {
            throw new IllegalArgumentException(
                    "writeBufferHighWaterMark cannot be less than " +
                            "writeBufferLowWaterMark (" +
                            getWriteBufferLowWaterMark() + "): " +
                            writeBufferHighWaterMark);
        }
        setWriteBufferHighWaterMark0(writeBufferHighWaterMark);
    }

    private void setWriteBufferHighWaterMark0(int writeBufferHighWaterMark) {
        if (writeBufferHighWaterMark < 0) {
            throw new IllegalArgumentException("writeBufferHighWaterMark: " +
                    writeBufferHighWaterMark);
        }
        this.writeBufferHighWaterMark = writeBufferHighWaterMark;
    }

    @Override
    public int getWriteBufferLowWaterMark() {
        return writeBufferLowWaterMark;
    }

    @Override
    public void setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        if (writeBufferLowWaterMark > getWriteBufferHighWaterMark()) {
            throw new IllegalArgumentException(
                    "writeBufferLowWaterMark cannot be greater than " +
                            "writeBufferHighWaterMark (" +
                            getWriteBufferHighWaterMark() + "): " +
                            writeBufferLowWaterMark);
        }
        setWriteBufferLowWaterMark0(writeBufferLowWaterMark);
    }

    private void setWriteBufferLowWaterMark0(int writeBufferLowWaterMark) {
        if (writeBufferLowWaterMark < 0) {
            throw new IllegalArgumentException("writeBufferLowWaterMark: " +
                    writeBufferLowWaterMark);
        }
        this.writeBufferLowWaterMark = writeBufferLowWaterMark;
    }

    @Override
    public int getWriteSpinCount() {
        return writeSpinCount;
    }

    @Override
    public void setWriteSpinCount(int writeSpinCount) {
        if (writeSpinCount <= 0) {
            throw new IllegalArgumentException(
                    "writeSpinCount must be a positive integer.");
        }
        this.writeSpinCount = writeSpinCount;
    }
    
    @Override
    public void setNetworkInterface(NetworkInterface networkInterface) {
        if (DetectionUtil.javaVersion() < 7) {
            throw new UnsupportedOperationException();
        } else {
            try {
                channel.setOption(StandardSocketOptions.IP_MULTICAST_IF, networkInterface);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }
    }

    @Override
    public NetworkInterface getNetworkInterface() {
        if (DetectionUtil.javaVersion() < 7) {
            throw new UnsupportedOperationException();
        } else {
            try {
                return (NetworkInterface) channel.getOption(StandardSocketOptions.IP_MULTICAST_IF);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }
    }

    @Override
    public int getTimeToLive() {
        if (DetectionUtil.javaVersion() < 7) {
            throw new UnsupportedOperationException();
        } else {
            try {
                return (int) channel.getOption(StandardSocketOptions.IP_MULTICAST_TTL);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }
    }

    @Override
    public void setTimeToLive(int ttl) {
        if (DetectionUtil.javaVersion() < 7) {
            throw new UnsupportedOperationException();
        } else {
            try {
                channel.setOption(StandardSocketOptions.IP_MULTICAST_TTL, ttl);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }
       
    }
    
    @Override
    public InetAddress getInterface() {
        NetworkInterface inf = getNetworkInterface();
        if (inf == null) {
            return null;
        } else {
            Enumeration<InetAddress> addresses = inf.getInetAddresses();
            if (addresses.hasMoreElements()) {
                return addresses.nextElement();
            }
            return null;
        }
    }

    @Override
    public void setInterface(InetAddress interfaceAddress) {
        try {
            setNetworkInterface(NetworkInterface.getByInetAddress(interfaceAddress));
        } catch (SocketException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public boolean isLoopbackModeDisabled() {
        if (DetectionUtil.javaVersion() < 7) {
            throw new UnsupportedOperationException();
        } else {
            try {
                return (Boolean) channel.getOption(StandardSocketOptions.IP_MULTICAST_LOOP);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }
    }

    @Override
    public void setLoopbackModeDisabled(boolean loopbackModeDisabled) {
        if (DetectionUtil.javaVersion() < 7) {
            throw new UnsupportedOperationException();
        } else {
            try {
                channel.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, loopbackModeDisabled);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }
    }

}

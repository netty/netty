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
package org.jboss.netty.channel.socket.nio;

import java.net.Socket;
import java.util.Map;

import org.jboss.netty.channel.socket.DefaultSocketChannelConfig;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.ConversionUtil;

/**
 * The default {@link NioSocketChannelConfig} implementation.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
class DefaultNioSocketChannelConfig extends DefaultSocketChannelConfig
        implements NioSocketChannelConfig {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(DefaultNioSocketChannelConfig.class);

    private volatile int writeBufferHighWaterMark = 64 * 1024;
    private volatile int writeBufferLowWaterMark  = 32 * 1024;
    private volatile ReceiveBufferSizePredictor predictor =
        new DefaultReceiveBufferSizePredictor();
    private volatile int writeSpinCount = 16;

    DefaultNioSocketChannelConfig(Socket socket) {
        super(socket);
    }

    @Override
    public void setOptions(Map<String, Object> options) {
        super.setOptions(options);
        if (getWriteBufferHighWaterMark() < getWriteBufferLowWaterMark()) {
            // Recover the integrity of the configuration with a sensible value.
            setWriteBufferLowWaterMark0(getWriteBufferHighWaterMark() >>> 1);
            // Notify the user about misconfiguration.
            logger.warn(
                    "writeBufferLowWaterMark cannot be greater than " +
                    "writeBufferHighWaterMark; setting to the half of the " +
                    "writeBufferHighWaterMark.");
        }
    }

    @Override
    protected boolean setOption(String key, Object value) {
        if (super.setOption(key, value)) {
            return true;
        }

        if (key.equals("readWriteFair")) {
            setReadWriteFair(true); // Deprecated
        } else if (key.equals("writeBufferHighWaterMark")) {
            setWriteBufferHighWaterMark0(ConversionUtil.toInt(value));
        } else if (key.equals("writeBufferLowWaterMark")) {
            setWriteBufferLowWaterMark0(ConversionUtil.toInt(value));
        } else if (key.equals("writeSpinCount")) {
            setWriteSpinCount(ConversionUtil.toInt(value));
        } else if (key.equals("receiveBufferSizePredictor")) {
            setReceiveBufferSizePredictor((ReceiveBufferSizePredictor) value);
        } else {
            return false;
        }
        return true;
    }

    public int getWriteBufferHighWaterMark() {
        return writeBufferHighWaterMark;
    }

    public void setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        if (writeBufferHighWaterMark < getWriteBufferLowWaterMark()) {
            throw new IllegalArgumentException(
                    "writeBufferHighWaterMark cannot be less than " +
                    "writeBufferLowWaterMark (" + getWriteBufferLowWaterMark() + "): " +
                    writeBufferHighWaterMark);
        }
        setWriteBufferHighWaterMark0(writeBufferHighWaterMark);
    }

    private void setWriteBufferHighWaterMark0(int writeBufferHighWaterMark) {
        if (writeBufferHighWaterMark < 0) {
            throw new IllegalArgumentException(
                    "writeBufferHighWaterMark: " + writeBufferHighWaterMark);
        }
        this.writeBufferHighWaterMark = writeBufferHighWaterMark;
    }

    public int getWriteBufferLowWaterMark() {
        return writeBufferLowWaterMark;
    }

    public void setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        if (writeBufferLowWaterMark > getWriteBufferHighWaterMark()) {
            throw new IllegalArgumentException(
                    "writeBufferLowWaterMark cannot be greater than " +
                    "writeBufferHighWaterMark (" + getWriteBufferHighWaterMark() + "): " +
                    writeBufferLowWaterMark);
        }
        setWriteBufferLowWaterMark0(writeBufferLowWaterMark);
    }

    private void setWriteBufferLowWaterMark0(int writeBufferLowWaterMark) {
        if (writeBufferLowWaterMark < 0) {
            throw new IllegalArgumentException(
                    "writeBufferLowWaterMark: " + writeBufferLowWaterMark);
        }
        this.writeBufferLowWaterMark = writeBufferLowWaterMark;
    }

    public int getWriteSpinCount() {
        return writeSpinCount;
    }

    public void setWriteSpinCount(int writeSpinCount) {
        if (writeSpinCount <= 0) {
            throw new IllegalArgumentException(
                    "writeSpinCount must be a positive integer.");
        }
        this.writeSpinCount = writeSpinCount;
    }

    public ReceiveBufferSizePredictor getReceiveBufferSizePredictor() {
        return predictor;
    }

    public void setReceiveBufferSizePredictor(
            ReceiveBufferSizePredictor predictor) {
        if (predictor == null) {
            throw new NullPointerException("predictor");
        }
        this.predictor = predictor;
    }

    public boolean isReadWriteFair() {
        logger.warn(
                "Detected an access to a deprecated configuration parameter: " +
                "readWriteFair");
        return true;
    }

    public void setReadWriteFair(boolean readWriteFair) {
        isReadWriteFair();
    }
}

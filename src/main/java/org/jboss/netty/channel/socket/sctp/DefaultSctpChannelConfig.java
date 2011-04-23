/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel.socket.sctp;

import com.sun.nio.sctp.SctpChannel;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.channel.*;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.internal.ConversionUtil;

import java.util.Map;

/**
 * The default {@link SctpChannelConfig} implementation.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author Jestan Nirojan
 *
 * @version $Rev$, $Date$
 *
 */
class DefaultSctpChannelConfig implements SctpChannelConfig {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(DefaultSctpChannelConfig.class);

    private static final ReceiveBufferSizePredictorFactory DEFAULT_PREDICTOR_FACTORY =
        new AdaptiveReceiveBufferSizePredictorFactory();

    private volatile int writeBufferHighWaterMark = 64 * 1024;
    private volatile int writeBufferLowWaterMark  = 32 * 1024;
    private volatile ReceiveBufferSizePredictor predictor;
    private volatile ReceiveBufferSizePredictorFactory predictorFactory = DEFAULT_PREDICTOR_FACTORY;
    private volatile int writeSpinCount = 16;
    private SctpChannel socket;
    private int payloadProtocolId = 0;

    DefaultSctpChannelConfig(SctpChannel socket) {
        this.socket = socket;
    }

    @Override
    public void setOptions(Map<String, Object> options) {
        setOptions(options);
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
    public boolean setOption(String key, Object value) {
        if (key.equals("writeBufferHighWaterMark")) {
            setWriteBufferHighWaterMark0(ConversionUtil.toInt(value));
        } else if (key.equals("writeBufferLowWaterMark")) {
            setWriteBufferLowWaterMark0(ConversionUtil.toInt(value));
        } else if (key.equals("writeSpinCount")) {
            setWriteSpinCount(ConversionUtil.toInt(value));
        } else if (key.equals("receiveBufferSizePredictorFactory")) {
            setReceiveBufferSizePredictorFactory((ReceiveBufferSizePredictorFactory) value);
        } else if (key.equals("receiveBufferSizePredictor")) {
            setReceiveBufferSizePredictor((ReceiveBufferSizePredictor) value);
        } else {
            //TODO: set sctp channel options
            return false;
        }
        return true;
    }

    @Override
    public ChannelBufferFactory getBufferFactory() {
        return null;
    }

    @Override
    public void setBufferFactory(ChannelBufferFactory bufferFactory) {
    }

    @Override
    public ChannelPipelineFactory getPipelineFactory() {
        return null;
    }

    @Override
    public void setPipelineFactory(ChannelPipelineFactory pipelineFactory) {
    }

    @Override
    public int getConnectTimeoutMillis() {
        return 0;
    }

    @Override
    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
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

    @Override
    public int getWriteBufferLowWaterMark() {
        return writeBufferLowWaterMark;
    }

    @Override
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
    public ReceiveBufferSizePredictor getReceiveBufferSizePredictor() {
        ReceiveBufferSizePredictor predictor = this.predictor;
        if (predictor == null) {
            try {
                this.predictor = predictor = getReceiveBufferSizePredictorFactory().getPredictor();
            } catch (Exception e) {
                throw new ChannelException(
                        "Failed to create a new " +
                        ReceiveBufferSizePredictor.class.getSimpleName() + '.',
                        e);
            }
        }
        return predictor;
    }

    @Override
    public void setReceiveBufferSizePredictor(
            ReceiveBufferSizePredictor predictor) {
        if (predictor == null) {
            throw new NullPointerException("predictor");
        }
        this.predictor = predictor;
    }

    @Override
    public ReceiveBufferSizePredictorFactory getReceiveBufferSizePredictorFactory() {
        return predictorFactory;
    }

    @Override
    public void setReceiveBufferSizePredictorFactory(ReceiveBufferSizePredictorFactory predictorFactory) {
        if (predictorFactory == null) {
            throw new NullPointerException("predictorFactory");
        }
        this.predictorFactory = predictorFactory;
    }

    @Override
    public int getPayloadProtocol() {
        return payloadProtocolId;
    }

    @Override
    public boolean isTcpNoDelay() {
        return false;
    }

    @Override
    public void setTcpNoDelay(boolean tcpNoDelay) {
    }

    @Override
    public int getSoLinger() {
        return 0;
    }

    @Override
    public void setSoLinger(int soLinger) {
    }

    @Override
    public int getSendBufferSize() {
        return 0;
    }

    @Override
    public void setSendBufferSize(int sendBufferSize) {
    }

    @Override
    public int getReceiveBufferSize() {
        return 0;
    }

    @Override
    public void setReceiveBufferSize(int receiveBufferSize) {
    }

    @Override
    public boolean isKeepAlive() {
        return false;
    }

    @Override
    public void setKeepAlive(boolean keepAlive) {
    }

    @Override
    public int getTrafficClass() {
        return 0;
    }

    @Override
    public void setTrafficClass(int trafficClass) {
    }

    @Override
    public boolean isReuseAddress() {
        return false;
    }

    @Override
    public void setReuseAddress(boolean reuseAddress) {
    }

    @Override
    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
    }
}

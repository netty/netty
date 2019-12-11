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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * ThresholdCompressionEncoder
 * <p>
 * Let compression happen only at certain thresholds.
 */
public abstract class ThresholdCompressionEncoder extends MessageToByteEncoder<ByteBuf> {

    protected static final int NOT_LIMIT = -1;

    /**
     * min threshold for compression.
     */
    private int minThreshold;

    /**
     * max threshold for compression.
     */
    private int maxThreshold;

    public ThresholdCompressionEncoder() {
        this(true, NOT_LIMIT, NOT_LIMIT);
    }

    public ThresholdCompressionEncoder(boolean preferDirect) {
        this(preferDirect, NOT_LIMIT, NOT_LIMIT);
    }

    public ThresholdCompressionEncoder(int minThreshold, int maxThreshold) {
        this(true, minThreshold, maxThreshold);
    }

    public ThresholdCompressionEncoder(boolean preferDirect, int minThreshold, int maxThreshold) {
        super(preferDirect);
        if (minThreshold < NOT_LIMIT || maxThreshold < NOT_LIMIT) {
            throw new IllegalArgumentException("minThreshold or maxThreshold is illegal, minThreshold:" +
                    minThreshold + ", maxThreshold:" + maxThreshold);
        }
        this.minThreshold = minThreshold;
        this.maxThreshold = maxThreshold;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        int readableBytes = msg.readableBytes();
        if (this.minThreshold == NOT_LIMIT && this.maxThreshold == NOT_LIMIT) {
            // no threshold, directly encode.
            doEncode(ctx, msg, out, readableBytes);
        } else {
            if (this.minThreshold == NOT_LIMIT) {
                // maxThreshold != NOT_LIMIT, limit max threshold.
                if (readableBytes <= this.maxThreshold) {
                    doEncode(ctx, msg, out, readableBytes);
                }
            } else if (this.maxThreshold == NOT_LIMIT) {
                // minThreshold != NOT_LIMIT, limit min threshold.
                if (readableBytes >= minThreshold) {
                    doEncode(ctx, msg, out, readableBytes);
                }
            } else {
                // limit both min and max threshold.
                if (this.minThreshold <= readableBytes && readableBytes <= this.maxThreshold) {
                    doEncode(ctx, msg, out, readableBytes);
                }
            }

            // no compress, directly call writeBytes.
            noCompress(out, msg);
        }
    }

    protected abstract void doEncode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out, int readableBytes) throws Exception;

    protected void noCompress(ByteBuf in, ByteBuf out) {
        out.writeBytes(in);
    }

    public int getMinThreshold() {
        return minThreshold;
    }

    public void setMinThreshold(int minThreshold) {
        this.minThreshold = minThreshold;
    }

    public int getMaxThreshold() {
        return maxThreshold;
    }

    public void setMaxThreshold(int maxThreshold) {
        this.maxThreshold = maxThreshold;
    }
}

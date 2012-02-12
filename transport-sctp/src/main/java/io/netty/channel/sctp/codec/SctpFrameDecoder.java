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
package io.netty.channel.sctp.codec;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.sctp.SctpChannel;
import io.netty.channel.sctp.SctpFrame;
import io.netty.handler.codec.oneone.OneToOneDecoder;

/**
 * SCTP Frame Decoder which extract payload channel buffer
 * Note: Supported SCTP Frame Interleave Level - 0
 */

public class SctpFrameDecoder extends OneToOneDecoder {

    private final InboundStreamFilter inboundStreamFilter;

    private volatile ChannelBuffer cumulation;

    public SctpFrameDecoder() {
        this.inboundStreamFilter = new DefaultInboundStreamFilter();
    }

    public SctpFrameDecoder(InboundStreamFilter inboundStreamFilter) {
        this.inboundStreamFilter = inboundStreamFilter;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        if (!(msg instanceof SctpFrame)) {
            return msg;
        }
        final SctpChannel sctpChannel = (SctpChannel) channel;
        final SctpFrame sctpFrame = (SctpFrame) msg;

        if (inboundStreamFilter.filter(sctpChannel, sctpFrame)) {

            final boolean complete = sctpFrame.getMessageInfo().isComplete();
            if (complete) {
                if (cumulation == null) {
                    return sctpFrame.getPayloadBuffer();
                } else {
                    final ChannelBuffer extractedFrame = ChannelBuffers.wrappedBuffer(cumulation, sctpFrame.getPayloadBuffer());
                    cumulation = null;
                    return extractedFrame;
                }
            } else {
                if (cumulation == null) {
                    cumulation = sctpFrame.getPayloadBuffer();
                } else {
                    cumulation = ChannelBuffers.wrappedBuffer(cumulation, sctpFrame.getPayloadBuffer());
                }
                return null;
            }
        } else {
            return msg;
        }
    }
}

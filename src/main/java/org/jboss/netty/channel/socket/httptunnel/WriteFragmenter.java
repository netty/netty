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
package org.jboss.netty.channel.socket.httptunnel;


import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;

/**
 * Downstream handler which places an upper bound on the size of written
 * {@link ChannelBuffer ChannelBuffers}. If a buffer
 * is bigger than the specified upper bound, the buffer is broken up
 * into two or more smaller pieces.
 * <p>
 * This is utilised by the http tunnel to smooth out the per-byte latency,
 * by placing an upper bound on HTTP request / response body sizes.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("one")
public class WriteFragmenter extends SimpleChannelDownstreamHandler {

    public static final String NAME = "writeFragmenter";
    private int splitThreshold;

    public WriteFragmenter(int splitThreshold) {
        this.splitThreshold = splitThreshold;
    }

    public void setSplitThreshold(int splitThreshold) {
        this.splitThreshold = splitThreshold;
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        ChannelBuffer data = (ChannelBuffer)e.getMessage();

        if (data.readableBytes() <= splitThreshold) {
            super.writeRequested(ctx, e);
        } else {
            List<ChannelBuffer> fragments = WriteSplitter.split(data, splitThreshold);
            ChannelFutureAggregator aggregator = new ChannelFutureAggregator(e.getFuture());
            for(ChannelBuffer fragment : fragments) {
                ChannelFuture fragmentFuture = Channels.future(ctx.getChannel(), true);
                aggregator.addFuture(fragmentFuture);
                Channels.write(ctx, fragmentFuture, fragment);
            }
        }
    }
}

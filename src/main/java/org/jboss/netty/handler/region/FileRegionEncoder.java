/*
 * Copyright 2011 Red Hat, Inc.
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
package org.jboss.netty.handler.region;


import java.nio.channels.WritableByteChannel;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.FileRegion;
import org.jboss.netty.channel.MessageEvent;

/**
 * {@link ChannelDownstreamHandler} implementation which encodes a {@link FileRegion} to {@link ChannelBuffer}'s if one of the given {@link ChannelHandler} was found in the {@link ChannelPipeline}.
 * 
 * This {@link ChannelDownstreamHandler} should be used if you plan to write {@link FileRegion} objects and also have some {@link ChannelDownstreamHandler} in the {@link ChannelPipeline} which needs to transform
 * the to be written {@link ChannelBuffer} in any case. This could be for example {@link ChannelDownstreamHandler}'s which needs to encrypt or compress messages.
 * 
 * Users of this {@link FileRegionEncoder} should add / remove this {@link ChannelDownstreamHandler} on the fly to get the best performance out of their system.
 * 
 * 
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://www.murkycloud.com/">Norman Maurer</a>
 *
 */
@ChannelHandler.Sharable
public class FileRegionEncoder implements ChannelDownstreamHandler{

    @Override
    public void handleDownstream(
            ChannelHandlerContext ctx, ChannelEvent evt) throws Exception {
        if (!(evt instanceof MessageEvent)) {
            ctx.sendDownstream(evt);
            return;
        }

        MessageEvent e = (MessageEvent) evt;
        Object originalMessage = e.getMessage();
        if (originalMessage instanceof FileRegion) {

            FileRegion fr = (FileRegion) originalMessage;
            WritableByteChannel bchannel = new ChannelWritableByteChannel(ctx, e);

            int length = 0;
            long i = 0;
            while ((i = fr.transferTo(bchannel, length)) > 0) {
                length += i;
                if (length >= fr.getCount()) {
                    break;
                }
            }

        } else {
            // no converting is needed so just sent the event downstream
            ctx.sendDownstream(evt);
        }

    }
}

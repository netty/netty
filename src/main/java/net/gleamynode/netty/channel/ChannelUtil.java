/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.channel;

import net.gleamynode.netty.pipeline.DefaultPipeline;
import net.gleamynode.netty.pipeline.Pipe;
import net.gleamynode.netty.pipeline.Pipeline;
import net.gleamynode.netty.pipeline.PipelineFactory;

public class ChannelUtil {

    public static Pipeline<ChannelEvent> newPipeline() {
        return new DefaultPipeline<ChannelEvent>();
    }

    public static Pipeline<ChannelEvent> newPipeline(Pipeline<ChannelEvent> pipeline) {
        Pipeline<ChannelEvent> newPipeline = newPipeline();
        for (Pipe<ChannelEvent> p: pipeline) {
            newPipeline.addLast(p);
        }
        return newPipeline;
    }

    public static PipelineFactory<ChannelEvent> newPipelineFactory(
            final Pipeline<ChannelEvent> pipeline) {
        return new PipelineFactory<ChannelEvent>() {
            public Pipeline<ChannelEvent> getPipeline() {
                return newPipeline(pipeline);
            }
        };
    }

    private ChannelUtil() {
        // Unused
    }
}

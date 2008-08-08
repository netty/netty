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

import java.util.Map;

import net.gleamynode.netty.pipeline.PipelineFactory;

public interface ChannelConfig {
    void setOptions(Map<String, Object> options);
    PipelineFactory<ChannelEvent> getPipelineFactory();
    void setPipelineFactory(PipelineFactory<ChannelEvent> pipelineFactory);
    int getConnectTimeoutMillis();
    void setConnectTimeoutMillis(int connectTimeoutMillis);
    int getWriteTimeoutMillis();
    void setWriteTimeoutMillis(int writeTimeoutMillis);
}

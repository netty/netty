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


/**
 *
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 * @apiviz.composedOf net.gleamynode.netty.channel.ChannelHandlerContext
 * @apiviz.owns       net.gleamynode.netty.channel.ChannelHandler
 * @apiviz.uses       net.gleamynode.netty.channel.ChannelSink - - sends events downstream
 */
public interface ChannelPipeline {
    void addFirst (String name, ChannelHandler handler);
    void addLast  (String name, ChannelHandler handler);
    void addBefore(String baseName, String name, ChannelHandler handler);
    void addAfter (String baseName, String name, ChannelHandler handler);

    void remove(ChannelHandler handler);
    ChannelHandler remove(String name);
    <T extends ChannelHandler> T remove(Class<T> handlerType);
    ChannelHandler removeFirst();
    ChannelHandler removeLast();

    void replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler);
    ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler);
    <T extends ChannelHandler> T replace(Class<T> oldHandlerType, String newName, ChannelHandler newHandler);

    ChannelHandler getFirst();
    ChannelHandler getLast();

    ChannelHandler get(String name);
    <T extends ChannelHandler> T get(Class<T> handlerType);

    ChannelHandlerContext getContext(ChannelHandler handler);
    ChannelHandlerContext getContext(String name);
    ChannelHandlerContext getContext(Class<? extends ChannelHandler> handlerType);

    void sendUpstream(ChannelEvent e);
    void sendDownstream(ChannelEvent e);

    Channel getChannel();
    ChannelSink getSink();
    void attach(Channel channel, ChannelSink sink);

    Map<String, ChannelHandler> toMap();
}

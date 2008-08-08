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

/**
 * The core channel API which is asynchronous and event-driven abstraction of
 * various transports such as a
 * <a href="http://en.wikipedia.org/wiki/New_I/O#Channels">NIO Channel</a>.
 *
 * @apiviz.landmark
 * @apiviz.exclude ^java
 * @apiviz.exclude ^net\.gleamynode\.netty\.channel\.[^\.]+\.
 * @apiviz.exclude ^net\.gleamynode\.netty\.(bootstrap|handler)\.
 * @apiviz.exclude \.(Abstract|Default).*$
 * @apiviz.exclude \.[A-Za-z]+ChannelFuture$
 * @apiviz.exclude \.ChannelState$
 */
package net.gleamynode.netty.channel;
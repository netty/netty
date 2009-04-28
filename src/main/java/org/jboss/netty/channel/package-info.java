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

/**
 * The core channel API which is asynchronous and event-driven abstraction of
 * various transports such as a
 * <a href="http://en.wikipedia.org/wiki/New_I/O#Channels">NIO Channel</a>.
 *
 * @apiviz.landmark
 * @apiviz.exclude ^java
 * @apiviz.exclude ^org\.jboss\.netty\.channel\.[^\.]+\.
 * @apiviz.exclude ^org\.jboss\.netty\.(bootstrap|handler|util)\.
 * @apiviz.exclude \.(Abstract|Default).*$
 * @apiviz.exclude \.(Downstream|Upstream).*Event$
 * @apiviz.exclude \.[A-Za-z]+ChannelFuture$
 * @apiviz.exclude \.ChannelPipelineFactory$
 * @apiviz.exclude \.ChannelHandlerContext$
 * @apiviz.exclude \.ChannelSink$
 * @apiviz.exclude \.ChannelLocal$
 * @apiviz.exclude \.[^\.]+ReceiveBufferSizePredictor$
 */
package org.jboss.netty.channel;

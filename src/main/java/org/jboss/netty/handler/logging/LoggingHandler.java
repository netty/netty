/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.netty.handler.logging;

import static org.jboss.netty.buffer.ChannelBuffers.*;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * A {@link ChannelHandler} that logs all events in <tt>DEBUG</tt> level.  This
 * handler is supposed to be used for debugging purpose only as the amount of
 * logged events will be large.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("all")
public class LoggingHandler implements ChannelUpstreamHandler, ChannelDownstreamHandler {

    private final InternalLogger logger = InternalLoggerFactory.getInstance(getClass());
    private final boolean hexDump;

    public LoggingHandler() {
        this(true);
    }

    public LoggingHandler(boolean hexDump) {
        this.hexDump = hexDump;
    }

    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
            throws Exception {
        log(e);
        ctx.sendUpstream(e);
    }

    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e)
            throws Exception {
        log(e);
        ctx.sendDownstream(e);
    }

    protected void log(ChannelEvent e) {
        String msg = e.toString();
        if (logger.isDebugEnabled()) {
            // Append hex dump if necessary.
            if (hexDump && e instanceof MessageEvent) {
                MessageEvent me = (MessageEvent) e;
                if (me.getMessage() instanceof ChannelBuffer) {
                    ChannelBuffer buf = (ChannelBuffer) me.getMessage();
                    msg = msg + " - (HEXDUMP: " + hexDump(buf) + ')';
                }
            }

            // Log the message (and exception if available.)
            if (e instanceof ExceptionEvent) {
                logger.debug(msg, ((ExceptionEvent) e).getCause());
            } else {
                logger.debug(msg);
            }
        }
    }
}

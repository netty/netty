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
package org.jboss.netty.example.factorial;

import java.math.BigInteger;
import java.util.Formatter;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

/**
 * Handler for a server-side channel.  Please note that this handler's
 * {@link ChannelPipelineCoverage} annotation value is "one".  It means
 * this handler maintains some stateful information which is specific to
 * a certain channel.  Therefore, an instance of this handler can
 * cover only one ChannelPipeline and Channel pair.  You have to create
 * a new handler instance whenever you create a new channel and insert
 * this handler  to avoid a race condition.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("one") // <-- HERE
public class FactorialServerHandler extends SimpleChannelUpstreamHandler {

    private static final Logger logger = Logger.getLogger(
            FactorialServerHandler.class.getName());

    // Stateful properties.
    private int lastMultiplier = 1;
    private BigInteger factorial = new BigInteger(new byte[] { 1 });

    @Override
    public void handleUpstream(
            ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent) {
            logger.info(e.toString());
        }
        super.handleUpstream(ctx, e);
    }

    @Override
    public void messageReceived(
            ChannelHandlerContext ctx, MessageEvent e) {

        // Calculate the cumulative factorial and send it to the client.
        BigInteger number;
        if (e.getMessage() instanceof BigInteger) {
            number = (BigInteger) e.getMessage();
        } else {
            number = new BigInteger(e.getMessage().toString());
        }
        lastMultiplier = number.intValue();
        factorial = factorial.multiply(number);
        e.getChannel().write(factorial);
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx,
            ChannelStateEvent e) throws Exception {
        logger.info(new Formatter().format(
                "Factorial of %,d is: %,d", lastMultiplier, factorial).toString());
    }

    @Override
    public void exceptionCaught(
            ChannelHandlerContext ctx, ExceptionEvent e) {
        logger.log(
                Level.WARNING,
                "Unexpected exception from downstream.",
                e.getCause());
        e.getChannel().close();
    }
}

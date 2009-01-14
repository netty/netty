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
package org.jboss.netty.example.localtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Formatter;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.example.localtime.LocalTimeProtocol.Continent;
import org.jboss.netty.example.localtime.LocalTimeProtocol.LocalTime;
import org.jboss.netty.example.localtime.LocalTimeProtocol.LocalTimes;
import org.jboss.netty.example.localtime.LocalTimeProtocol.Location;
import org.jboss.netty.example.localtime.LocalTimeProtocol.Locations;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("one")
public class LocalTimeClientHandler extends SimpleChannelHandler {

    private static final Logger logger = Logger.getLogger(
            LocalTimeClientHandler.class.getName());

    // Stateful properties
    private volatile Channel channel;
    private final BlockingQueue<LocalTimes> answer = new LinkedBlockingQueue<LocalTimes>();

    public List<String> getLocalTimes(Collection<String> cities) {
        Locations.Builder builder = Locations.newBuilder();

        for (String c: cities) {
            String[] components = c.split("/");
            builder.addLocation(Location.newBuilder().
                setContinent(Continent.valueOf(components[0].toUpperCase())).
                setCity(components[1]).build());
        }

        channel.write(builder.build());

        LocalTimes localTimes;
        for (;;) {
            try {
                localTimes = answer.take();
                break;
            } catch (InterruptedException e) {
                // Ignore.
            }
        }

        List<String> result = new ArrayList<String>();
        for (LocalTime lt: localTimes.getLocalTimeList()) {
            result.add(
                    new Formatter().format(
                            "%4d-%02d-%02d %02d:%02d:%02d %s",
                            lt.getYear(),
                            lt.getMonth(),
                            lt.getDayOfMonth(),
                            lt.getHour(),
                            lt.getMinute(),
                            lt.getSecond(),
                            lt.getDayOfWeek().name()).toString());
        }

        return result;
    }

    @Override
    public void handleUpstream(
            ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent) {
            logger.info(e.toString());
        }
        super.handleUpstream(ctx, e);
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        channel = e.getChannel();
        super.channelOpen(ctx, e);
    }

    @Override
    public void messageReceived(
            ChannelHandlerContext ctx, final MessageEvent e) {
        answer.offer((LocalTimes) e.getMessage());
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

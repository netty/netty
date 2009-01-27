/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
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
package org.jboss.netty.servlet;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import static org.jboss.netty.channel.Channels.pipeline;
import org.jboss.netty.channel.local.LocalAddress;
import static org.jboss.netty.servlet.NettyServletContextListener.BOOTSTRAP_PROP;
import static org.jboss.netty.servlet.NettyServletContextListener.STREAMING_PROP;
import static org.jboss.netty.servlet.NettyServletContextListener.RECONNECT_PROP;
import static org.jboss.netty.servlet.NettyServlet.CHANNEL_PROP;
import static org.jboss.netty.servlet.NettyServlet.HANDLER_PROP;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

/**
 * A session listenor that uses the client bootstrap to create a channel.
 * 
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class NettySessionListener implements HttpSessionListener, ChannelHandler {
    
    public void sessionCreated(HttpSessionEvent event) {
        HttpSession session = event.getSession();
        ClientBootstrap bootstrap = (ClientBootstrap) session.getServletContext().getAttribute(BOOTSTRAP_PROP);
        Boolean streaming = (Boolean) session.getServletContext().getAttribute(STREAMING_PROP);
        if(streaming) {
            session.setMaxInactiveInterval(-1);
        }
        final ServletChannelHandler handler = new ServletChannelHandler(streaming, session,  (Long) session.getServletContext().getAttribute(RECONNECT_PROP));
        session.setAttribute(HANDLER_PROP, handler);
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = pipeline();
                pipeline.addLast(NettySessionListener.class.getName(), handler);
                return pipeline;
            }
        });
        ChannelFuture future = bootstrap.connect(new LocalAddress("netty"));
        future.awaitUninterruptibly();
        final Channel ch = future.getChannel();
        session.setAttribute(CHANNEL_PROP, ch);
    }

    public void sessionDestroyed(HttpSessionEvent event) {
        Channel channel = (Channel) event.getSession().getAttribute(CHANNEL_PROP);
        if (channel != null) {
            channel.close();
        }
    }
}

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

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class NettySessionListener implements HttpSessionListener, ChannelHandler {
    
    public void sessionCreated(HttpSessionEvent event) {
        HttpSession session = event.getSession();
        System.out.println("NettySessionListener.sessionCreated");
        ClientBootstrap bootstrap = (ClientBootstrap) session.getServletContext().getAttribute("bootstrap");
        System.out.println("created session  = " + session.getId());

        session.setMaxInactiveInterval(Integer.MAX_VALUE);
        final ServletChannelHandler handler = new ServletChannelHandler(true);
        session.setAttribute("handler", handler);
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
        session.setAttribute("channel", ch);
    }

    public void sessionDestroyed(HttpSessionEvent event) {
        System.out.println("JBMSessionListener.sessionDestroyed");
        Channel channel = (Channel) event.getSession().getAttribute("channel");
        if (channel != null) {
            channel.close();
        }
    }
}

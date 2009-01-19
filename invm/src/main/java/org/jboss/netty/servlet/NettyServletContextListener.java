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
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.local.LocalServerChannels;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class NettyServletContextListener implements ServletContextListener {

    public void contextInitialized(ServletContextEvent context) {

        String channelName = context.getServletContext().getInitParameter("serverChannelName");

        if (channelName != null) {

            String name = channelName.trim();
            ChannelFactory channelFactory = LocalServerChannels.getClientChannelFactory(name);
            if (channelFactory != null) {

                context.getServletContext().setAttribute("bootstrap", new ClientBootstrap(channelFactory));
            }
            else {
                //todo warning
            }


        }
    }

    public void contextDestroyed(ServletContextEvent context) {
    }

}

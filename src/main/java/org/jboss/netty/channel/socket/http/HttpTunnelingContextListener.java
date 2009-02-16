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
package org.jboss.netty.channel.socket.http;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.local.LocalClientChannelFactory;

/**
 * A context listener that creates a client bootstrap that uses a local channel factory. The local channel factory should
 * already be registered before the contect is loaded.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @version $Rev$, $Date$
 */
public class HttpTunnelingContextListener implements ServletContextListener {

    private static final long DEFAULT_RECONNECT_TIMEOUT = 5000;

    private static final boolean DEFAULT_IS_STREAMING = true;

    static final String SERVER_CHANNEL_PROP = "serverChannelName";

    static final String RECONNECT_PROP = "reconnectTimeout";

    static final String STREAMING_PROP = "streaming";

    static final String BOOTSTRAP_PROP = "bootstrap";

    private final ChannelFactory factory = new LocalClientChannelFactory();

    public void contextInitialized(ServletContextEvent context) {
        context.getServletContext().setAttribute(BOOTSTRAP_PROP, new ClientBootstrap(factory));
        String timeoutParam =  context.getServletContext().getInitParameter(RECONNECT_PROP);
        context.getServletContext().setAttribute(RECONNECT_PROP, timeoutParam == null?DEFAULT_RECONNECT_TIMEOUT:Long.decode(timeoutParam.trim()));
        String streaming = context.getServletContext().getInitParameter(STREAMING_PROP);
        context.getServletContext().setAttribute(STREAMING_PROP, streaming == null?DEFAULT_IS_STREAMING: Boolean.valueOf(streaming.trim()));
        String serverChannel = context.getServletContext().getInitParameter(SERVER_CHANNEL_PROP);
        context.getServletContext().setAttribute(SERVER_CHANNEL_PROP, serverChannel);
    }

    public void contextDestroyed(ServletContextEvent context) {
        // Unused
    }

}

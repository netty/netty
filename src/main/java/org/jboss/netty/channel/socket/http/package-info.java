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
 * An HTTP-based client-side {@link org.jboss.netty.channel.socket.SocketChannel}
 * and its corresponding server-side Servlet implementation that make your
 * existing server application work in a firewalled network.
 *
 * <h3>Deploying the HTTP tunnel as a Servlet</h3>
 *
 * First, {@link org.jboss.netty.channel.socket.http.HttpTunnelingServlet} must be
 * configured in a <tt>web.xml</tt>.
 *
 * <pre>
 * &lt;?xml version="1.0" encoding="UTF-8"?&gt;
 * &lt;web-app xmlns="http://java.sun.com/xml/ns/j2ee"
 *             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 *             xsi:schemaLocation="http://java.sun.com/xml/ns/j2ee http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd"
 *             version="2.4"&gt;
 *
 *   &lt;servlet&gt;
 *     &lt;servlet-name&gt;NettyTunnelingServlet&lt;/servlet-name&gt;
 *     &lt;servlet-class&gt;<b>org.jboss.netty.channel.socket.http.HttpTunnelingServlet</b>&lt;/servlet-class&gt;
 *     &lt;!--
 *       The name of the channel, this should be a registered local channel.
 *       See LocalTransportRegister.
 *     --&gt;
 *     &lt;init-param&gt;
 *       &lt;param-name&gt;<b>endpoint</b>&lt;/param-name&gt;
 *       &lt;param-value&gt;<b>local:myLocalServer</b>&lt;/param-value&gt;
 *     &lt;/init-param&gt;
 *     &lt;load-on-startup&gt;<b>1</b>&lt;/load-on-startup&gt;
 *   &lt;/servlet&gt;
 *
 *   &lt;servlet-mapping&gt;
 *     &lt;servlet-name&gt;NettyTunnelingServlet&lt;/servlet-name&gt;
 *     &lt;url-pattern&gt;<b>/netty-tunnel</b>&lt;/url-pattern&gt;
 *   &lt;/servlet-mapping&gt;
 * &lt;/web-app&gt;
 * </pre>
 *
 * Second, you have to bind your Netty-based server application in the same
 * Servlet context or shared class loader space using the local transport
 * (see {@link org.jboss.netty.channel.local.LocalServerChannelFactory}.)
 * You can use your favorite IoC framework such as JBoss Microcontainer, Guice,
 * and Spring to do this.  The following example shows how to bind an echo
 * erver to the endpoint specifed above (<tt>web.xml</tt>) in JBossAS 5:
 *
 * <pre>
 * &lt;bean name="my-local-echo-server"
 *       class="org.jboss.netty.example.http.tunnel.LocalEchoServerRegistration" /&gt;
 *
 * ...
 *
 * package org.jboss.netty.example.http.tunnel;
 * ...
 *
 * public class LocalEchoServerRegistration {
 *
 *     private final ChannelFactory factory = new DefaultLocalServerChannelFactory();
 *     private volatile Channel serverChannel;
 *
 *     public void start() {
 *         ServerBootstrap serverBootstrap = new ServerBootstrap(factory);
 *         EchoHandler handler = new EchoHandler();
 *         serverBootstrap.getPipeline().addLast("handler", handler);
 *
 *         // Note that "myLocalServer" is the endpoint which was specified in web.xml.
 *         serverChannel = serverBootstrap.bind(new LocalAddress("<b>myLocalServer</b>"));
 *     }
 *
 *     public void stop() {
 *         serverChannel.close();
 *     }
 * }
 * </pre>
 *
 * <h3>Connecting to the HTTP tunnel</h3>
 *
 * Once the tunnel has been configured, your client-side application needs only
 * a couple lines of changes.
 *
 * <pre>
 * ClientBootstrap b = new ClientBootstrap(
 *         <b>new HttpTunnelingClientSocketChannelFactory(new NioClientSocketChannelFactory(...))</b>);
 *
 * // Configure the pipeline (or pipeline factory) here.
 * ...
 *
 * // The host name of the HTTP server.
 * b.setOption(<b>"serverName"</b>, "example.com");
 * // The path to the HTTP tunneling Servlet (set to <b>/netty-tunnel</b> in web.xml)
 * b.setOption(<b>"serverPath"</b>, "contextPath<b>/netty-tunnel</b>");
 * b.connect(new InetSocketAddress("example.com", 80);
 * </pre>
 *
 * For more configuration parameters such as HTTPS options,
 * refer to {@link org.jboss.netty.channel.socket.http.HttpTunnelingSocketChannelConfig}.
 */
package org.jboss.netty.channel.socket.http;


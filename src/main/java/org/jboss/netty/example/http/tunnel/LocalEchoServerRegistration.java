/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.example.http.tunnel;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.local.DefaultLocalServerChannelFactory;
import org.jboss.netty.channel.local.LocalAddress;
import org.jboss.netty.example.echo.EchoServerHandler;

/**
 * Deploy this in JBossAS 5 or other IoC container by adding the following bean.
 *
 * <pre>
 * &lt;bean name="org.jboss.netty.example.http.tunnel.LocalEchoServerRegistration"
 *       class="org.jboss.netty.example.http.tunnel.LocalEchoServerRegistration" /&gt;
 * </pre>
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class LocalEchoServerRegistration {

    private final ChannelFactory factory = new DefaultLocalServerChannelFactory();
    private volatile Channel serverChannel;

    public void start() {
        ServerBootstrap serverBootstrap = new ServerBootstrap(factory);
        EchoServerHandler handler = new EchoServerHandler();
        serverBootstrap.getPipeline().addLast("handler", handler);

        // Note that "myLocalServer" is the endpoint which was specified in web.xml.
        serverChannel = serverBootstrap.bind(new LocalAddress("myLocalServer"));
    }

    public void stop() {
        serverChannel.close();
    }
}

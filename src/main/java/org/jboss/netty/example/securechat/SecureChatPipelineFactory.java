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
package org.jboss.netty.example.securechat;

import static org.jboss.netty.channel.Channels.*;

import javax.net.ssl.SSLEngine;

import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.jboss.netty.handler.ssl.SslHandler;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class SecureChatPipelineFactory implements
        ChannelPipelineFactory {

    private final ChannelHandler handler;

    public SecureChatPipelineFactory(ChannelHandler handler) {
        this.handler = handler;
    }

    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = pipeline();

        // Add SSL handler first to encrypt and decrypt everything.
        // You will need something more complicated to identify both
        // and server in the real world.
        SSLEngine engine;
        if (handler instanceof SecureChatClientHandler) {
            engine = SecureChatSslContextFactory.getClientContext().createSSLEngine();
            engine.setUseClientMode(true);
        } else {
            engine = SecureChatSslContextFactory.getServerContext().createSSLEngine();
            engine.setUseClientMode(false);
        }

        pipeline.addLast("ssl", new SslHandler(engine));

        // On top of the SSL handler, add the text line codec.
        pipeline.addLast("framer", new DelimiterBasedFrameDecoder(
                8192, Delimiters.lineDelimiter()));
        pipeline.addLast("decoder", new StringDecoder());
        pipeline.addLast("encoder", new StringEncoder());

        // and then business logic.
        pipeline.addLast("handler", handler);

        return pipeline;
    }
}

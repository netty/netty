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
package org.jboss.netty.channel.socket.servlet;

import java.net.URL;
import java.util.concurrent.Executor;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.util.ExecutorUtil;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class ServletClientSocketChannelFactory implements ClientSocketChannelFactory {

    private final Executor workerExecutor;
    private final ChannelSink sink;
    private final URL url;

    /**
     *
     * @param workerExecutor
     */
    public ServletClientSocketChannelFactory(Executor workerExecutor, URL url) {
        this(url, workerExecutor, Runtime.getRuntime().availableProcessors());
    }

    /**
     * Creates a new instance.
     *
     *        the {@link java.util.concurrent.Executor} which will execute the boss thread
     * @param url
     * @param workerExecutor
     *        the {@link java.util.concurrent.Executor} which will execute the I/O worker threads
     * @param workerCount
     */
    public ServletClientSocketChannelFactory(
          URL url, Executor workerExecutor,
          int workerCount) {
        if (url == null) {
            throw new NullPointerException("Url is null");
        }
        this.url = url;
        if (workerExecutor == null) {
            throw new NullPointerException("workerExecutor");
        }
        if (workerCount <= 0) {
            throw new IllegalArgumentException(
                    "workerCount (" + workerCount + ") " +
                    "must be a positive integer.");
        }

        this.workerExecutor = workerExecutor;
        sink = new ServletClientSocketPipelineSink(workerExecutor);
    }

    public SocketChannel newChannel(ChannelPipeline pipeline) {
        return new ServletClientSocketChannel(this, pipeline, sink, url);
    }

    public void releaseExternalResources() {
        ExecutorUtil.terminate(workerExecutor);
    }
}
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
package org.jboss.netty.channel.socket.sctp;

import com.sun.nio.sctp.SctpStandardSocketOption;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.DefaultServerChannelConfig;
import org.jboss.netty.util.internal.ConversionUtil;

import java.io.IOException;
import java.net.SocketAddress;

/**
 * The default {@link org.jboss.netty.channel.socket.ServerSocketChannelConfig} implementation.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author <a href="http://github.com/jestan">Jestan Nirojan</a>
 *
 * @version $Rev$, $Date$
 */
public class SctpServerChannelConfig extends DefaultServerChannelConfig
        implements ServerSctpChannelConfig {

    private final com.sun.nio.sctp.SctpServerChannel serverChannel;
    private volatile int backlog;

    /**
     * Creates a new instance.
     */
    public SctpServerChannelConfig(com.sun.nio.sctp.SctpServerChannel serverChannel) {
        if (serverChannel == null) {
            throw new NullPointerException("serverChannel");
        }
        this.serverChannel = serverChannel;
    }

    @Override
    public boolean setOption(String key, Object value) {
        if (super.setOption(key, value)) {
            return true;
        }

        if (key.equals("sctpInitMaxStreams")) {
            setInitMaxStreams((SctpStandardSocketOption.InitMaxStreams) value);
        } else if (key.equals("backlog")) {
            setBacklog(ConversionUtil.toInt(value));
        } else {
            return false;
        }
        return true;
    }

    @Override
    public SctpStandardSocketOption.InitMaxStreams getInitMaxStreams() {
        try {
            return serverChannel.getOption(SctpStandardSocketOption.SCTP_INIT_MAXSTREAMS);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void setInitMaxStreams(SctpStandardSocketOption.InitMaxStreams initMaxStreams) {
        try {
            serverChannel.setOption(SctpStandardSocketOption.SCTP_INIT_MAXSTREAMS, initMaxStreams);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public int getBacklog() {
        return backlog;
    }

    @Override
    public void setBacklog(int backlog) {
        if (backlog < 0) {
            throw new IllegalArgumentException("backlog: " + backlog);
        }
        this.backlog = backlog;
    }
}

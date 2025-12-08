/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.kqueue;

import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.util.NetUtil;

import java.io.IOException;
import java.util.Map;

import static io.netty.channel.ChannelOption.SO_BACKLOG;
import static io.netty.channel.ChannelOption.SO_RCVBUF;
import static io.netty.channel.ChannelOption.SO_REUSEADDR;
import static io.netty.channel.ChannelOption.TCP_FASTOPEN;
import static io.netty.channel.kqueue.KQueueChannelOption.SO_ACCEPTFILTER;
import static io.netty.channel.unix.UnixChannelOption.SO_REUSEPORT;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

final class KQueueServerSocketChannelConfig extends KQueueChannelConfig {
    private volatile int backlog = NetUtil.SOMAXCONN;
    private volatile boolean enableTcpFastOpen;

    KQueueServerSocketChannelConfig(KQueueServerSocketChannel channel) {
        super(channel);

        // Use SO_REUSEADDR by default as java.nio does the same.
        //
        // See https://github.com/netty/netty/issues/2605
        setReuseAddress(true);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(), SO_RCVBUF, SO_REUSEADDR, SO_BACKLOG, TCP_FASTOPEN,
                SO_REUSEPORT, SO_ACCEPTFILTER);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == SO_RCVBUF) {
            return (T) Integer.valueOf(getReceiveBufferSize());
        }
        if (option == SO_REUSEADDR) {
            return (T) Boolean.valueOf(isReuseAddress());
        }
        if (option == SO_BACKLOG) {
            return (T) Integer.valueOf(getBacklog());
        }
        if (option == TCP_FASTOPEN) {
            return (T) (isTcpFastOpen() ? Integer.valueOf(1) : Integer.valueOf(0));
        }
        if (option == SO_REUSEPORT) {
            return (T) Boolean.valueOf(isReusePort());
        }
        if (option == SO_ACCEPTFILTER) {
            return (T) getAcceptFilter();
        }
        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        validate(option, value);

        if (option == SO_RCVBUF) {
            setReceiveBufferSize((Integer) value);
        } else if (option == SO_REUSEADDR) {
            setReuseAddress((Boolean) value);
        } else if (option == SO_BACKLOG) {
            setBacklog((Integer) value);
        } else if (option == TCP_FASTOPEN) {
            setTcpFastOpen((Integer) value > 0);
        } else if (option == SO_REUSEPORT) {
            setReusePort((Boolean) value);
        } else if (option == SO_ACCEPTFILTER) {
            setAcceptFilter((AcceptFilter) value);
        } else {
            return super.setOption(option, value);
        }

        return true;
    }

    private void setReusePort(boolean reusePort) {
        try {
            ((AbstractKQueueChannel) channel).socket.setReusePort(reusePort);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isReusePort() {
        try {
            return ((AbstractKQueueChannel) channel).socket.isReusePort();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setAcceptFilter(AcceptFilter acceptFilter) {
        try {
            ((AbstractKQueueChannel) channel).socket.setAcceptFilter(acceptFilter);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private AcceptFilter getAcceptFilter() {
        try {
            return ((AbstractKQueueChannel) channel).socket.getAcceptFilter();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isReuseAddress() {
        try {
            return ((AbstractKQueueChannel) channel).socket.isReuseAddress();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setReuseAddress(boolean reuseAddress) {
        try {
            ((AbstractKQueueChannel) channel).socket.setReuseAddress(reuseAddress);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getReceiveBufferSize() {
        try {
            return ((AbstractKQueueChannel) channel).socket.getReceiveBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setReceiveBufferSize(int receiveBufferSize) {
        try {
            ((AbstractKQueueChannel) channel).socket.setReceiveBufferSize(receiveBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    int getBacklog() {
        return backlog;
    }

    private void setBacklog(int backlog) {
        checkPositiveOrZero(backlog, "backlog");
        this.backlog = backlog;
    }

    /**
     * Returns {@code true} if TCP FastOpen is enabled.
     *
     * @see <a href="https://tools.ietf.org/html/rfc7413#appendix-A.2">RFC 7413 Passive Open</a>
     */
    boolean isTcpFastOpen() {
        return enableTcpFastOpen;
    }

    /**
     * Enables TCP FastOpen on the server channel. If the underlying os doesn't support TCP_FASTOPEN setting this has no
     * effect. This has to be set before doing listen on the socket otherwise this takes no effect.
     *
     * @param enableTcpFastOpen {@code true} if TCP FastOpen should be enabled for incoming connections.
     *
     * @see <a href="https://tools.ietf.org/html/rfc7413#appendix-A.2">RFC 7413 Passive Open</a>
     */
    private void setTcpFastOpen(boolean enableTcpFastOpen) {
        this.enableTcpFastOpen = enableTcpFastOpen;
    }
}

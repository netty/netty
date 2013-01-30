/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.udt;

import com.barchart.udt.SocketUDT;
import com.barchart.udt.nio.ChannelUDT;
import io.netty.channel.ChannelOption;

import java.io.IOException;
import java.util.Map;

import static io.netty.channel.udt.UdtChannelOption.*;

/**
 * The default {@link UdtServerChannelConfig} implementation.
 */
public class DefaultUdtServerChannelConfig extends DefaultUdtChannelConfig implements
        UdtServerChannelConfig {

    private volatile int backlog = 64;

    public DefaultUdtServerChannelConfig(final UdtChannel channel,
            final ChannelUDT channelUDT, final boolean apply)
            throws IOException {
        super(channel, channelUDT, apply);
        if (apply) {
            apply(channelUDT);
        }
    }

    protected void apply(final ChannelUDT channelUDT) throws IOException {
        final SocketUDT socketUDT = channelUDT.socketUDT();
        // nothing to apply for now.
    }

    @Override
    public int getBacklog() {
        return backlog;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(final ChannelOption<T> option) {
        if (option == SO_BACKLOG) {
            return (T) Integer.valueOf(getBacklog());
        }
        return super.getOption(option);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(), SO_BACKLOG);
    }

    @Override
    public UdtServerChannelConfig setBacklog(final int backlog) {
        this.backlog = backlog;
        return this;
    }

    @Override
    public <T> boolean setOption(final ChannelOption<T> option, final T value) {
        validate(option, value);
        if (option == SO_BACKLOG) {
            setBacklog((Integer) value);
        } else {
            return super.setOption(option, value);
        }
        return true;
    }

}

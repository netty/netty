/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.rxtx;

import io.netty.channel.ChannelOption;
import io.netty.channel.rxtx.RxtxChannelConfig.Databits;
import io.netty.channel.rxtx.RxtxChannelConfig.Paritybit;
import io.netty.channel.rxtx.RxtxChannelConfig.Stopbits;

/**
 * Option for configuring a serial port connection
 */
public final class RxtxChannelOption<T> extends ChannelOption<T> {
    public static final RxtxChannelOption<Integer> BAUD_RATE =
            new RxtxChannelOption<Integer>("BAUD_RATE");

    public static final RxtxChannelOption<Boolean> DTR =
            new RxtxChannelOption<Boolean>("DTR");

    public static final RxtxChannelOption<Boolean> RTS =
            new RxtxChannelOption<Boolean>("RTS");

    public static final RxtxChannelOption<Stopbits> STOP_BITS =
            new RxtxChannelOption<Stopbits>("STOP_BITS");

    public static final RxtxChannelOption<Databits> DATA_BITS =
            new RxtxChannelOption<Databits>("DATA_BITS");

    public static final RxtxChannelOption<Paritybit> PARITY_BIT =
            new RxtxChannelOption<Paritybit>("PARITY_BIT");

    public static final RxtxChannelOption<Integer> WAIT_TIME =
            new RxtxChannelOption<Integer>("WAIT_TIME");

    public static final RxtxChannelOption<Integer> READ_TIMEOUT =
            new RxtxChannelOption<Integer>("READ_TIMEOUT");

    @SuppressWarnings("deprecation")
    private RxtxChannelOption(String name) {
        super(name);
    }
}

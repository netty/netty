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

    public static final ChannelOption<Integer> BAUD_RATE = valueOf(RxtxChannelOption.class, "BAUD_RATE");
    public static final ChannelOption<Boolean> DTR = valueOf(RxtxChannelOption.class, "DTR");
    public static final ChannelOption<Boolean> RTS = valueOf(RxtxChannelOption.class, "RTS");
    public static final ChannelOption<Stopbits> STOP_BITS = valueOf(RxtxChannelOption.class, "STOP_BITS");
    public static final ChannelOption<Databits> DATA_BITS = valueOf(RxtxChannelOption.class, "DATA_BITS");
    public static final ChannelOption<Paritybit> PARITY_BIT = valueOf(RxtxChannelOption.class, "PARITY_BIT");
    public static final ChannelOption<Integer> WAIT_TIME = valueOf(RxtxChannelOption.class, "WAIT_TIME");
    public static final ChannelOption<Integer> READ_TIMEOUT = valueOf(RxtxChannelOption.class, "READ_TIMEOUT");

    @SuppressWarnings({ "unused", "deprecation" })
    private RxtxChannelOption() {
        super(null);
    }
}

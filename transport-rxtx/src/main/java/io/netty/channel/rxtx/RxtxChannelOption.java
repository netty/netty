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

    @SuppressWarnings("rawtypes")
    private static final Class<RxtxChannelOption> T = RxtxChannelOption.class;

    public static final ChannelOption<Integer> BAUD_RATE = valueOf(T, "BAUD_RATE");
    public static final ChannelOption<Boolean> DTR = valueOf(T, "DTR");
    public static final ChannelOption<Boolean> RTS = valueOf(T, "RTS");
    public static final ChannelOption<Stopbits> STOP_BITS = valueOf(T, "STOP_BITS");
    public static final ChannelOption<Databits> DATA_BITS = valueOf(T, "DATA_BITS");
    public static final ChannelOption<Paritybit> PARITY_BIT = valueOf(T, "PARITY_BIT");
    public static final ChannelOption<Integer> WAIT_TIME = valueOf(T, "WAIT_TIME");
    public static final ChannelOption<Integer> READ_TIMEOUT = valueOf(T, "READ_TIMEOUT");

    @SuppressWarnings({ "unused", "deprecation" })
    private RxtxChannelOption() {
        super(null);
    }
}

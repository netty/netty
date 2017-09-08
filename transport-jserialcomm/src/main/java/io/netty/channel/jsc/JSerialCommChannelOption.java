/*
 * Copyright 2017 The Netty Project
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
package io.netty.channel.jsc;

import io.netty.channel.ChannelOption;
import io.netty.channel.jsc.JSerialCommChannelConfig.Paritybit;
import io.netty.channel.jsc.JSerialCommChannelConfig.Stopbits;

/**
 * Option for configuring a serial port connection
 */
public final class JSerialCommChannelOption<T> extends ChannelOption<T> {

    public static final ChannelOption<Integer> BAUD_RATE = valueOf("BAUD_RATE");
    public static final ChannelOption<Stopbits> STOP_BITS = valueOf("STOP_BITS");
    public static final ChannelOption<Integer> DATA_BITS = valueOf("DATA_BITS");
    public static final ChannelOption<Paritybit> PARITY_BIT = valueOf("PARITY_BIT");
    public static final ChannelOption<Integer> WAIT_TIME = valueOf("WAIT_TIME");
    public static final ChannelOption<Integer> READ_TIMEOUT = valueOf("READ_TIMEOUT");

    @SuppressWarnings({ "unused", "deprecation" })
    private JSerialCommChannelOption() {
        super(null);
    }
}

/*
 * Copyright 2022 The Netty Project
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
package io.netty.channel.socket;

import io.netty.channel.ChannelConfig;

/**
 * A {@link ChannelConfig} for a {@link TunChannel}.
 *
 * <h3>Available options</h3>
 * <p>
 * In addition to the options provided by {@link ChannelConfig}, {@link TunChannelConfig} allows the
 * following options in the option map:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th><th>Associated setter method</th>
 * </tr><tr>
 * <td>{@link TunChannelOption#TUN_MTU}</td><td>{@link #setMtu(int)}</td>
 * </tr>
 * </table>
 */
public interface TunChannelConfig extends ChannelConfig {
    /**
     * Gets the {@link TunChannelOption#TUN_MTU} option.
     */
    int getMtu();

    /**
     * Sets the {@link TunChannelOption#TUN_MTU} option.
     */
    TunChannelConfig setMtu(int mtu);
}

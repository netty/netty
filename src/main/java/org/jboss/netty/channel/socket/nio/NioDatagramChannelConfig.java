/*
 * Copyright 2011 The Netty Project
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
package org.jboss.netty.channel.socket.nio;

import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.channel.socket.DatagramChannelConfig;

/**
 * A {@link DatagramChannelConfig} for a NIO TCP/IP {@link DatagramChannel}.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link ChannelConfig} and
 * {@link DatagramChannelConfig}, {@link NioDatagramChannelConfig} allows the
 * following options in the option map:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th><th>Associated setter method</th>
 * </tr><tr>
 * <td>{@code "writeBufferHighWaterMark"}</td><td>{@link #setWriteBufferHighWaterMark(int)}</td>
 * </tr><tr>
 * <td>{@code "writeBufferLowWaterMark"}</td><td>{@link #setWriteBufferLowWaterMark(int)}</td>
 * </tr><tr>
 * <td>{@code "writeSpinCount"}</td><td>{@link #setWriteSpinCount(int)}</td>
 * </tr><tr>
 * </table>
 */
public interface NioDatagramChannelConfig extends DatagramChannelConfig, NioChannelConfig {


}

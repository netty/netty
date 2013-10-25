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

import com.barchart.udt.OptionUDT;
import io.netty.channel.ChannelOption;

import static io.netty.channel.ChannelOption.*;

/**
 * Options for the UDT transport
 */
public final class UdtChannelOption {

    private static final Class<UdtChannelOption> T = UdtChannelOption.class;

    /**
     * See {@link OptionUDT#Protocol_Receive_Buffer_Size}.
     */
    public static final ChannelOption<Integer> PROTOCOL_RECEIVE_BUFFER_SIZE =
            valueOf(T, "PROTOCOL_RECEIVE_BUFFER_SIZE");

    /**
     * See {@link OptionUDT#Protocol_Send_Buffer_Size}.
     */
    public static final ChannelOption<Integer> PROTOCOL_SEND_BUFFER_SIZE = valueOf(T, "PROTOCOL_SEND_BUFFER_SIZE");

    /**
     * See {@link OptionUDT#System_Receive_Buffer_Size}.
     */
    public static final ChannelOption<Integer> SYSTEM_RECEIVE_BUFFER_SIZE = valueOf(T, "SYSTEM_RECEIVE_BUFFER_SIZE");

    /**
     * See {@link OptionUDT#System_Send_Buffer_Size}.
     */
    public static final ChannelOption<Integer> SYSTEM_SEND_BUFFER_SIZE = valueOf(T, "SYSTEM_SEND_BUFFER_SIZE");

    private UdtChannelOption() { }
}

/*
 * Copyright 2025 The Netty Project
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
package io.netty.channel;

import io.netty.util.ReferenceCounted;
import io.netty.util.internal.StringUtil;

import java.util.Objects;

/**
 * A shutdown type for a {@link Channel}.
 */
public final class ChannelShutdownType {

    private static final ChannelShutdownType EMPTY_INBOUND = new ChannelShutdownType(
            ChannelShutdownDirection.Inbound, null);
    private static final ChannelShutdownType EMPTY_OUTBOUND = new ChannelShutdownType(
            ChannelShutdownDirection.Outbound, null);

    private final ChannelShutdownDirection direction;
    private final Object data;

    private ChannelShutdownType(ChannelShutdownDirection direction, Object data) {
        this.direction = Objects.requireNonNull(direction, "direction");
        this.data = data;
    }

    /**
     * Returns the {@link ChannelShutdownDirection} of the shutdown.
     *
     * @return  direction
     */
    public ChannelShutdownDirection direction() {
        return direction;
    }

    /**
     * Returns the optional data that will be used or was used for the shutdown or {@code null} if none will be used
     * or was used.
     *
     * @return  data
     */
    public Object data() {
        return data;
    }

    @Override
    public String toString() {
        return "ChannelShutdownType{" +
                "direction=" + direction +
                ", data=" + data +
                '}';
    }

    /**
     * Returns a {@link ChannelShutdownType} for {@link ChannelShutdownDirection#Inbound} with no data.
     *
     * @return type
     */
    public static ChannelShutdownType newInbound() {
        return newInbound(null);
    }

    /**
     * Returns a {@link ChannelShutdownType} for {@link ChannelShutdownDirection#Inbound} with the given data.
     *
     * @param data      the data that is used or {@code null}.
     * @return type
     * @throws IllegalArgumentException if the data is of type {@link ReferenceCounted}.
     */
    public static ChannelShutdownType newInbound(Object data) {
        verifyData(data);
        if (data == null) {
            return EMPTY_INBOUND;
        }
        return new ChannelShutdownType(ChannelShutdownDirection.Inbound, data);
    }

    /**
     * Returns a {@link ChannelShutdownType} for {@link ChannelShutdownDirection#Outbound} with no data.
     *
     * @return type
     */
    public static ChannelShutdownType newOutbound() {
        return newOutbound(null);
    }

    /**
     * Returns a {@link ChannelShutdownType} for {@link ChannelShutdownDirection#Outbound} with the given data.
     *
     * @param data      the data that is used or {@code null}.
     * @return type
     * @throws IllegalArgumentException if the data is of type {@link ReferenceCounted}.
     */
    public static ChannelShutdownType newOutbound(Object data) {
        verifyData(data);
        if (data == null) {
            return EMPTY_OUTBOUND;
        }
        return new ChannelShutdownType(ChannelShutdownDirection.Outbound, data);
    }

    private static void verifyData(Object data) {
        if (data instanceof ReferenceCounted) {
            throw new IllegalArgumentException("ReferenceCounted data is not allowed: "
                    + StringUtil.simpleClassName(data));
        }
    }
}

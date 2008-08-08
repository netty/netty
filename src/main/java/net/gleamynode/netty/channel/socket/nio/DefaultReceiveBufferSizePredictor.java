/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.channel.socket.nio;

public class DefaultReceiveBufferSizePredictor implements
        ReceiveBufferSizePredictor {
    private static final int DEFAULT_MINIMUM = 256;
    private static final int DEFAULT_INITIAL = 1024;
    private static final int DEFAULT_MAXIMUM = 1048576;

    private final int minimum;
    private final int maximum;
    private int nextReceiveBufferSize = 1024;
    private boolean shouldHalveNow;

    public DefaultReceiveBufferSizePredictor() {
        this(DEFAULT_MINIMUM, DEFAULT_INITIAL, DEFAULT_MAXIMUM);
    }

    public DefaultReceiveBufferSizePredictor(int minimum, int initial, int maximum) {
        if (minimum <= 0) {
            throw new IllegalArgumentException("minimum: " + minimum);
        }
        if (initial < minimum) {
            throw new IllegalArgumentException("initial: " + initial);
        }
        if (maximum < initial) {
            throw new IllegalArgumentException("maximum: " + maximum);
        }
        this.minimum = minimum;
        nextReceiveBufferSize = initial;
        this.maximum = maximum;
    }

    public int nextReceiveBufferSize() {
        return nextReceiveBufferSize;
    }

    public void previousReceiveBufferSize(int previousReceiveBufferSize) {
        if (previousReceiveBufferSize < nextReceiveBufferSize >>> 1) {
            if (shouldHalveNow) {
                nextReceiveBufferSize = Math.max(minimum, nextReceiveBufferSize >>> 1);
                shouldHalveNow = false;
            } else {
                shouldHalveNow = true;
            }
        } else if (previousReceiveBufferSize == nextReceiveBufferSize) {
            nextReceiveBufferSize = Math.min(maximum, nextReceiveBufferSize << 1);
            shouldHalveNow = false;
        }
    }
}

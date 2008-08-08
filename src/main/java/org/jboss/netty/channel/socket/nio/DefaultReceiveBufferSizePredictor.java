/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.channel.socket.nio;

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

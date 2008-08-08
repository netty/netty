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
    private static final int MINIMUM = 256;
    private static final int MAXIMUM = 1048576 * 16;  // Can you reach here?

    private int nextReceiveBufferSize = 1024;
    private boolean shouldHalveNow;

    public int nextReceiveBufferSize() {
        return nextReceiveBufferSize;
    }

    public void previousReceiveBufferSize(int previousReceiveBufferSize) {
        if (nextReceiveBufferSize != MINIMUM &&
            previousReceiveBufferSize < nextReceiveBufferSize >>> 1) {
            if (shouldHalveNow) {
                nextReceiveBufferSize >>>= 1;
                shouldHalveNow = false;
            } else {
                shouldHalveNow = true;
            }
        } else if (nextReceiveBufferSize != MAXIMUM &&
                   previousReceiveBufferSize == nextReceiveBufferSize) {
            nextReceiveBufferSize <<=1;
            shouldHalveNow = false;
        }
    }
}

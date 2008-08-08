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
package net.gleamynode.netty.example.discard;

public class ThroughputMonitor extends Thread {

    private final DiscardHandler echoHandler;

    public ThroughputMonitor(DiscardHandler echoHandler) {
        this.echoHandler = echoHandler;
    }

    @Override
    public void run() {
        long oldCounter = echoHandler.getTransferredBytes();
        long startTime = System.currentTimeMillis();
        for (;;) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            long endTime = System.currentTimeMillis();
            long newCounter = echoHandler.getTransferredBytes();
            System.err.format(
                    "%4.3f MiB/s%n",
                    (newCounter - oldCounter) * 1000 / (endTime - startTime) /
                    1048576.0);
            oldCounter = newCounter;
            startTime = endTime;
        }
    }
}

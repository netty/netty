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
package org.jboss.netty.example.echo;

public class ThroughputMonitor extends Thread {

    private final EchoHandler echoHandler;

    public ThroughputMonitor(EchoHandler echoHandler) {
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

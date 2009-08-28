/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.example.discard;

/**
 * Measures and prints the current throughput every 3 seconds.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
public class ThroughputMonitor extends Thread {

    private final DiscardServerHandler handler;

    public ThroughputMonitor(DiscardServerHandler handler) {
        this.handler = handler;
    }

    @Override
    public void run() {
        long oldCounter = handler.getTransferredBytes();
        long startTime = System.currentTimeMillis();
        for (;;) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            long endTime = System.currentTimeMillis();
            long newCounter = handler.getTransferredBytes();
            System.err.format(
                    "%4.3f MiB/s%n",
                    (newCounter - oldCounter) * 1000.0 / (endTime - startTime) /
                    1048576.0);
            oldCounter = newCounter;
            startTime = endTime;
        }
    }
}

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
package org.jboss.netty.example.objectecho;

/**
 * Measures and prints the current throughput every 3 seconds.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
public class ThroughputMonitor extends Thread {

    private final ObjectEchoHandler handler;

    public ThroughputMonitor(ObjectEchoHandler handler) {
        this.handler = handler;
    }

    @Override
    public void run() {
        long oldCounter = handler.getTransferredMessages();
        long startTime = System.currentTimeMillis();
        for (;;) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            long endTime = System.currentTimeMillis();
            long newCounter = handler.getTransferredMessages();
            System.err.format(
                    "%4.3f msgs/s%n",
                    (newCounter - oldCounter) * 1000 / (double) (endTime - startTime));
            oldCounter = newCounter;
            startTime = endTime;
        }
    }
}

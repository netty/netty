/*
 * Copyright 2012 The Netty Project
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
package io.netty.util.concurrent;

import static org.junit.Assert.*;

import org.easymock.EasyMock;
import org.junit.Test;

import java.util.concurrent.Executors;

public class ImmediateEventExecutorTest {

    @Test
    public void shouldExecuteImmediately() {
        TaskScheduler scheduler = new TaskScheduler(Executors.defaultThreadFactory());
        ImmediateEventExecutor e = new ImmediateEventExecutor(scheduler);
        long startTime = System.nanoTime();
        e.execute(new Runnable() {
            @Override
            public void run() {
                long startTime = System.nanoTime();
                for (;;) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                    if (System.nanoTime() - startTime >= 1000000000L) {
                        break;
                    }
                }
            }
        });
        assertTrue(System.nanoTime() - startTime >= 1000000000L);
        scheduler.shutdown();
    }
}

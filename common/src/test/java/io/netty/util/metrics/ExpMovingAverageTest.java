/*
 * Copyright 2014 The Netty Project
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

package io.netty.util.metrics;

import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ExpMovingAverageTest {
    @Test
    @Ignore
    public void test1() throws Exception {
        final ExpMovingAverage avg = new ExpMovingAverage(0.3, 5);

        final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);
        executor.scheduleAtFixedRate(new Runnable() {
            private int state;

            @Override
            public void run() {
                switch (state) {
                    case 0:
                        avg.update(1000);
                        break;
                    case 1:
                        avg.update(1050);
                        break;
                    case 2:
                        avg.update(950);
                        break;
                    case 3:
                        avg.update(1000);
                        break;
                    case 4:
                        avg.update(1100);
                        break;
                    case 5:
                        avg.update(3000);
                        break;
                    case 6:
                        avg.update(10000);
                        break;
                    case 7:
                        avg.update(15000);
                        break;
                    case 8:
                        avg.update(25000);
                        break;
                    case 9:
                        avg.update(45000);
                        break;
                    case 10:
                        avg.update(1000);
                        break;
                    case 11:
                        avg.update(1100);
                        break;
                    case 12:
                        avg.update(1200);
                        break;
                    case 13:
                        executor.shutdown();
                        break;
                }

                System.out.println("state " + state + ": " + avg.value());

                state++;
                avg.tick();
            }
        }, 0, 5, TimeUnit.SECONDS).get();
    }
}

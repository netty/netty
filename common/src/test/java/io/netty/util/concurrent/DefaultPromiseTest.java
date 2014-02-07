/*
 * Copyright 2013 The Netty Project
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

import org.junit.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

@SuppressWarnings("unchecked")
public class DefaultPromiseTest {

    @Test
    public void testNoStackOverflowErrorWithImmediateEventExecutorA() throws Exception {
        final Promise<Void>[] p = new DefaultPromise[128];
        for (int i = 0; i < p.length; i ++) {
            final int finalI = i;
            p[i] = new DefaultPromise<Void>(ImmediateEventExecutor.INSTANCE);
            p[i].addListener(new FutureListener<Void>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    if (finalI + 1 < p.length) {
                        p[finalI + 1].setSuccess(null);
                    }
                }
            });
        }

        p[0].setSuccess(null);

        for (Promise<Void> a: p) {
            assertThat(a.isSuccess(), is(true));
        }
    }

    @Test
    public void testNoStackOverflowErrorWithImmediateEventExecutorB() throws Exception {
        final Promise<Void>[] p = new DefaultPromise[128];
        for (int i = 0; i < p.length; i ++) {
            final int finalI = i;
            p[i] = new DefaultPromise<Void>(ImmediateEventExecutor.INSTANCE);
            p[i].addListener(new FutureListener<Void>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    DefaultPromise.notifyListener(ImmediateEventExecutor.INSTANCE, future, new FutureListener<Void>() {
                        @Override
                        public void operationComplete(Future<Void> future) throws Exception {
                            if (finalI + 1 < p.length) {
                                p[finalI + 1].setSuccess(null);
                            }
                        }
                    });
                }
            });
        }

        p[0].setSuccess(null);

        for (Promise<Void> a: p) {
            assertThat(a.isSuccess(), is(true));
        }
    }

    @Test
    public void testListenerNotifyOrder() throws Exception {
        SingleThreadEventExecutor executor =
                new SingleThreadEventExecutor(null, Executors.defaultThreadFactory(), true) {
                    @Override
                    protected void run() {
                        for (;;) {
                            Runnable task = takeTask();
                            if (task != null) {
                                task.run();
                                updateLastExecutionTime();
                            }

                            if (confirmShutdown()) {
                                break;
                            }
                        }
                    }
                };

        final BlockingQueue<FutureListener<Void>> listeners = new LinkedBlockingQueue<FutureListener<Void>>();
        int runs = 20000;

        for (int i = 0; i < runs; i++) {
            final Promise<Void> promise = new DefaultPromise<Void>(executor);
            FutureListener<Void> listener1 = new FutureListener<Void>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    listeners.add(this);
                }
            };
            FutureListener<Void> listener2 = new FutureListener<Void>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    listeners.add(this);
                }
            };
            FutureListener<Void> listener3 = new FutureListener<Void>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    listeners.add(this);
                }
            };
            GlobalEventExecutor.INSTANCE.execute(new Runnable() {
                @Override
                public void run() {
                    promise.setSuccess(null);
                }
            });
            promise.addListener(listener1).addListener(listener2).addListener(listener3);

            assertSame("Fail during run " + i + " / " + runs, listener1, listeners.take());
            assertSame("Fail during run " + i + " / " + runs, listener2, listeners.take());
            assertSame("Fail during run " + i + " / " + runs, listener3, listeners.take());
            assertTrue("Fail during run " + i + " / " + runs, listeners.isEmpty());
        }
        executor.shutdownGracefully().sync();
    }
}

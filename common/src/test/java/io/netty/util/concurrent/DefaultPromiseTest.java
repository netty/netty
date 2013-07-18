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
}

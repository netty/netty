/*
 * Copyright 2018 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.util.concurrent;

import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCounted;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class EventExecutorTest {
    @Test
    public void notifyAfterShutdown() throws Exception {
        EventExecutor executor = new DefaultEventExecutor();
        try {
            ReferenceCounted data = new AbstractReferenceCounted() {
                @Override
                protected void deallocate() {
                    // noop
                }

                @Override
                public ReferenceCounted touch(Object hint) {
                    return this;
                }
            };
            assertEquals(1, data.refCnt());

            Promise<ReferenceCounted> promise = executor.newPromise();
            executor.shutdownGracefully(0L, 0L, TimeUnit.MILLISECONDS).sync();

            assertFalse(promise.trySuccess(data));
            assertEquals(1, data.refCnt());
            data.release();
        } finally {
            executor.shutdownGracefully();
        }
    }
}

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
package io.netty.channel.nio;


import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.LockSupport;


public class NioSelectNowEventLoop extends NioEventLoop {

    private int backoffCounter;

    public NioSelectNowEventLoop(NioEventLoopGroup parent, ThreadFactory threadFactory,
        SelectorProvider selectorProvider) {
        super(parent, threadFactory, selectorProvider);
    }

    @Override
    protected void wakeup(boolean inEventLoop) {
        // we never need to wakeup.
    }

    @Override
    protected boolean doConditionalSelect(boolean oldWakenUp) throws Exception {
        if (selectNow() == 0 && !hasTasks()) {
            backoffCounter = backoff(backoffCounter);
            return true;
        } else {
            backoffCounter = 0;
            return false;
        }
    }

    private static int backoff(final int backoffCounter) {
        if (backoffCounter > 3000) {
            LockSupport.parkNanos(100000);
        } else if (backoffCounter > 2000) {
            Thread.yield();
        } else if (backoffCounter > 1000) {
            LockSupport.parkNanos(1);
        }
        return backoffCounter + 1;
    }

}

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
package org.jboss.netty.channel.socket.nio;

import static org.easymock.EasyMock.*;


import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;


import org.jboss.netty.channel.ChannelFuture;
import org.junit.Assert;
import org.junit.Test;

public abstract class AbstractNioWorkerTest {

    @Test
    public void testShutdownWorkerThrowsException() throws InterruptedException {
        AbstractNioChannel<?> mockChannel = createMockChannel();
        replay(mockChannel);
        
        ChannelFuture mockFuture = createMock(ChannelFuture.class);
        replay(mockFuture);

        
        ExecutorService executor = Executors.newCachedThreadPool();
        AbstractNioWorker worker = createWorker(executor);
        worker.shutdown();

        // give the Selector time to detect the shutdown
        Thread.sleep(SelectorUtil.DEFAULT_SELECT_TIMEOUT * 10);
        try {
            worker.register(mockChannel, mockFuture);
            Assert.fail();
        } catch (RejectedExecutionException e) {
            // expected
        }
        verify(mockChannel, mockFuture);
        reset(mockChannel, mockFuture);
    }

    protected abstract AbstractNioWorker createWorker(Executor executor);

    protected abstract AbstractNioChannel<?> createMockChannel();
}

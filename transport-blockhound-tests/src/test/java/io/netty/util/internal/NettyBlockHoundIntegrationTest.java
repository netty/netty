/*
 * Copyright 2019 The Netty Project

 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:

 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.internal;

import io.netty.util.concurrent.GlobalEventExecutor;
import org.junit.BeforeClass;
import org.junit.Test;
import reactor.blockhound.BlockHound;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class NettyBlockHoundIntegrationTest {

    @BeforeClass
    public static void setUpClass() {
        BlockHound.install();
    }

    @Test
    public void testBlockingCallsInNettyThreads() throws Exception {
        final FutureTask<Void> future = new FutureTask<>(() -> {
            Thread.sleep(0);
            return null;
        });
        GlobalEventExecutor.INSTANCE.execute(future);

        try {
            future.get(5, TimeUnit.SECONDS);
            fail("Expected an exception due to a blocking call but none was thrown");
        } catch (ExecutionException e) {
            Throwable throwable = e.getCause();
            assertNotNull("An exception was thrown", throwable);
            assertTrue("Blocking call was reported", throwable.getMessage().contains("Blocking call"));
        }
    }
}

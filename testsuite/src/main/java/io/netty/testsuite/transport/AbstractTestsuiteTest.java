/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.testsuite.transport;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.testsuite.util.TestUtils;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.TestInfo;

import java.util.List;

public abstract class AbstractTestsuiteTest<T extends AbstractBootstrap<?, ?>> {
    protected final InternalLogger logger = InternalLoggerFactory.getInstance(getClass());
    protected volatile T cb;

    protected abstract List<TestsuitePermutation.BootstrapFactory<T>> newFactories();

    protected List<ByteBufAllocator> newAllocators() {
        return TestsuitePermutation.allocator();
    }

    protected void run(TestInfo testInfo, Runner<T> runner) throws Throwable {
        List<TestsuitePermutation.BootstrapFactory<T>> combos = newFactories();
        String methodName = TestUtils.testMethodName(testInfo);
        for (ByteBufAllocator allocator: newAllocators()) {
            int i = 0;
            for (TestsuitePermutation.BootstrapFactory<T> e: combos) {
                cb = e.newInstance();
                configure(cb, allocator);
                logger.info(String.format(
                        "Running: %s %d of %d with %s",
                        methodName, ++ i, combos.size(), StringUtil.simpleClassName(allocator)));
                runner.run(cb);
            }
        }
    }

    protected abstract void configure(T bootstrap, ByteBufAllocator allocator);

    public interface Runner<CB extends AbstractBootstrap<?, ?>> {
        void run(CB cb) throws Throwable;
    }
}

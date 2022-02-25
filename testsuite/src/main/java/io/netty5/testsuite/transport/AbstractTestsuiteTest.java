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
package io.netty5.testsuite.transport;

import io.netty5.bootstrap.AbstractBootstrap;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.testsuite.transport.TestsuitePermutation.AllocatorConfig;
import io.netty5.testsuite.util.TestUtils;
import io.netty5.util.internal.StringUtil;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.TestInfo;

import java.util.List;

public abstract class AbstractTestsuiteTest<T extends AbstractBootstrap<?, ?, ?>> {
    protected final InternalLogger logger = InternalLoggerFactory.getInstance(getClass());
    protected volatile T cb;

    protected abstract List<TestsuitePermutation.BootstrapFactory<T>> newFactories();

    protected List<AllocatorConfig> newAllocators() {
        return TestsuitePermutation.allocator();
    }

    protected void run(TestInfo testInfo, Runner<T> runner) throws Throwable {
        List<TestsuitePermutation.BootstrapFactory<T>> combos = newFactories();
        String methodName = TestUtils.testMethodName(testInfo);
        for (AllocatorConfig config: newAllocators()) {
            int i = 0;
            for (TestsuitePermutation.BootstrapFactory<T> e: combos) {
                cb = e.newInstance();
                configure(cb, config.byteBufAllocator, config.bufferAllocator);
                logger.info(String.format(
                        "Running: %s %d of %d with %s",
                        methodName, ++ i, combos.size(), StringUtil.simpleClassName(config.byteBufAllocator)));
                runner.run(cb);
            }
        }
    }

    protected abstract void configure(T bootstrap, ByteBufAllocator byteBufAllocator, BufferAllocator bufferAllocator);

    public interface Runner<CB extends AbstractBootstrap<?, ?, ?>> {
        void run(CB cb) throws Throwable;
    }
}

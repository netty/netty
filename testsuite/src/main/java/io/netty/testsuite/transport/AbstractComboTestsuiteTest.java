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

public abstract class AbstractComboTestsuiteTest<SB extends AbstractBootstrap<?, ?>,
        CB extends AbstractBootstrap<?, ?>> {
    protected final InternalLogger logger = InternalLoggerFactory.getInstance(getClass());
    protected volatile CB cb;
    protected volatile SB sb;

    protected abstract List<TestsuitePermutation.BootstrapComboFactory<SB, CB>> newFactories();

    protected List<ByteBufAllocator> newAllocators() {
        return TestsuitePermutation.allocator();
    }

    protected void run(TestInfo testInfo, Runner<SB, CB> runner) throws Throwable {
        List<TestsuitePermutation.BootstrapComboFactory<SB, CB>> combos = newFactories();
        String methodName = TestUtils.testMethodName(testInfo);
        for (ByteBufAllocator allocator: newAllocators()) {
            int i = 0;
            for (TestsuitePermutation.BootstrapComboFactory<SB, CB> e: combos) {
                sb = e.newServerInstance();
                cb = e.newClientInstance();
                configure(sb, cb, allocator);
                logger.info(String.format(
                        "Running: %s %d of %d (%s + %s) with %s",
                        methodName, ++ i, combos.size(), sb, cb, StringUtil.simpleClassName(allocator)));
                runner.run(sb, cb);
            }
        }
    }

    protected abstract void configure(SB sb, CB cb, ByteBufAllocator allocator);

    public interface Runner<SB extends AbstractBootstrap<?, ?>, CB extends AbstractBootstrap<?, ?>> {
        void run(SB sb, CB cb) throws Throwable;
    }
}

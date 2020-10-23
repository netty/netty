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
import org.junit.Rule;
import org.junit.rules.TestName;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public abstract class AbstractTestsuiteTest<T extends AbstractBootstrap<?, ?>> {
    private final Class<T> clazz;
    protected final InternalLogger logger = InternalLoggerFactory.getInstance(getClass());
    protected volatile T cb;

    protected AbstractTestsuiteTest(Class<T> clazz) {
        this.clazz = clazz;
    }

    protected abstract List<TestsuitePermutation.BootstrapFactory<T>> newFactories();

    protected List<ByteBufAllocator> newAllocators() {
        return TestsuitePermutation.allocator();
    }

    @Rule
    public final TestName testName = new TestName();

    protected void run() throws Throwable {
        List<TestsuitePermutation.BootstrapFactory<T>> combos = newFactories();
        for (ByteBufAllocator allocator: newAllocators()) {
            int i = 0;
            for (TestsuitePermutation.BootstrapFactory<T> e: combos) {
                cb = e.newInstance();
                configure(cb, allocator);
                logger.info(String.format(
                        "Running: %s %d of %d with %s",
                        testName.getMethodName(), ++ i, combos.size(), StringUtil.simpleClassName(allocator)));
                try {
                    Method m = getClass().getMethod(
                            TestUtils.testMethodName(testName), clazz);
                    m.invoke(this, cb);
                } catch (InvocationTargetException ex) {
                    throw ex.getCause();
                }
            }
        }
    }

    protected abstract void configure(T bootstrap, ByteBufAllocator allocator);
}

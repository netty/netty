/*
 * Copyright 2014 The Netty Project
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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public abstract class AbstractComboTestsuiteTest<T extends AbstractBootstrap<?, ?>,
        V extends AbstractBootstrap<?, ?>> {
    private final Class<T> sbClazz;
    private final Class<V> cbClazz;
    protected final InternalLogger logger = InternalLoggerFactory.getInstance(getClass());
    protected volatile V cb;
    protected volatile T sb;

    protected AbstractComboTestsuiteTest(Class<T> sbClazz, Class<V> cbClazz) {
        this.sbClazz = sbClazz;
        this.cbClazz = cbClazz;
    }

    protected abstract List<SocketTestPermutation.BootstrapComboFactory<T, V>> newFactories();

    protected List<ByteBufAllocator> newAllocators() {
        return SocketTestPermutation.allocator();
    }

    @Rule
    public final TestName testName = new TestName();

    protected void run() throws Throwable {
        List<SocketTestPermutation.BootstrapComboFactory<T, V>> combos = newFactories();
        for (ByteBufAllocator allocator: newAllocators()) {
            int i = 0;
            for (SocketTestPermutation.BootstrapComboFactory<T, V> e: combos) {
                sb = e.newServerInstance();
                cb = e.newClientInstance();
                configure(sb, cb, allocator);
                logger.info(String.format(
                        "Running: %s %d of %d (%s + %s) with %s",
                        testName.getMethodName(), ++ i, combos.size(), sb, cb, StringUtil.simpleClassName(allocator)));
                try {
                    Method m = getClass().getMethod(
                            testName.getMethodName(), sbClazz, cbClazz);
                    m.invoke(this, sb, cb);
                } catch (InvocationTargetException ex) {
                    throw ex.getCause();
                }
            }
        }
    }

    protected abstract void configure(T bootstrap, V bootstrap2, ByteBufAllocator allocator);
}

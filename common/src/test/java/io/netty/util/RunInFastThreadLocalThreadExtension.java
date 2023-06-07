/*
 * Copyright 2023 The Netty Project
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
package io.netty.util;

import io.netty.util.concurrent.FastThreadLocalThread;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Annotate your test class with {@code @ExtendWith(RunInFastThreadLocalThreadExtension.class)} to have all test methods
 * run in a {@link io.netty.util.concurrent.FastThreadLocalThread}.
 * <p>
 * This extension implementation is modified from the JUnit 5
 * <a href="https://junit.org/junit5/docs/current/user-guide/#extensions-intercepting-invocations">
 * intercepting invocations</a> example.
 */
public class RunInFastThreadLocalThreadExtension implements InvocationInterceptor {
    @Override
    public void interceptTestMethod(
            final Invocation<Void> invocation,
            final ReflectiveInvocationContext<Method> invocationContext,
            final ExtensionContext extensionContext) throws Throwable {
        final AtomicReference<Throwable> throwable = new AtomicReference<Throwable>();
        Thread thread = new FastThreadLocalThread(new Runnable() {
            @Override
            public void run() {
                try {
                    invocation.proceed();
                } catch (Throwable t) {
                    throwable.set(t);
                }
            }
        });
        thread.start();
        thread.join();
        Throwable t = throwable.get();
        if (t != null) {
            throw t;
        }
    }
}

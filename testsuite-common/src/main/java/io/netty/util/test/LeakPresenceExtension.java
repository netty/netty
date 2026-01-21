/*
 * Copyright 2025 The Netty Project
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

package io.netty.util.test;

import io.netty.util.LeakPresenceDetector;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.concurrent.TimeUnit;

/**
 * Junit 5 extension for leak detection using {@link LeakPresenceDetector}.
 * <p>
 * Leak presence is checked at the class level. Any resource must be closed at the end of the test class, by the time
 * {@link org.junit.jupiter.api.AfterAll} has been called. Method-level detection is not possible because some tests
 * retain resources between methods on the same class, notably parameterized tests that allocate different buffers
 * before running the test methods with those buffers.
 * <p>
 * This extension supports parallel test execution, but has to make some assumptions about the thread lifecycle. The
 * resource scope for the class is created in {@link org.junit.jupiter.api.BeforeAll}, and then saved in a thread local
 * on each {@link org.junit.jupiter.api.BeforeEach}. This appears to work well with junit's default parallelism,
 * despite the use of a fork-join pool that can transfer tasks between threads, but it may lead to problems if tests
 * make use of fork-join machinery themselves.
 * <p>
 * The ThreadLocal holding the scope is {@link InheritableThreadLocal inheritable}, so that e.g. event loops created
 * in a test are assigned to the test resource scope.
 */
public final class LeakPresenceExtension
        implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback, AfterAllCallback {

    private static final Object SCOPE_KEY = new Object();
    private static final Object PREVIOUS_SCOPE_KEY = new Object();

    static {
        System.setProperty("io.netty.customResourceLeakDetector", WithTransferableScope.class.getName());
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        ExtensionContext.Store store = context.getStore(ExtensionContext.Namespace.GLOBAL);
        if (store.get(SCOPE_KEY) != null) {
            throw new IllegalStateException("Weird context lifecycle");
        }
        LeakPresenceDetector.ResourceScope scope = new LeakPresenceDetector.ResourceScope(context.getDisplayName());
        store.put(SCOPE_KEY, scope);

        WithTransferableScope.SCOPE.set(scope);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        LeakPresenceDetector.ResourceScope outerScope;
        ExtensionContext outerContext = context;
        while (true) {
            outerScope = (LeakPresenceDetector.ResourceScope)
                    outerContext.getStore(ExtensionContext.Namespace.GLOBAL).get(SCOPE_KEY);
            if (outerScope != null) {
                break;
            }
            outerContext = outerContext.getParent()
                    .orElseThrow(() -> new IllegalStateException("No resource scope found"));
        }

        LeakPresenceDetector.ResourceScope previousScope = WithTransferableScope.SCOPE.get();
        WithTransferableScope.SCOPE.set(outerScope);
        if (previousScope != null) {
            context.getStore(ExtensionContext.Namespace.GLOBAL).put(PREVIOUS_SCOPE_KEY, previousScope);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        LeakPresenceDetector.ResourceScope previousScope = (LeakPresenceDetector.ResourceScope)
                context.getStore(ExtensionContext.Namespace.GLOBAL).get(PREVIOUS_SCOPE_KEY);
        if (previousScope != null) {
            WithTransferableScope.SCOPE.set(previousScope);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws InterruptedException {
        LeakPresenceDetector.ResourceScope scope =
                (LeakPresenceDetector.ResourceScope) context.getStore(ExtensionContext.Namespace.GLOBAL).get(SCOPE_KEY);

        // Wait some time for resources to close. Many tests do loop.shutdownGracefully without waiting, and that's ok.
        long start = System.nanoTime();
        while (scope.hasOpenResources() && System.nanoTime() - start < TimeUnit.SECONDS.toNanos(5)) {
            TimeUnit.MILLISECONDS.sleep(100);
        }

        scope.close();
    }

    public static final class WithTransferableScope<T> extends LeakPresenceDetector<T> {
        static final InheritableThreadLocal<ResourceScope> SCOPE = new InheritableThreadLocal<>();

        @SuppressWarnings("unused")
        public WithTransferableScope(Class<?> resourceType, int samplingInterval) {
            super(resourceType);
        }

        @SuppressWarnings("unused")
        public WithTransferableScope(Class<?> resourceType, int samplingInterval, long maxActive) {
            super(resourceType);
        }

        @Override
        protected ResourceScope currentScope() throws AllocationProhibitedException {
            ResourceScope scope = SCOPE.get();
            if (scope == null) {
                throw new AllocationProhibitedException("Resource created outside test?");
            }
            return scope;
        }
    }
}

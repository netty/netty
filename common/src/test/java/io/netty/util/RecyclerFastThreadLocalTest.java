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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(RunInFastThreadLocalThreadExtension.class)
public class RecyclerFastThreadLocalTest extends RecyclerTest {
    @NotNull
    @Override
    protected Thread newThread(Runnable runnable) {
        return new FastThreadLocalThread(runnable);
    }

    @Override
    @ParameterizedTest
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    @MethodSource("ownerTypeAndUnguarded")
    public void testThreadCanBeCollectedEvenIfHandledObjectIsReferenced(OwnerType ownerType, boolean unguarded)
            throws Exception {
        final AtomicBoolean collected = new AtomicBoolean();
        final AtomicReference<HandledObject> reference = new AtomicReference<HandledObject>();
        Thread thread = new FastThreadLocalThread(new Runnable() {
            @Override
            public void run() {
                final Recycler<HandledObject> recycler = newRecycler(ownerType, unguarded, 1024);
                HandledObject object = recycler.get();
                // Store a reference to the HandledObject to ensure it is not collected when the run method finish.
                reference.set(object);
                Recycler.unpinOwner(recycler);
            }
        }) {
            @Override
            protected void finalize() throws Throwable {
                super.finalize();
                collected.set(true);
            }
        };
        assertFalse(collected.get());
        thread.start();
        thread.join();

        // Null out so it can be collected.
        thread = null;

        // Loop until the Thread was collected. If we can not collect it the Test will fail due of a timeout.
        while (!collected.get()) {
            System.gc();
            System.runFinalization();
            Thread.sleep(50);
        }

        // Now call recycle after the Thread was collected to ensure this still works...
        if (reference.get() != null) {
            reference.getAndSet(null).recycle();
        }
    }
}

/*
 * Copyright 2021 The Netty Project
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
package io.netty5.buffer.api.internal;

import io.netty5.buffer.api.Resource;
import io.netty5.buffer.api.Send;

import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.function.Supplier;

import static io.netty5.buffer.api.internal.Statics.findVarHandle;
import static java.lang.invoke.MethodHandles.lookup;

public class SendFromSupplier<T extends Resource<T>> implements Send<T> {
    private static final VarHandle GATE = findVarHandle(lookup(), SendFromSupplier.class, "gate", boolean.class);
    private final Class<T> concreteObjectType;
    private final Supplier<? extends T> supplier;

    @SuppressWarnings("unused") // Accessed via VarHandle GATE.
    private volatile boolean gate;

    public SendFromSupplier(Class<T> concreteObjectType, Supplier<? extends T> supplier) {
        this.concreteObjectType = Objects.requireNonNull(concreteObjectType, "Concrete type cannot be null.");
        this.supplier = Objects.requireNonNull(supplier, "Supplier cannot be null.");
    }

    @Override
    public T receive() {
        if (passGate()) {
            throw new IllegalStateException("This object has already been received.");
        }
        return supplier.get();
    }

    @Override
    public boolean referentIsInstanceOf(Class<?> cls) {
        return cls.isAssignableFrom(concreteObjectType);
    }

    @Override
    public void close() {
        if (!passGate()) {
            supplier.get().close();
        }
    }

    /**
     * Atomically check and pass through the gate.
     *
     * @return {@code true} if the gate has already been passed,
     * otherwise {@code false} if we got through the gate first.
     */
    private boolean passGate() {
        return (boolean) GATE.getAndSet(this, true);
    }
}

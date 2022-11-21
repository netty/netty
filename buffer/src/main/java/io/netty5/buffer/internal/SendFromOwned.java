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
package io.netty5.buffer.internal;

import io.netty5.buffer.Drop;
import io.netty5.buffer.Owned;
import io.netty5.util.Resource;
import io.netty5.util.Send;

import java.lang.invoke.VarHandle;

import static io.netty5.buffer.internal.InternalBufferUtils.findVarHandle;
import static java.lang.invoke.MethodHandles.lookup;

public class SendFromOwned<I extends Resource<I>, T extends ResourceSupport<I, T>> implements Send<I> {
    private static final VarHandle RECEIVED = findVarHandle(lookup(), SendFromOwned.class, "received", int.class);
    private static final int STATE_RECEIVED = 1;
    private static final int STATE_CLOSED = 2;

    private final Owned<T> outgoing;
    private final Drop<T> drop;
    private final Class<?> concreteType;
    @SuppressWarnings("unused")
    private volatile int received; // Accessed via VarHandle. State 0 is "unreceived"
    private T copy;

    public SendFromOwned(Owned<T> outgoing, Drop<T> drop, Class<?> concreteType) {
        this.outgoing = outgoing;
        this.drop = drop;
        this.concreteType = concreteType;
    }

    @SuppressWarnings("unchecked")
    @Override
    public I receive() {
        gateReception();
        copy = outgoing.transferOwnership(drop);
        drop.attach(copy);
        return (I) copy;
    }

    private void gateReception() {
        int state = (int) RECEIVED.compareAndExchange(this, 0, STATE_RECEIVED);
        if (state != 0) {
            IllegalStateException exception = new IllegalStateException(
                    state == STATE_RECEIVED? "This object has already been received." : "This Send has been closed.");
            T obj = copy;
            if (obj != null) {
                obj.attachTrace(exception);
            }
            throw exception;
        }
    }

    @Override
    public boolean referentIsInstanceOf(Class<?> cls) {
        return cls.isAssignableFrom(concreteType);
    }

    @Override
    public void close() {
        int state = (int) RECEIVED.compareAndExchange(this, 0, STATE_CLOSED);
        if (state == 0) {
            copy = outgoing.transferOwnership(drop);
            drop.attach(copy);
            copy.close();
        }
    }
}

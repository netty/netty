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

import io.netty5.buffer.api.Drop;
import io.netty5.buffer.api.Owned;
import io.netty5.buffer.api.Resource;
import io.netty5.buffer.api.Send;
import io.netty5.util.internal.UnstableApi;

import java.util.Objects;

/**
 * Internal support class for resources.
 *
 * @param <I> The public interface for the resource.
 * @param <T> The concrete implementation of the resource.
 */
@UnstableApi
public abstract class ResourceSupport<I extends Resource<I>, T extends ResourceSupport<I, T>> implements Resource<I> {
    private int acquires; // Closed if negative.
    private Drop<T> drop;
    private final LifecycleTracer tracer;

    protected ResourceSupport(Drop<T> drop) {
        this.drop = drop;
        tracer = LifecycleTracer.get();
    }

    /**
     * Encapsulation bypass for calling {@link #acquire()} on the given object.
     * <p>
     * Note: this {@code acquire} method does not check the type of the return value from acquire at compile time.
     * The type is instead checked at runtime, and will cause a {@link ClassCastException} to be thrown if done
     * incorrectly.
     *
     * @param obj The object we wish to acquire (increment reference count) on.
     * @param <T> The type of the acquired object, given by target-typing.
     * @return The acquired object.
     */
    @SuppressWarnings("unchecked")
    static <T> T acquire(ResourceSupport<?, ?> obj) {
        return (T) obj.acquire();
    }

    /**
     * Encapsulation bypass for getting the {@link LifecycleTracer} attached to the given object.
     *
     * @param obj The object to get the {@link LifecycleTracer} from.
     * @return The {@link LifecycleTracer} that is attached to the given object.
     */
    static LifecycleTracer getTracer(ResourceSupport<?, ?> obj) {
        return obj.tracer;
    }

    /**
     * Increment the reference count.
     * <p>
     * Note, this method is not thread-safe because Resources are meant to thread-confined.
     *
     * @return This {@link Resource} instance.
     */
    protected final I acquire() {
        if (acquires < 0) {
            throw attachTrace(createResourceClosedException());
        }
        if (acquires == Integer.MAX_VALUE) {
            throw new IllegalStateException("Reached maximum allowed acquires (" + Integer.MAX_VALUE + ").");
        }
        acquires++;
        tracer.acquire(acquires);
        return self();
    }

    protected abstract RuntimeException createResourceClosedException();

    /**
     * Decrement the reference count, and dispose of the resource if the last reference is closed.
     * <p>
     * Note, this method is not thread-safe because Resources are meant to be thread-confined.
     * <p>
     * Subclasses who wish to attach behaviour to the close action should override the {@link #makeInaccessible()}
     * method instead, or make it part of their drop implementation.
     *
     * @throws IllegalStateException If this Resource has already been closed.
     */
    @Override
    public final void close() {
        if (acquires == -1) {
            throw attachTrace(new IllegalStateException("Double-free: Resource already closed and dropped."));
        }
        int acq = acquires;
        acquires--;
        tracer.close(acq);
        if (acq == 0) {
            // The 'acquires' was 0, now decremented to -1, which means we need to drop.
            tracer.drop(0);
            try {
                drop.drop(impl());
            } finally {
                makeInaccessible();
            }
        }
    }

    /**
     * Send this Resource instance to another Thread, transferring the ownership to the recipient.
     * This method can be used when the receiving thread is not known up front.
     * <p>
     * This instance immediately becomes inaccessible, and all attempts at accessing this resource will throw.
     * Calling {@link #close()} will have no effect, so this method is safe to call within a try-with-resources
     * statement.
     *
     * @throws IllegalStateException if this object has any outstanding acquires; that is, if this object has been
     * {@link #acquire() acquired} more times than it has been {@link #close() closed}.
     */
    @Override
    public final Send<I> send() {
        if (acquires < 0) {
            throw attachTrace(createResourceClosedException());
        }
        if (!isOwned()) {
            throw notSendableException();
        }
        try {
            var owned = tracer.send(prepareSend());
            return new SendFromOwned<I, T>(owned, drop, getClass());
        } finally {
            acquires = -2; // Close without dropping. This also ignore future double-free attempts.
            makeInaccessible();
        }
    }

    /**
     * Attach a trace of the life-cycle of this object as suppressed exceptions to the given throwable.
     *
     * @param throwable The throwable to attach a life-cycle trace to.
     * @param <E> The concrete exception type.
     * @return The given exception, which can then be thrown.
     */
    protected <E extends Throwable> E attachTrace(E throwable) {
        return tracer.attachTrace(throwable);
    }

    /**
     * Create an {@link IllegalStateException} with a custom message, tailored to this particular
     * {@link Resource} instance, for when the object cannot be sent for some reason.
     * @return An {@link IllegalStateException} to be thrown when this object cannot be sent.
     */
    protected IllegalStateException notSendableException() {
        return new IllegalStateException(
                "Cannot send() a reference counted object with " + countBorrows() + " borrows: " + this + '.');
    }

    /**
     * Encapsulation bypass to call {@link #isOwned()} on the given object.
     *
     * @param obj The object to query the ownership state on.
     * @return {@code true} if the given object is owned, otherwise {@code false}.
     */
    static boolean isOwned(ResourceSupport<?, ?> obj) {
        return obj.isOwned();
    }

    /**
     * Query if this object is in an "owned" state, which means no other references have been
     * {@linkplain #acquire() acquired} to it.
     *
     * This would usually be the case, since there are no public methods for acquiring references to these objects.
     *
     * @return {@code true} if this object is in an owned state, otherwise {@code false}.
     */
    protected boolean isOwned() {
        return acquires == 0;
    }

    /**
     * Encapsulation bypass to call {@link #countBorrows()} on the given object.
     *
     * @param obj The object to count borrows on.
     * @return The number of borrows, or outstanding {@linkplain #acquire() acquires}, if any, of the given object.
     */
    static int countBorrows(ResourceSupport<?, ?> obj) {
        return obj.countBorrows();
    }

    /**
     * Count the number of borrows of this object.
     * Note that even if the number of borrows is {@code 0}, this object might not be {@linkplain #isOwned() owned}
     * because there could be other restrictions involved in ownership.
     *
     * @return The number of borrows, if any, of this object.
     */
    protected int countBorrows() {
        return Math.max(acquires, 0);
    }

    @Override
    public boolean isAccessible() {
        return acquires >= 0;
    }

    @Override
    public I touch(Object hint) {
        if (isAccessible()) {
            tracer.touch(hint);
        }
        return self();
    }

    /**
     * Prepare this instance for ownership transfer. This method is called from {@link #send()} in the sending thread.
     * This method should put this resource in a deactivated state where it is no longer accessible from the currently
     * owning thread.
     * In this state, the resource instance should only allow a call to {@link Owned#transferOwnership(Drop)} in the
     * recipient thread.
     *
     * @return This resource instance in a deactivated state.
     */
    protected abstract Owned<T> prepareSend();

    /**
     * Called when this resource needs to be considered inaccessible.
     * This is called at the correct points, by the {@link ResourceSupport} class,
     * when the resource is being closed or sent,
     * and can be used to set further traps for accesses that makes accessibility checks cheap.
     * There would normally not be any reason to call this directly from a sub-class.
     */
    protected void makeInaccessible() {
    }

    /**
     * Get access to the underlying {@link Drop} object.
     * This method is unsafe because it opens the possibility of bypassing and overriding resource lifetimes.
     *
     * @return The {@link Drop} object used by this reference counted object.
     */
    protected Drop<T> unsafeGetDrop() {
        return drop;
    }

    /**
     * Replace the current underlying {@link Drop} object with the given one.
     * This method is unsafe because it opens the possibility of bypassing and overriding resource lifetimes.
     *
     * @param replacement The new {@link Drop} object to use instead of the current one.
     */
    protected void unsafeSetDrop(Drop<T> replacement) {
        drop = Objects.requireNonNull(replacement, "Replacement drop cannot be null.");
    }

    @SuppressWarnings("unchecked")
    private I self() {
        return (I) this;
    }

    @SuppressWarnings("unchecked")
    private T impl() {
        return (T) this;
    }
}

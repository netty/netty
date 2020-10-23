/*
 * Copyright 2013 The Netty Project
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
package io.netty.util.internal;

import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.ObjectPool.Handle;
import io.netty.util.internal.ObjectPool.ObjectCreator;

/**
 * Some pending write which should be picked up later.
 */
public final class PendingWrite {
    private static final ObjectPool<PendingWrite> RECYCLER = ObjectPool.newPool(new ObjectCreator<PendingWrite>() {
        @Override
        public PendingWrite newObject(Handle<PendingWrite> handle) {
            return new PendingWrite(handle);
        }
    });

    /**
     * Create a new empty {@link RecyclableArrayList} instance
     */
    public static PendingWrite newInstance(Object msg, Promise<Void> promise) {
        PendingWrite pending = RECYCLER.get();
        pending.msg = msg;
        pending.promise = promise;
        return pending;
    }

    private final Handle<PendingWrite> handle;
    private Object msg;
    private Promise<Void> promise;

    private PendingWrite(Handle<PendingWrite> handle) {
        this.handle = handle;
    }

    /**
     * Clear and recycle this instance.
     */
    public boolean recycle() {
        msg = null;
        promise = null;
        handle.recycle(this);
        return true;
    }

    /**
     * Fails the underlying {@link Promise} with the given cause and recycle this instance.
     */
    public boolean failAndRecycle(Throwable cause) {
        ReferenceCountUtil.release(msg);
        if (promise != null) {
            promise.setFailure(cause);
        }
        return recycle();
    }

    /**
     * Mark the underlying {@link Promise} successfully and recycle this instance.
     */
    public boolean successAndRecycle() {
        if (promise != null) {
            promise.setSuccess(null);
        }
        return recycle();
    }

    public Object msg() {
        return msg;
    }

    public Promise<Void> promise() {
        return promise;
    }

    /**
     * Recycle this instance and return the {@link Promise}.
     */
    public Promise<Void> recycleAndGet() {
        Promise<Void> promise = this.promise;
        recycle();
        return promise;
    }
}

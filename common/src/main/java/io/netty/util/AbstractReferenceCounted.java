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
package io.netty.util;

import io.netty.util.internal.RefCnt;

/**
 * Abstract base class for classes wants to implement {@link ReferenceCounted}.
 */
public abstract class AbstractReferenceCounted extends RefCnt implements ReferenceCounted {

    @Override
    public int refCnt() {
        return RefCnt.refCnt(this);
    }

    /**
     * An unsafe operation intended for use by a subclass that sets the reference count of the object directly
     */
    protected void setRefCnt(int refCnt) {
        RefCnt.setRefCnt(this, refCnt);
    }

    @Override
    public ReferenceCounted retain() {
        RefCnt.retain(this);
        return this;
    }

    @Override
    public ReferenceCounted retain(int increment) {
        RefCnt.retain(this, increment);
        return this;
    }

    @Override
    public ReferenceCounted touch() {
        return touch(null);
    }

    @Override
    public boolean release() {
        return handleRelease(RefCnt.release(this));
    }

    @Override
    public boolean release(int decrement) {
        return handleRelease(RefCnt.release(this, decrement));
    }

    private boolean handleRelease(boolean result) {
        if (result) {
            deallocate();
        }
        return result;
    }

    /**
     * Called once {@link #refCnt()} is equals 0.
     */
    protected abstract void deallocate();
}

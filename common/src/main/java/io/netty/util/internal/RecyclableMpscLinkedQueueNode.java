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

package io.netty.util.internal;

import io.netty.util.Recycler;

/**
 * {@link MpscLinkedQueueNode} that will automatically call {@link #recycle(Recycler.Handle)} when the node was
 * unlinked.
 */
public abstract class RecyclableMpscLinkedQueueNode<T> extends MpscLinkedQueueNode<T> {
    private final Recycler.Handle handle;

    protected RecyclableMpscLinkedQueueNode(Recycler.Handle handle) {
        if (handle == null) {
            throw new NullPointerException("handle");
        }
        this.handle = handle;
    }

    @Override
    final void unlink() {
        super.unlink();
        recycle(handle);
    }

    /**
     * Called once unliked and so ready to recycled.
     */
    protected abstract void recycle(Recycler.Handle handle);
}

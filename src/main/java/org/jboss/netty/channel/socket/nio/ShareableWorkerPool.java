/*
 * Copyright 2012 The Netty Project
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
package org.jboss.netty.channel.socket.nio;

import org.jboss.netty.channel.socket.Worker;
import org.jboss.netty.util.ExternalResourceReleasable;

/**
 * This implementation of a {@link WorkerPool} should be used if you plan to share a
 * {@link WorkerPool} between different Factories. You will need to call {@link #destroy()} by your
 * own once you want to release any resources of it.
 *
 *
 */
public final class ShareableWorkerPool<E extends Worker> implements WorkerPool<E> {

    private final WorkerPool<E> wrapped;

    public ShareableWorkerPool(WorkerPool<E> wrapped) {
        this.wrapped = wrapped;
    }

    public E nextWorker() {
        return wrapped.nextWorker();
    }

    public void rebuildSelectors() {
        wrapped.rebuildSelectors();
    }

    /**
     * Destroy the {@link ShareableWorkerPool} and release all resources. After this is called its not usable anymore
     */
    public void destroy() {
        wrapped.shutdown();
        if (wrapped instanceof ExternalResourceReleasable) {
            ((ExternalResourceReleasable) wrapped).releaseExternalResources();
        }
    }

    public void shutdown() {
        // do nothing
    }
}

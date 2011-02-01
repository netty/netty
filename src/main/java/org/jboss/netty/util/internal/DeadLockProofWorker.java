/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.util.internal;

import java.util.concurrent.Executor;

/**
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public final class DeadLockProofWorker {

    /**
     * An <em>internal use only</em> thread-local variable that tells the
     * {@link Executor} that this worker acquired a worker thread from.
     */
    public static final ThreadLocal<Executor> PARENT = new ThreadLocal<Executor>();

    public static void start(final Executor parent, final Runnable runnable) {
        if (parent == null) {
            throw new NullPointerException("parent");
        }
        if (runnable == null) {
            throw new NullPointerException("runnable");
        }

        parent.execute(new Runnable() {
            public void run() {
                PARENT.set(parent);
                try {
                    runnable.run();
                } finally {
                    PARENT.remove();
                }
            }
        });
    }

    private DeadLockProofWorker() {
        super();
    }
}

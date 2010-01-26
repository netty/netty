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

import org.jboss.netty.channel.ChannelFuture;

/**
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev$, $Date$
 */
public class IoWorkerRunnable implements Runnable {

    /**
     * An <em>internal use only</em> thread-local variable that determines if
     * the caller is running on an I/O worker thread, which is the case where
     * the caller enters a dead lock if the caller calls
     * {@link ChannelFuture#await()} or {@link ChannelFuture#awaitUninterruptibly()}.
     */
    public static final ThreadLocal<Boolean> IN_IO_THREAD = new ThreadLocalBoolean();

    private final Runnable runnable;

    public IoWorkerRunnable(Runnable runnable) {
        if (runnable == null) {
            throw new NullPointerException("runnable");
        }
        this.runnable = runnable;
    }

    public void run() {
        IN_IO_THREAD.set(Boolean.TRUE);
        try {
            runnable.run();
        } finally {
            IN_IO_THREAD.remove();
        }
    }
}

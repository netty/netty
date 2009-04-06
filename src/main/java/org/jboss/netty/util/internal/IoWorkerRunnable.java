/*
 * Copyright (C) 2009  Trustin Heuiseung Lee
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.util.internal;

import org.jboss.netty.channel.ChannelFuture;

/**
 * @author Trustin Heui-seung Lee (trustin@gmail.com)
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

/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.netty.channel;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A {@link ChannelFactoryResource} whose underlying resource is a list of
 * {@link Executor}s.  {@link #release()} will shut down all specified
 * {@link ExecutorService}s immediately and wait for their termination.
 * An {@link Executor} which is not an {@link ExecutorService} will be ignored.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class ChannelFactoryExecutorResource implements
        ChannelFactoryResource {

    private final Executor[] executors;

    /**
     * Creates a new instance with the specified executors.
     *
     * @param executors a list of {@link Executor}s
     */
    public ChannelFactoryExecutorResource(Executor... executors) {
        if (executors == null) {
            throw new NullPointerException("executors");
        }
        this.executors = new Executor[executors.length];
        for (int i = 0; i < executors.length; i ++) {
            if (executors[i] == null) {
                throw new NullPointerException("executors[" + i + "]");
            }
            this.executors[i] = executors[i];
        }
    }

    public void release() {
        for (Executor e: executors) {
            if (!(e instanceof ExecutorService)) {
                continue;
            }

            ExecutorService es = (ExecutorService) e;
            for (;;) {
                es.shutdownNow();
                try {
                    if (es.awaitTermination(1, TimeUnit.SECONDS)) {
                        break;
                    }
                } catch (InterruptedException ex) {
                    // Ignore.
                }
            }
        }
    }
}

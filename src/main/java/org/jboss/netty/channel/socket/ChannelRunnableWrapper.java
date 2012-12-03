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
package org.jboss.netty.channel.socket;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.DefaultChannelFuture;

public class ChannelRunnableWrapper extends DefaultChannelFuture implements Runnable {

    private final Runnable task;
    private boolean started;

    public ChannelRunnableWrapper(Channel channel, Runnable task) {
        super(channel, true);
        this.task = task;
    }

    public void run() {
        synchronized (this) {
            if (!isCancelled()) {
                started = true;
            } else {
                return;
            }
        }
        try {
            task.run();
            setSuccess();
        } catch (Throwable t) {
            setFailure(t);
        }
    }

    @Override
    public synchronized boolean cancel() {
        if (started) {
            return false;
        }
        return super.cancel();
    }
}

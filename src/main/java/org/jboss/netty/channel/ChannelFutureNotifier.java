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
package org.jboss.netty.channel;

/**
 * ChannelFutureListener implementation which takes another ChannelFuture and notifies it
 * once the operationComplete method was called.
 */
public final class ChannelFutureNotifier implements ChannelFutureListener {
    private final ChannelFuture future;

    public ChannelFutureNotifier(ChannelFuture future) {
        this.future = future;
    }

    public void operationComplete(ChannelFuture cf) throws Exception {
        if (cf.isSuccess()) {
            future.setSuccess();
        } else {
            future.setFailure(cf.getCause());
        }
    }
}

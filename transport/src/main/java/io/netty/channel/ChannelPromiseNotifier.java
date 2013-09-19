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
package io.netty.channel;

/**
 * ChannelFutureListener implementation which takes other {@link ChannelFuture}(s) and notifies them on completion.
 */
public final class ChannelPromiseNotifier implements ChannelFutureListener {

    private final ChannelPromise[] promises;

    /**
     * Create a new instance
     *
     * @param promises  the {@link ChannelPromise}s to notify once this {@link ChannelFutureListener} is notified.
     */
    public ChannelPromiseNotifier(ChannelPromise... promises) {
        if (promises == null) {
            throw new NullPointerException("promises");
        }
        for (ChannelPromise promise: promises) {
            if (promise == null) {
                throw new IllegalArgumentException("promises contains null ChannelPromise");
            }
        }
        this.promises = promises.clone();
    }

    @Override
    public void operationComplete(ChannelFuture cf) throws Exception {
        if (cf.isSuccess()) {
            for (ChannelPromise p: promises) {
                p.setSuccess();
            }
            return;
        }

        Throwable cause = cf.cause();
        for (ChannelPromise p: promises) {
            p.setFailure(cause);
        }
    }
}

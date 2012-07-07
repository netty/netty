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

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Custom {@link Callable} implementation which will catch all {@link Throwable} which happens
 * during execution of {@link DefaultChannelPipelineModificationTask#doCall()} and return them in the
 * {@link Future}. This allows to re-throw them later.
 *
 * It also handles the right synchronization of the {@link DefaultChannelPipelineModificationTask#doCall()}
 * method.
 *
 * It was originally an inner class of {@link DefaultChannelPipeline}, but moved to a top level
 * type to work around a compiler bug.
 */
abstract class DefaultChannelPipelineModificationTask implements Callable<Throwable> {

    private final ChannelPipeline lock;

    DefaultChannelPipelineModificationTask(ChannelPipeline lock) {
        this.lock = lock;
    }

    @Override
    public Throwable call() {
        try {
            synchronized (lock) {
                doCall();
            }
        } catch (Throwable t) {
            return t;
        }
        return null;
    }

    /**
     * Execute the modification
     */
    abstract void doCall();

}

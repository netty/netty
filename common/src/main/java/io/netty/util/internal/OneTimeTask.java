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

import io.netty.util.concurrent.EventExecutor;

/**
 * {@link Runnable} which represent a one time task which may allow the {@link EventExecutor} to reduce the amount of
 * produced garbage when queue it for execution.
 *
 * <strong>It is important this will not be reused. After submitted it is not allowed to get submitted again!</strong>
 */
public abstract class OneTimeTask extends MpscLinkedQueueNode<Runnable> implements Runnable {

    @Override
    public Runnable value() {
        return this;
    }
}

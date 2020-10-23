/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ssl;

import io.netty.util.internal.ObjectUtil;

public abstract class SslCompletionEvent {

    private final Throwable cause;

    SslCompletionEvent() {
        cause = null;
    }

    SslCompletionEvent(Throwable cause) {
        this.cause = ObjectUtil.checkNotNull(cause, "cause");
    }

    /**
     * Return {@code true} if the completion was successful
     */
    public final boolean isSuccess() {
        return cause == null;
    }

    /**
     * Return the {@link Throwable} if {@link #isSuccess()} returns {@code false}
     * and so the completion failed.
     */
    public final Throwable cause() {
        return cause;
    }

    @Override
    public  String toString() {
        final Throwable cause = cause();
        return cause == null? getClass().getSimpleName() + "(SUCCESS)" :
                getClass().getSimpleName() +  '(' + cause + ')';
    }
}

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
package io.netty.util.concurrent;

import io.netty.util.internal.InternalThreadLocalMap;

/**
 * A special {@link Thread} that provides fast access to {@link FastThreadLocal} variables.
 */
public class FastThreadLocalThread extends Thread {

    private InternalThreadLocalMap threadLocalMap;

    public FastThreadLocalThread() { }

    public FastThreadLocalThread(Runnable target) {
        super(target);
    }

    public FastThreadLocalThread(ThreadGroup group, Runnable target) {
        super(group, target);
    }

    public FastThreadLocalThread(String name) {
        super(name);
    }

    public FastThreadLocalThread(ThreadGroup group, String name) {
        super(group, name);
    }

    public FastThreadLocalThread(Runnable target, String name) {
        super(target, name);
    }

    public FastThreadLocalThread(ThreadGroup group, Runnable target, String name) {
        super(group, target, name);
    }

    public FastThreadLocalThread(ThreadGroup group, Runnable target, String name, long stackSize) {
        super(group, target, name, stackSize);
    }

    /**
     * Returns the internal data structure that keeps the thread-local variables bound to this thread.
     * Note that this method is for internal use only, and thus is subject to change at any time.
     */
    public final InternalThreadLocalMap threadLocalMap() {
        return threadLocalMap;
    }

    /**
     * Sets the internal data structure that keeps the thread-local variables bound to this thread.
     * Note that this method is for internal use only, and thus is subject to change at any time.
     */
    public final void setThreadLocalMap(InternalThreadLocalMap threadLocalMap) {
        this.threadLocalMap = threadLocalMap;
    }
}

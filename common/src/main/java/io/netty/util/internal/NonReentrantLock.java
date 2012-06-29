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
package io.netty.util.internal;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * A custom implementation of a lock that does not allow reentry
 */
public final class NonReentrantLock extends AbstractQueuedSynchronizer
        implements Lock {

    /**
     * The serial version unique ID
     */
    private static final long serialVersionUID = -833780837233068610L;

    /**
     * The {@link Thread} that owns this {@link NonReentrantLock}
     */
    private Thread owner;

    /**
     * Locks this {@link NonReentrantLock}
     */
    @Override
    public void lock() {
        acquire(1);
    }

    /**
     * Locks this {@link NonReentrantLock}, but allow interruption
     *
     * @throws InterruptedException The lock was interrupted
     */
    @Override
    public void lockInterruptibly() throws InterruptedException {
        acquireInterruptibly(1);
    }

    /**
     * Try to lock this {@link NonReentrantLock}
     *
     * @return True if locking was successful, otherwise false
     */
    @Override
    public boolean tryLock() {
        return tryAcquire(1);
    }

    /**
     * Tries to lock this {@link NonReentrantLock} over a period of time
     *
     * @param time The maximum number of time units to attempt to get a lock for.
     * @param unit The {@link TimeUnit} associated with the time parameter
     * @return True if the lock was successful, otherwise false
     * @throws InterruptedException The locking attempt was interrupted
     */
    @Override
    public boolean tryLock(long time, TimeUnit unit)
            throws InterruptedException {
        return tryAcquireNanos(1, unit.toNanos(time));
    }

    /**
     * Unlocks this {@link NonReentrantLock}
     */
    @Override
    public void unlock() {
        release(1);
    }

    /**
     * Checks to see if this {@link NonReentrantLock} is held by the current {@link Thread}
     *
     * @return True if held by the current thread, otherwise false
     */
    public boolean isHeldByCurrentThread() {
        return isHeldExclusively();
    }

    /**
     * Creates a new {@link Condition}
     *
     * @return The condition object
     */
    @Override
    public Condition newCondition() {
        return new ConditionObject();
    }

    /**
     * Try to acquire a lock
     *
     * @param acquires A number that is sent by acquiring methods
     * @return True if a lock is acquired, otherwise false
     */
    @Override
    protected boolean tryAcquire(int acquires) {
        if (compareAndSetState(0, 1)) {
            owner = Thread.currentThread();
            return true;
        }
        return false;
    }

    /**
     * Tries to release the lock
     *
     * @param releases A number that is passed by the release methods
     * @return True if a release is granted, otherwise false
     */
    @Override
    protected boolean tryRelease(int releases) {
        if (Thread.currentThread() != owner) {
            throw new IllegalMonitorStateException();
        }
        owner = null;
        setState(0);
        return true;
    }

    /**
     * Checks to see if this {@link NonReentrantLock} is held exclusively by the current {@link Thread}
     *
     * @return True if held exclusively, otherwise false
     */
    @Override
    protected boolean isHeldExclusively() {
        return getState() != 0 && owner == Thread.currentThread();
    }
}

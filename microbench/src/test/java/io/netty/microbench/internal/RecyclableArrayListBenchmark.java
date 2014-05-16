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
package io.netty.microbench.internal;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.internal.RecyclableArrayList;
import org.openjdk.jmh.annotations.GenerateMicroBenchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;

/**
 * This class benchmarks different allocators with different allocation sizes.
 */
@Threads(4)
@Measurement(iterations = 10, batchSize = 100)
public class RecyclableArrayListBenchmark extends AbstractMicrobenchmark {

    @Param({ "00000", "00256", "01024", "04096", "16384", "65536" })
    public int size;

    private static final AtomicReference<Role> lastRole = new AtomicReference<Role>();

    @State(Scope.Thread)
    public static class Role {

        Queue<RecyclableArrayList> lists;
        Semaphore create;
        Semaphore recycle;
        Type type;
        int recycleBuffered;
        int createBuffered;

        public Role() {
            while (true) {
                Role prev = lastRole.get();
                Type prevType = prev == null ? Type.RECYCLE : prev.type;
                switch (prevType) {
                    case RECYCLE:
                        type = Type.PRODUCE;
                        lists = new ConcurrentLinkedQueue<RecyclableArrayList>();
                        create = new Semaphore(5000);
                        recycle = new Semaphore(0);
                        break;
                    case PRODUCE:
                        type = Type.RECYCLE;
                        lists = prev.lists;
                        create = prev.create;
                        recycle = prev.recycle;
                        break;
                    default:
                        throw new IllegalStateException();
                }
                if (lastRole.compareAndSet(prev, this)) {
                    break;
                }
            }
        }

        private static enum Type {
            PRODUCE, RECYCLE
        }
    }

    @GenerateMicroBenchmark
    public void recycleOtherThread(Role role) throws InterruptedException {
        switch (role.type) {
            case PRODUCE:
                if (!role.create.tryAcquire()) {
                    role.recycle.release(role.createBuffered);
                    role.createBuffered = 0;
                    if (!role.create.tryAcquire(1L, TimeUnit.MILLISECONDS)) {
                        return;
                    }
                }
                role.lists.add(RecyclableArrayList.newInstance(size));
                role.createBuffered++;
                break;
            case RECYCLE:
                if (!role.recycle.tryAcquire()) {
                    role.create.release(role.recycleBuffered);
                    role.recycleBuffered = 0;
                    if (!role.recycle.tryAcquire(1L, TimeUnit.MILLISECONDS)) {
                        return;
                    }
                }
                role.lists.poll().recycle();
                role.recycleBuffered++;
                break;
        }
    }

    @GenerateMicroBenchmark
    public void recycleSameThread(Role role) {
        RecyclableArrayList list = RecyclableArrayList.newInstance(size);
        list.recycle();
    }
}

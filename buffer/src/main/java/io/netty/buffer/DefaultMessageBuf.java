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
package io.netty.buffer;

import java.util.ArrayDeque;
import java.util.Collection;

final class DefaultMessageBuf<T> extends ArrayDeque<T> implements MessageBuf<T> {

    private static final long serialVersionUID = 1229808623624907552L;

    private boolean freed;

    DefaultMessageBuf() { }

    DefaultMessageBuf(int initialCapacity) {
        super(initialCapacity);
    }

    @Override
    public BufType type() {
        return BufType.MESSAGE;
    }

    @Override
    public int drainTo(Collection<? super T> c) {
        int cnt = 0;
        for (;;) {
            T o = poll();
            if (o == null) {
                break;
            }
            c.add(o);
            cnt ++;
        }
        return cnt;
    }

    @Override
    public int drainTo(Collection<? super T> c, int maxElements) {
        int cnt = 0;
        while (cnt < maxElements) {
            T o = poll();
            if (o == null) {
                break;
            }
            c.add(o);
            cnt ++;
        }
        return cnt;
    }

    @Override
    public boolean isFreed() {
        return freed;
    }

    @Override
    public void free() {
        freed = true;
    }
}

/*
 * Copyright 2013 The Netty Project
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
/*
 * Written by Josh Bloch of Google Inc. and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/.
 */
package io.netty.channel;

import io.netty.util.Recycler;
import io.netty.util.Recycler.Handle;

/**
 * Default implementation of {@link ChannelOutboundBuffer} which should be suitable for most transport implementations.
 */
public class DefaultChannelOutboundBuffer extends ChannelOutboundBuffer {

    private Entry first;
    private Entry last;
    private int flushed;
    private int messages;

    public DefaultChannelOutboundBuffer(AbstractChannel channel) {
        super(channel);
    }

    @Override
    protected void addMessage(Object msg, int pendingSize, ChannelPromise promise) {
        Entry e = Entry.newInstance(this);
        if (last == null) {
            first = e;
            last = e;
        } else {
            last.next = e;
            last = e;
        }
        e.msg = msg;
        e.pendingSize = pendingSize;
        e.promise = promise;
        e.total = total(msg);

        messages++;
    }

    @Override
    protected void addFlush() {
        flushed = messages;
    }

    @Override
    public Object current() {
        if (isEmpty()) {
            return null;
        } else {
            return first.msg;
        }
    }

    @Override
    public void progress(long amount) {
        Entry e = first;
        e.pendingSize -= amount;
        assert e.pendingSize >= 0;
        ChannelPromise p = e.promise;
        if (p instanceof ChannelProgressivePromise) {
            long progress = e.progress + amount;
            e.progress = progress;
            ((ChannelProgressivePromise) p).tryProgress(progress, e.total);
        }
    }

    @Override
    public boolean remove() {
        if (isEmpty()) {
            return false;
        }

        Entry e = first;
        first = e.next;
        if (first == null) {
            last = null;
        }

        messages--;
        flushed--;
        e.success();

        return true;
    }

    @Override
    public boolean remove(Throwable cause) {
        if (isEmpty()) {
            return false;
        }

        Entry e = first;
        first = e.next;
        if (first == null) {
            last = null;
        }

        messages--;
        flushed--;
        e.fail(cause, true);

        return true;
    }

    @Override
    public int size() {
        return flushed;
    }

    @Override
    protected void failUnflushed(Throwable cause) {
        Entry e = first;
        while (e != null) {
            e.fail(cause, false);
            e = e.next;
        }
    }

    static class Entry {
        private static final Recycler<Entry> RECYCLER = new Recycler<Entry>() {
            @Override
            protected Entry newObject(Handle<Entry> handle) {
                return new Entry(handle);
            }
        };

        static Entry newInstance(DefaultChannelOutboundBuffer buffer) {
            Entry entry = RECYCLER.get();
            entry.buffer = buffer;
            return entry;
        }

        private final Recycler.Handle<Entry> entryHandle;
        private Object msg;
        private ChannelPromise promise;
        private long progress;
        private long total;
        private long pendingSize;
        private Entry next;
        private DefaultChannelOutboundBuffer buffer;

        @SuppressWarnings("unchecked")
        protected Entry(Recycler.Handle<? extends Entry> entryHandle) {
            this.entryHandle = (Handle<Entry>) entryHandle;
        }

        private void success() {
            try {
                safeRelease(msg);
                promise.trySuccess();
                buffer.decrementPendingOutboundBytes(pendingSize);
            } finally {
                recycle();
            }
        }

        private void fail(Throwable cause, boolean decrementAndNotify) {
            try {
                safeRelease(msg);
                safeFail(promise, cause);
                if (decrementAndNotify) {
                    buffer.decrementPendingOutboundBytes(pendingSize);
                }
            } finally {
                recycle();
            }
        }

        private void recycle() {
            msg = null;
            promise = null;
            progress = 0;
            total = 0;
            pendingSize = 0;
            next = null;
            buffer = null;
            entryHandle.recycle(this);
        }
    }

    @Override
    protected void onRecycle() {
        first = null;
        last = null;
        flushed = 0;
        messages = 0;
    }
}

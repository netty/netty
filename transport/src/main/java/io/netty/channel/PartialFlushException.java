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
package io.netty.channel;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Special {@link ChannelException} which will be used by {@link ChannelOutboundInvoker#flush(ChannelPromise)},
 * {@link ChannelOutboundInvoker#flush()}, {@link ChannelOutboundInvoker#write(Object)} and
 * {@link ChannelOutboundInvoker#write(Object, ChannelPromise)} if the operation was only partial successful.
 */
public class PartialFlushException extends ChannelException implements Iterable<Throwable> {
    private static final long serialVersionUID = 990261865971015004L;

    private final Throwable[] causes;

    public PartialFlushException(String msg, Throwable... causes) {
        super(msg);
        if (causes == null) {
            throw new NullPointerException("causes");
        }
        if (causes.length == 0) {
            throw new IllegalArgumentException("causes must at least contain one element");
        }
        this.causes = causes;
    }

    public PartialFlushException(Throwable... causes) {
        if (causes == null) {
            throw new NullPointerException("causes");
        }
        if (causes.length == 0) {
            throw new IllegalArgumentException("causes must at least contain one element");
        }
        this.causes = causes;
    }

    /**
     * Return an {@link Iterator} which holds all of the {@link Throwable} for the failed flush.
     */
    @Override
    public Iterator<Throwable> iterator() {
        return new Iterator<Throwable>() {
            private int index;
            @Override
            public boolean hasNext() {
                return index < causes.length;
            }

            @Override
            public Throwable next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return causes[index++];
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("Read-only");
            }
        };
    }
}

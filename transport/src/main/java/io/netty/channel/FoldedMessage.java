/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import io.netty.buffer.MessageBuf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Special {@link Iterator} which can be used to pass multiple messages at the same time to an Inbound or Outbound
 * {@link MessageBuf}.
 *
 */
public final class FoldedMessage implements Iterator<Object> {
    private final List<Object> messages;
    private int index;

    public FoldedMessage(Object... msgs) {
        if (msgs == null) {
            throw new NullPointerException("messages");
        }
        messages = new ArrayList<Object>(msgs.length);
        for (Object msg: msgs) {
            unfold(msg, messages);
        }
    }

    private static void unfold(Object msg, List<Object> messages) {
        if (msg instanceof FoldedMessage) {
            FoldedMessage folded = (FoldedMessage) msg;
            if (folded.hasNext()) {
                unfold(folded.next(), messages);
            }
        } else {
            if (msg == null) {
                throw new IllegalArgumentException("messages contains null value");
            }
            messages.add(msg);
        }
    }

    @Override
    public boolean hasNext() {
        return elements() > 0;
    }

    @Override
    public Object next() {
        if (hasNext()) {
            return messages.get(index++);
        }
        throw new NoSuchElementException();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    public int elements() {
        return messages.size() - index;
    }

    public Object get(int index) {
        return messages.get(index);
    }
}

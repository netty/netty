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
package org.jboss.netty.handler.codec.spdy;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.channel.MessageEvent;

final class SpdySession {

    private static final SpdyProtocolException STREAM_CLOSED = new SpdyProtocolException("Stream closed");

    private final AtomicInteger activeLocalStreams  = new AtomicInteger();
    private final AtomicInteger activeRemoteStreams = new AtomicInteger();
    private final Map<Integer, StreamState> activeStreams =
        new ConcurrentHashMap<Integer, StreamState>();

    int numActiveStreams(boolean remote) {
        if (remote) {
            return activeRemoteStreams.get();
        } else {
            return activeLocalStreams.get();
        }
    }

    boolean noActiveStreams() {
        return activeStreams.isEmpty();
    }

    boolean isActiveStream(int streamId) {
        return activeStreams.containsKey(streamId);
    }

    // Stream-IDs should be iterated in priority order
    Set<Integer> getActiveStreams() {
        TreeSet<Integer> StreamIds = new TreeSet<Integer>(new PriorityComparator());
        StreamIds.addAll(activeStreams.keySet());
        return StreamIds;
    }

    void acceptStream(
            int streamId, byte priority, boolean remoteSideClosed, boolean localSideClosed,
            int sendWindowSize, int receiveWindowSize, boolean remote) {
        if (!remoteSideClosed || !localSideClosed) {
            StreamState state = activeStreams.put(
                    streamId,
                    new StreamState(priority, remoteSideClosed, localSideClosed, sendWindowSize, receiveWindowSize));
            if (state == null) {
                if (remote) {
                    activeRemoteStreams.incrementAndGet();
                } else {
                    activeLocalStreams.incrementAndGet();
                }
            }
        }
    }

    private StreamState removeActiveStream(int streamId, boolean remote) {
        StreamState state = activeStreams.remove(streamId);
        if (state != null) {
            if (remote) {
                activeRemoteStreams.decrementAndGet();
            } else {
                activeLocalStreams.decrementAndGet();
            }
        }
        return state;
    }

    void removeStream(int streamId, boolean remote) {
        StreamState state = removeActiveStream(streamId, remote);
        if (state != null) {
            MessageEvent e = state.removePendingWrite();
            while (e != null) {
                e.getFuture().setFailure(STREAM_CLOSED);
                e = state.removePendingWrite();
            }
        }
    }

    boolean isRemoteSideClosed(int streamId) {
        StreamState state = activeStreams.get(streamId);
        return state == null || state.isRemoteSideClosed();
    }

    void closeRemoteSide(int streamId, boolean remote) {
        StreamState state = activeStreams.get(streamId);
        if (state != null) {
            state.closeRemoteSide();
            if (state.isLocalSideClosed()) {
                removeActiveStream(streamId, remote);
            }
        }
    }

    boolean isLocalSideClosed(int streamId) {
        StreamState state = activeStreams.get(streamId);
        return state == null || state.isLocalSideClosed();
    }

    void closeLocalSide(int streamId, boolean remote) {
        StreamState state = activeStreams.get(streamId);
        if (state != null) {
            state.closeLocalSide();
            if (state.isRemoteSideClosed()) {
                removeActiveStream(streamId, remote);
            }
        }
    }

    /*
     * hasReceivedReply and receivedReply are only called from messageReceived
     * no need to synchronize access to the StreamState
     */

    boolean hasReceivedReply(int streamId) {
        StreamState state = activeStreams.get(streamId);
        return state != null && state.hasReceivedReply();
    }

    void receivedReply(int streamId) {
        StreamState state = activeStreams.get(streamId);
        if (state != null) {
            state.receivedReply();
        }
    }

    int getSendWindowSize(int streamId) {
        StreamState state = activeStreams.get(streamId);
        return state != null ? state.getSendWindowSize() : -1;
    }

    int updateSendWindowSize(int streamId, int deltaWindowSize) {
        StreamState state = activeStreams.get(streamId);
        return state != null ? state.updateSendWindowSize(deltaWindowSize) : -1;
    }

    int updateReceiveWindowSize(int streamId, int deltaWindowSize) {
        StreamState state = activeStreams.get(streamId);
        if (deltaWindowSize > 0) {
            state.setReceiveWindowSizeLowerBound(0);
        }
        return state != null ? state.updateReceiveWindowSize(deltaWindowSize) : -1;
    }

    int getReceiveWindowSizeLowerBound(int streamId) {
        StreamState state = activeStreams.get(streamId);
        return state != null ? state.getReceiveWindowSizeLowerBound() : 0;
    }

    void updateAllReceiveWindowSizes(int deltaWindowSize) {
        for (StreamState state: activeStreams.values()) {
            state.updateReceiveWindowSize(deltaWindowSize);
            if (deltaWindowSize < 0) {
                state.setReceiveWindowSizeLowerBound(deltaWindowSize);
            }
        }
    }

    boolean putPendingWrite(int streamId, MessageEvent evt) {
        StreamState state = activeStreams.get(streamId);
        return state != null && state.putPendingWrite(evt);
    }

    MessageEvent getPendingWrite(int streamId) {
        StreamState state = activeStreams.get(streamId);
        return state != null ? state.getPendingWrite() : null;
    }

    MessageEvent removePendingWrite(int streamId) {
        StreamState state = activeStreams.get(streamId);
        return state != null ? state.removePendingWrite() : null;
    }

    private static final class StreamState {

        private final byte priority;
        private volatile boolean remoteSideClosed;
        private volatile boolean localSideClosed;
        private boolean receivedReply;
        private final AtomicInteger sendWindowSize;
        private final AtomicInteger receiveWindowSize;
        private volatile int receiveWindowSizeLowerBound;
        private final ConcurrentLinkedQueue<MessageEvent> pendingWriteQueue =
                new ConcurrentLinkedQueue<MessageEvent>();

        StreamState(
                byte priority, boolean remoteSideClosed, boolean localSideClosed,
                int sendWindowSize, int receiveWindowSize) {
            this.priority = priority;
            this.remoteSideClosed = remoteSideClosed;
            this.localSideClosed = localSideClosed;
            this.sendWindowSize = new AtomicInteger(sendWindowSize);
            this.receiveWindowSize = new AtomicInteger(receiveWindowSize);
        }

        byte getPriority() {
            return priority;
        }

        boolean isRemoteSideClosed() {
            return remoteSideClosed;
        }

        void closeRemoteSide() {
            remoteSideClosed = true;
        }

        boolean isLocalSideClosed() {
            return localSideClosed;
        }

        void closeLocalSide() {
            localSideClosed = true;
        }

        boolean hasReceivedReply() {
            return receivedReply;
        }

        void receivedReply() {
            receivedReply = true;
        }

        int getSendWindowSize() {
            return sendWindowSize.get();
        }

        int updateSendWindowSize(int deltaWindowSize) {
            return sendWindowSize.addAndGet(deltaWindowSize);
        }

        int updateReceiveWindowSize(int deltaWindowSize) {
            return receiveWindowSize.addAndGet(deltaWindowSize);
        }

        int getReceiveWindowSizeLowerBound() {
            return receiveWindowSizeLowerBound;
        }

        void setReceiveWindowSizeLowerBound(int receiveWindowSizeLowerBound) {
            this.receiveWindowSizeLowerBound = receiveWindowSizeLowerBound;
        }

        boolean putPendingWrite(MessageEvent evt) {
            return pendingWriteQueue.offer(evt);
        }

        MessageEvent getPendingWrite() {
            return pendingWriteQueue.peek();
        }

        MessageEvent removePendingWrite() {
            return pendingWriteQueue.poll();
        }
    }

    private final class PriorityComparator implements Comparator<Integer> {

        PriorityComparator() {
        }

        public int compare(Integer id1, Integer id2) {
            StreamState state1 = activeStreams.get(id1);
            StreamState state2 = activeStreams.get(id2);
            return state1.getPriority() - state2.getPriority();
        }
    }
}

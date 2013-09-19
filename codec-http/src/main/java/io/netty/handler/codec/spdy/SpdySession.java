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
package io.netty.handler.codec.spdy;

import io.netty.channel.ChannelPromise;
import io.netty.util.internal.PlatformDependent;

import java.util.Comparator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

final class SpdySession {

    private final Map<Integer, StreamState> activeStreams = PlatformDependent.newConcurrentHashMap();

    int numActiveStreams() {
        return activeStreams.size();
    }

    boolean noActiveStreams() {
        return activeStreams.isEmpty();
    }

    boolean isActiveStream(int streamId) {
        return activeStreams.containsKey(streamId);
    }

    // Stream-IDs should be iterated in priority order
    Set<Integer> getActiveStreams() {
        TreeSet<Integer> streamIds = new TreeSet<Integer>(new PriorityComparator());
        streamIds.addAll(activeStreams.keySet());
        return streamIds;
    }

    void acceptStream(
            int streamId, byte priority, boolean remoteSideClosed, boolean localSideClosed,
            int sendWindowSize, int receiveWindowSize) {
        if (!remoteSideClosed || !localSideClosed) {
            activeStreams.put(streamId, new StreamState(
                    priority, remoteSideClosed, localSideClosed, sendWindowSize, receiveWindowSize));
        }
    }

    void removeStream(int streamId, Throwable cause) {
        StreamState state = activeStreams.remove(streamId);
        if (state != null) {
            state.clearPendingWrites(cause);
        }
    }

    boolean isRemoteSideClosed(int streamId) {
        StreamState state = activeStreams.get(streamId);
        return state == null || state.isRemoteSideClosed();
    }

    void closeRemoteSide(int streamId) {
        StreamState state = activeStreams.get(streamId);
        if (state != null) {
            state.closeRemoteSide();
            if (state.isLocalSideClosed()) {
                activeStreams.remove(streamId);
            }
        }
    }

    boolean isLocalSideClosed(int streamId) {
        StreamState state = activeStreams.get(streamId);
        return state == null || state.isLocalSideClosed();
    }

    void closeLocalSide(int streamId) {
        StreamState state = activeStreams.get(streamId);
        if (state != null) {
            state.closeLocalSide();
            if (state.isRemoteSideClosed()) {
                activeStreams.remove(streamId);
            }
        }
    }

    /*
     * hasReceivedReply and receivedReply are only called from channelRead()
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

    void updateAllSendWindowSizes(int deltaWindowSize) {
        for (StreamState state: activeStreams.values()) {
            state.updateSendWindowSize(deltaWindowSize);
        }
    }

    void updateAllReceiveWindowSizes(int deltaWindowSize) {
        for (StreamState state: activeStreams.values()) {
            state.updateReceiveWindowSize(deltaWindowSize);
            if (deltaWindowSize < 0) {
                state.setReceiveWindowSizeLowerBound(deltaWindowSize);
            }
        }
    }

    boolean putPendingWrite(int streamId, PendingWrite pendingWrite) {
        StreamState state = activeStreams.get(streamId);
        return state != null && state.putPendingWrite(pendingWrite);
    }

    PendingWrite getPendingWrite(int streamId) {
        StreamState state = activeStreams.get(streamId);
        return state != null ? state.getPendingWrite() : null;
    }

    PendingWrite removePendingWrite(int streamId) {
        StreamState state = activeStreams.get(streamId);
        return state != null ? state.removePendingWrite() : null;
    }

    private static final class StreamState {

        private final byte priority;
        private boolean remoteSideClosed;
        private boolean localSideClosed;
        private boolean receivedReply;
        private final AtomicInteger sendWindowSize;
        private final AtomicInteger receiveWindowSize;
        private int receiveWindowSizeLowerBound;
        private final Queue<PendingWrite> pendingWriteQueue = new ConcurrentLinkedQueue<PendingWrite>();

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

        boolean putPendingWrite(PendingWrite msg) {
            return pendingWriteQueue.offer(msg);
        }

        PendingWrite getPendingWrite() {
            return pendingWriteQueue.peek();
        }

        PendingWrite removePendingWrite() {
            return pendingWriteQueue.poll();
        }

        void clearPendingWrites(Throwable cause) {
            for (;;) {
                PendingWrite pendingWrite = pendingWriteQueue.poll();
                if (pendingWrite == null) {
                    break;
                }
                pendingWrite.fail(cause);
            }
        }
    }

    private final class PriorityComparator implements Comparator<Integer> {
        @Override
        public int compare(Integer id1, Integer id2) {
            StreamState state1 = activeStreams.get(id1);
            StreamState state2 = activeStreams.get(id2);
            return state1.getPriority() - state2.getPriority();
        }
    }

    public static final class PendingWrite {
        final SpdyDataFrame spdyDataFrame;
        final ChannelPromise promise;

        PendingWrite(SpdyDataFrame spdyDataFrame, ChannelPromise promise) {
            this.spdyDataFrame = spdyDataFrame;
            this.promise = promise;
        }

        void fail(Throwable cause) {
            spdyDataFrame.release();
            promise.setFailure(cause);
        }
    }
}

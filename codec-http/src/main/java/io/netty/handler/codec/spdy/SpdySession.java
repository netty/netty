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

import java.io.Serializable;
import java.util.Comparator;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.handler.codec.spdy.SpdyCodecUtil.*;

final class SpdySession {

    private final AtomicInteger activeLocalStreams  = new AtomicInteger();
    private final AtomicInteger activeRemoteStreams = new AtomicInteger();
    private final Map<Integer, StreamState> activeStreams = PlatformDependent.newConcurrentHashMap();
    private final StreamComparator streamComparator = new StreamComparator();
    private final AtomicInteger sendWindowSize;
    private final AtomicInteger receiveWindowSize;

    SpdySession(int sendWindowSize, int receiveWindowSize) {
        this.sendWindowSize = new AtomicInteger(sendWindowSize);
        this.receiveWindowSize = new AtomicInteger(receiveWindowSize);
    }

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
    Map<Integer, StreamState> activeStreams() {
        Map<Integer, StreamState> streams = new TreeMap<Integer, StreamState>(streamComparator);
        streams.putAll(activeStreams);
        return streams;
    }

    void acceptStream(
            int streamId, byte priority, boolean remoteSideClosed, boolean localSideClosed,
            int sendWindowSize, int receiveWindowSize, boolean remote) {
        if (!remoteSideClosed || !localSideClosed) {
            StreamState state = activeStreams.put(streamId, new StreamState(
                    priority, remoteSideClosed, localSideClosed, sendWindowSize, receiveWindowSize));
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

    void removeStream(int streamId, Throwable cause, boolean remote) {
        StreamState state = removeActiveStream(streamId, remote);
        if (state != null) {
            state.clearPendingWrites(cause);
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
        if (streamId == SPDY_SESSION_STREAM_ID) {
            return sendWindowSize.get();
        }

        StreamState state = activeStreams.get(streamId);
        return state != null ? state.getSendWindowSize() : -1;
    }

    int updateSendWindowSize(int streamId, int deltaWindowSize) {
        if (streamId == SPDY_SESSION_STREAM_ID) {
            return sendWindowSize.addAndGet(deltaWindowSize);
        }

        StreamState state = activeStreams.get(streamId);
        return state != null ? state.updateSendWindowSize(deltaWindowSize) : -1;
    }

    int updateReceiveWindowSize(int streamId, int deltaWindowSize) {
        if (streamId == SPDY_SESSION_STREAM_ID) {
            return receiveWindowSize.addAndGet(deltaWindowSize);
        }

        StreamState state = activeStreams.get(streamId);
        if (state == null) {
            return -1;
        }
        if (deltaWindowSize > 0) {
            state.setReceiveWindowSizeLowerBound(0);
        }
        return state.updateReceiveWindowSize(deltaWindowSize);
    }

    int getReceiveWindowSizeLowerBound(int streamId) {
        if (streamId == SPDY_SESSION_STREAM_ID) {
            return 0;
        }

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
        if (streamId == SPDY_SESSION_STREAM_ID) {
            for (Map.Entry<Integer, StreamState> e: activeStreams().entrySet()) {
                StreamState state = e.getValue();
                if (state.getSendWindowSize() > 0) {
                    PendingWrite pendingWrite = state.getPendingWrite();
                    if (pendingWrite != null) {
                        return pendingWrite;
                    }
                }
            }
            return null;
        }

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

    private final class StreamComparator implements Comparator<Integer> {

        StreamComparator() { }

        @Override
        public int compare(Integer id1, Integer id2) {
            StreamState state1 = activeStreams.get(id1);
            StreamState state2 = activeStreams.get(id2);

            int result = state1.getPriority() - state2.getPriority();
            if (result != 0) {
                return result;
            }

            return id1 - id2;
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

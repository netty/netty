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

import java.util.Comparator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

final class SpdySession {

    private final Map<Integer, StreamState> activeStreams =
        new ConcurrentHashMap<Integer, StreamState>();

    int numActiveStreams() {
        return activeStreams.size();
    }

    boolean noActiveStreams() {
        return activeStreams.isEmpty();
    }

    boolean isActiveStream(int streamID) {
        return activeStreams.containsKey(new Integer(streamID));
    }

    // Stream-IDs should be iterated in priority order
    Set<Integer> getActiveStreams() {
        TreeSet<Integer> StreamIDs = new TreeSet<Integer>(new PriorityComparator());
        StreamIDs.addAll(activeStreams.keySet());
        return StreamIDs;
    }

    void acceptStream(
            int streamID, byte priority, boolean remoteSideClosed, boolean localSideClosed,
            int sendWindowSize, int receiveWindowSize) {
        if (!remoteSideClosed || !localSideClosed) {
            activeStreams.put(
                    new Integer(streamID),
                    new StreamState(priority, remoteSideClosed, localSideClosed, sendWindowSize, receiveWindowSize));
        }
    }

    boolean removeStream(int streamID) {
        Integer StreamID = new Integer(streamID);
        StreamState state = activeStreams.get(StreamID);
        activeStreams.remove(StreamID);
        if (state != null) {
            return state.clearPendingWrites();
        } else {
            return false;
        }
    }

    boolean isRemoteSideClosed(int streamID) {
        StreamState state = activeStreams.get(new Integer(streamID));
        return state == null || state.isRemoteSideClosed();
    }

    void closeRemoteSide(int streamID) {
        Integer StreamID = new Integer(streamID);
        StreamState state = activeStreams.get(StreamID);
        if (state != null) {
            state.closeRemoteSide();
            if (state.isLocalSideClosed()) {
                activeStreams.remove(StreamID);
            }
        }
    }

    boolean isLocalSideClosed(int streamID) {
        StreamState state = activeStreams.get(new Integer(streamID));
        return state == null || state.isLocalSideClosed();
    }

    void closeLocalSide(int streamID) {
        Integer StreamID = new Integer(streamID);
        StreamState state = activeStreams.get(StreamID);
        if (state != null) {
            state.closeLocalSide();
            if (state.isRemoteSideClosed()) {
                activeStreams.remove(StreamID);
            }
        }
    }

    /*
     * hasReceivedReply and receivedReply are only called from messageReceived
     * no need to synchronize access to the StreamState
     */
    boolean hasReceivedReply(int streamID) {
        StreamState state = activeStreams.get(new Integer(streamID));
        return state != null && state.hasReceivedReply();
    }

    void receivedReply(int streamID) {
        StreamState state = activeStreams.get(new Integer(streamID));
        if (state != null) {
            state.receivedReply();
        }
    }

    int getSendWindowSize(int streamID) {
        StreamState state = activeStreams.get(new Integer(streamID));
        return state != null ? state.getSendWindowSize() : -1;
    }

    int updateSendWindowSize(int streamID, int deltaWindowSize) {
        StreamState state = activeStreams.get(new Integer(streamID));
        return state != null ? state.updateSendWindowSize(deltaWindowSize) : -1;
    }

    int updateReceiveWindowSize(int streamID, int deltaWindowSize) {
        StreamState state = activeStreams.get(new Integer(streamID));
        if (deltaWindowSize > 0) {
            state.setReceiveWindowSizeLowerBound(0);
        }
        return state != null ? state.updateReceiveWindowSize(deltaWindowSize) : -1;
    }

    int getReceiveWindowSizeLowerBound(int streamID) {
        StreamState state = activeStreams.get(new Integer(streamID));
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

    boolean putPendingWrite(int streamID, Object msg) {
        StreamState state = activeStreams.get(Integer.valueOf(streamID));
        return state != null && state.putPendingWrite(msg);
    }

    Object getPendingWrite(int streamID) {
        StreamState state = activeStreams.get(Integer.valueOf(streamID));
        return state != null ? state.getPendingWrite() : null;
    }

    Object removePendingWrite(int streamID) {
        StreamState state = activeStreams.get(Integer.valueOf(streamID));
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
        private final Queue<Object> pendingWriteQueue = new ConcurrentLinkedQueue<Object>();

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

        boolean putPendingWrite(Object msg) {
            return pendingWriteQueue.offer(msg);
        }

        Object getPendingWrite() {
            return pendingWriteQueue.peek();
        }

        Object removePendingWrite() {
            return pendingWriteQueue.poll();
        }

        boolean clearPendingWrites() {
            if (pendingWriteQueue.isEmpty()) {
                return false;
            }
            pendingWriteQueue.clear();
            return true;
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
}

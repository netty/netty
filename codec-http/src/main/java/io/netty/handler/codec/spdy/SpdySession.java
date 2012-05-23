/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.spdy;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.MessageEvent;

final class SpdySession {

    private static final SpdyProtocolException STREAM_CLOSED = new SpdyProtocolException("Stream closed");

    private final Map<Integer, StreamState> activeStreams =
        new ConcurrentHashMap<Integer, StreamState>();

    SpdySession() {
    }

    public int numActiveStreams() {
        return activeStreams.size();
    }

    public boolean noActiveStreams() {
        return activeStreams.isEmpty();
    }

    public boolean isActiveStream(int streamID) {
        return activeStreams.containsKey(new Integer(streamID));
    }

    // Stream-IDs should be iterated in priority order
    public Set<Integer> getActiveStreams() {
        TreeSet<Integer> StreamIDs = new TreeSet<Integer>(new PriorityComparator());
        StreamIDs.addAll(activeStreams.keySet());
        return StreamIDs;
    }

    public void acceptStream(
            int streamID, byte priority, boolean remoteSideClosed, boolean localSideClosed,
            int sendWindowSize, int receiveWindowSize) {
        if (!remoteSideClosed || !localSideClosed) {
            activeStreams.put(
                    new Integer(streamID),
                    new StreamState(priority, remoteSideClosed, localSideClosed, sendWindowSize, receiveWindowSize));
        }
        return;
    }

    public void removeStream(int streamID) {
        Integer StreamID = new Integer(streamID);
        StreamState state = activeStreams.get(StreamID);
        activeStreams.remove(StreamID);
        if (state != null) {
            MessageEvent e = state.removePendingWrite();
            while (e != null) {
                e.getFuture().setFailure(STREAM_CLOSED);
                e = state.removePendingWrite();
            }
        }
    }

    public boolean isRemoteSideClosed(int streamID) {
        StreamState state = activeStreams.get(new Integer(streamID));
        return (state == null) || state.isRemoteSideClosed();
    }

    public void closeRemoteSide(int streamID) {
        Integer StreamID = new Integer(streamID);
        StreamState state = activeStreams.get(StreamID);
        if (state != null) {
            state.closeRemoteSide();
            if (state.isLocalSideClosed()) {
                activeStreams.remove(StreamID);
            }
        }
    }

    public boolean isLocalSideClosed(int streamID) {
        StreamState state = activeStreams.get(new Integer(streamID));
        return (state == null) || state.isLocalSideClosed();
    }

    public void closeLocalSide(int streamID) {
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

    public boolean hasReceivedReply(int streamID) {
        StreamState state = activeStreams.get(new Integer(streamID));
        return (state != null) && state.hasReceivedReply();
    }

    public void receivedReply(int streamID) {
        StreamState state = activeStreams.get(new Integer(streamID));
        if (state != null) {
            state.receivedReply();
        }
    }

    public int getSendWindowSize(int streamID) {
        StreamState state = activeStreams.get(new Integer(streamID));
        return state != null ? state.getSendWindowSize() : -1;
    }

    public int updateSendWindowSize(int streamID, int deltaWindowSize) {
        StreamState state = activeStreams.get(new Integer(streamID));
        return state != null ? state.updateSendWindowSize(deltaWindowSize) : -1;
    }

    public int updateReceiveWindowSize(int streamID, int deltaWindowSize) {
        StreamState state = activeStreams.get(new Integer(streamID));
        if (deltaWindowSize > 0) {
            state.setReceiveWindowSizeLowerBound(0);
        }
        return state != null ? state.updateReceiveWindowSize(deltaWindowSize) : -1;
    }

    public int getReceiveWindowSizeLowerBound(int streamID) {
        StreamState state = activeStreams.get(new Integer(streamID));
        return state != null ? state.getReceiveWindowSizeLowerBound() : 0;
    }

    public void updateAllReceiveWindowSizes(int deltaWindowSize) {
        for (StreamState state: activeStreams.values()) {
            state.updateReceiveWindowSize(deltaWindowSize);
            if (deltaWindowSize < 0) {
                state.setReceiveWindowSizeLowerBound(deltaWindowSize);
            }
        }
    }

    public boolean putPendingWrite(int streamID, MessageEvent evt) {
        StreamState state = activeStreams.get(new Integer(streamID));
        return state != null && state.putPendingWrite(evt);
    }

    public MessageEvent getPendingWrite(int streamID) {
        StreamState state = activeStreams.get(new Integer(streamID));
        return state != null ? state.getPendingWrite() : null;
    }

    public MessageEvent removePendingWrite(int streamID) {
        StreamState state = activeStreams.get(new Integer(streamID));
        return state != null ? state.removePendingWrite() : null;
    }

    private static final class StreamState {

        private final byte priority;
        private volatile boolean remoteSideClosed;
        private volatile boolean localSideClosed;
        private boolean receivedReply;
        private AtomicInteger sendWindowSize;
        private AtomicInteger receiveWindowSize;
        private volatile int receiveWindowSizeLowerBound;
        private ConcurrentLinkedQueue<MessageEvent> pendingWriteQueue =
                new ConcurrentLinkedQueue<MessageEvent>();

        public StreamState(
                byte priority, boolean remoteSideClosed, boolean localSideClosed,
                int sendWindowSize, int receiveWindowSize) {
            this.priority = priority;
            this.remoteSideClosed = remoteSideClosed;
            this.localSideClosed = localSideClosed;
            this.sendWindowSize = new AtomicInteger(sendWindowSize);
            this.receiveWindowSize = new AtomicInteger(receiveWindowSize);
        }

        public byte getPriority() {
            return priority;
        }

        public boolean isRemoteSideClosed() {
            return remoteSideClosed;
        }

        public void closeRemoteSide() {
            remoteSideClosed = true;
        }

        public boolean isLocalSideClosed() {
            return localSideClosed;
        }

        public void closeLocalSide() {
            localSideClosed = true;
        }

        public boolean hasReceivedReply() {
            return receivedReply;
        }

        public void receivedReply() {
            receivedReply = true;
        }

        public int getSendWindowSize() {
            return sendWindowSize.get();
        }

        public int updateSendWindowSize(int deltaWindowSize) {
            return sendWindowSize.addAndGet(deltaWindowSize);
        }

        public int updateReceiveWindowSize(int deltaWindowSize) {
            return receiveWindowSize.addAndGet(deltaWindowSize);
        }

        public int getReceiveWindowSizeLowerBound() {
            return receiveWindowSizeLowerBound;
        }

        public void setReceiveWindowSizeLowerBound(int receiveWindowSizeLowerBound) {
            this.receiveWindowSizeLowerBound = receiveWindowSizeLowerBound;
        }

        public boolean putPendingWrite(MessageEvent evt) {
            return pendingWriteQueue.offer(evt);
        }

        public MessageEvent getPendingWrite() {
            return pendingWriteQueue.peek();
        }

        public MessageEvent removePendingWrite() {
            return pendingWriteQueue.poll();
        }
    }

    private final class PriorityComparator implements Comparator<Integer> {

        public PriorityComparator() {
            super();
        }

        public int compare(Integer id1, Integer id2) {
            StreamState state1 = activeStreams.get(id1);
            StreamState state2 = activeStreams.get(id2);
            return state1.getPriority() - state2.getPriority();
        }
    }
}

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
package org.jboss.netty.handler.codec.spdy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class SpdySession {

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

    public void acceptStream(int streamID, boolean remoteSideClosed, boolean localSideClosed) {
        if (!remoteSideClosed || !localSideClosed) {
            activeStreams.put(new Integer(streamID),
                              new StreamState(remoteSideClosed, localSideClosed));
        }
    }

    public void removeStream(int streamID) {
        activeStreams.remove(new Integer(streamID));
    }

    public boolean isRemoteSideClosed(int streamID) {
        StreamState state = activeStreams.get(new Integer(streamID));
        return state == null || state.isRemoteSideClosed();
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
        return state == null || state.isLocalSideClosed();
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

    public boolean hasReceivedReply(int streamID) {
        StreamState state = activeStreams.get(new Integer(streamID));
        return state != null && state.hasReceivedReply();
    }

    public void receivedReply(int streamID) {
        StreamState state = activeStreams.get(new Integer(streamID));
        if (state != null) {
            state.receivedReply();
        }
    }

    private static final class StreamState {

        private boolean remoteSideClosed;
        private boolean localSideClosed;
        private boolean receivedReply;

        public StreamState(boolean remoteSideClosed, boolean localSideClosed) {
            this.remoteSideClosed = remoteSideClosed;
            this.localSideClosed = localSideClosed;
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
    }
}

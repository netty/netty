/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

/**
 * A single stream within an HTTP2 connection. Streams are compared to each other by priority.
 */
public interface Http2Stream {

    /**
     * The allowed states of an HTTP2 stream.
     */
    enum State {
        IDLE,
        RESERVED_LOCAL,
        RESERVED_REMOTE,
        OPEN,
        HALF_CLOSED_LOCAL,
        HALF_CLOSED_REMOTE,
        CLOSED
    }

    /**
     * Gets the unique identifier for this stream within the connection.
     */
    int id();

    /**
     * Gets the state of this stream.
     */
    State state();

    /**
     * Verifies that the stream is in one of the given allowed states.
     */
    Http2Stream verifyState(Http2Error error, State... allowedStates) throws Http2Exception;

    /**
     * If this is a reserved push stream, opens the stream for push in one direction.
     */
    Http2Stream openForPush() throws Http2Exception;

    /**
     * Closes the stream.
     */
    Http2Stream close();

    /**
     * Closes the local side of this stream. If this makes the stream closed, the child is closed as
     * well.
     */
    Http2Stream closeLocalSide();

    /**
     * Closes the remote side of this stream. If this makes the stream closed, the child is closed
     * as well.
     */
    Http2Stream closeRemoteSide();

    /**
     * Indicates whether the remote side of this stream is open (i.e. the state is either
     * {@link State#OPEN} or {@link State#HALF_CLOSED_LOCAL}).
     */
    boolean remoteSideOpen();

    /**
     * Indicates whether the local side of this stream is open (i.e. the state is either
     * {@link State#OPEN} or {@link State#HALF_CLOSED_REMOTE}).
     */
    boolean localSideOpen();
}

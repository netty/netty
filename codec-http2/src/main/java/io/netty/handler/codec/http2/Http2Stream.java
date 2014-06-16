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

import java.util.Collection;

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

    /**
     * Gets the in-bound flow control state for this stream.
     */
    FlowState inboundFlow();

    /**
     * Sets the in-bound flow control state for this stream.
     */
    void inboundFlow(FlowState state);

    /**
     * Gets the out-bound flow control window for this stream.
     */
    FlowState outboundFlow();

    /**
     * Sets the out-bound flow control window for this stream.
     */
    void outboundFlow(FlowState state);

    /**
     * Updates an priority for this stream. Calling this method may affect the straucture of the
     * priority tree.
     *
     * @param parentStreamId the parent stream that given stream should depend on. May be {@code 0},
     *            if the stream has no dependencies and should be an immediate child of the
     *            connection.
     * @param weight the weight to be assigned to this stream relative to its parent. This value
     *            must be between 1 and 256 (inclusive)
     * @param exclusive indicates that the stream should be the exclusive dependent on its parent.
     *            This only applies if the stream has a parent.
     * @return this stream.
     */
    Http2Stream setPriority(int parentStreamId, short weight, boolean exclusive)
            throws Http2Exception;

    /**
     * Indicates whether or not this stream is the root node of the priority tree.
     */
    boolean isRoot();

    /**
     * Indicates whether or not this is a leaf node (i.e. {@link #numChildren} is 0) of the priority tree.
     */
    boolean isLeaf();

    /**
     * Returns weight assigned to the dependency with the parent. The weight will be a value
     * between 1 and 256.
     */
    short weight();

    /**
     * The total of the weights of all children of this stream.
     */
    int totalChildWeights();

    /**
     * The parent (i.e. the node in the priority tree on which this node depends), or {@code null}
     * if this is the root node (i.e. the connection, itself).
     */
    Http2Stream parent();

    /**
     * Indicates whether or not this stream is a descendant in the priority tree from the given stream.
     */
    boolean isDescendantOf(Http2Stream stream);

    /**
     * Returns the number of child streams directly dependent on this stream.
     */
    int numChildren();

    /**
     * Indicates whether the given stream is a direct child of this stream.
     */
    boolean hasChild(int streamId);

    /**
     * Attempts to find a child of this stream with the given ID. If not found, returns
     * {@code null}.
     */
    Http2Stream child(int streamId);

    /**
     * Gets all streams that are direct dependents on this stream.
     */
    Collection<? extends Http2Stream> children();
}

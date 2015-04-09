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
     * Represents the state which flow controller implementations are expected to track.
     */
    interface FlowControlState {
        /**
         * Get the portion of the flow control window that is available for sending/receiving frames which are subject
         * to flow control. This quantity is measured in number of bytes.
         */
        int windowSize();

        /**
         * Get the initial flow control window size. This quantity is measured in number of bytes.
         * Note the unavailable window portion can be calculated by
         * {@link #initialWindowSize()} - {@link #windowSize()}.
         */
        int initialWindowSize();
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
     * Get the state as related to the {@link Http2LocalFlowController}.
     */
    FlowControlState localFlowState();

    /**
     * Set the state as related to the {@link Http2LocalFlowController}.
     */
    void localFlowState(FlowControlState state);

    /**
     * Get the state as related to {@link Http2RemoteFlowController}.
     */
    FlowControlState remoteFlowState();

    /**
     * Set the state as related to {@link Http2RemoteFlowController}.
     */
    void remoteFlowState(FlowControlState state);

    /**
     * Opens this stream, making it available via {@link Http2Connection#forEachActiveStream(Http2StreamVisitor)} and
     * transition state to:
     * <ul>
     * <li>{@link State#OPEN} if {@link #state()} is {@link State#IDLE} and {@code halfClosed} is {@code false}.</li>
     * <li>{@link State#HALF_CLOSED_LOCAL} if {@link #state()} is {@link State#IDLE} and {@code halfClosed}
     * is {@code true} and the stream is local.</li>
     * <li>{@link State#HALF_CLOSED_REMOTE} if {@link #state()} is {@link State#IDLE} and {@code halfClosed}
     * is {@code true} and the stream is remote.</li>
     * <li>{@link State#RESERVED_LOCAL} if {@link #state()} is {@link State#HALF_CLOSED_REMOTE}.</li>
     * <li>{@link State#RESERVED_REMOTE} if {@link #state()} is {@link State#HALF_CLOSED_LOCAL}.</li>
     * </ul>
     */
    Http2Stream open(boolean halfClosed) throws Http2Exception;

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
     * Indicates whether a {@code RST_STREAM} frame has been sent from the local endpoint for this stream.
     */
    boolean isResetSent();

    /**
     * Sets the flag indicating that a {@code RST_STREAM} frame has been sent from the local endpoint
     * for this stream. This does not affect the stream state.
     */
    Http2Stream resetSent();

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
     * Associates the application-defined data with this stream.
     * @return The value that was previously associated with {@code key}, or {@code null} if there was none.
     */
    Object setProperty(Object key, Object value);

    /**
     * Returns application-defined data if any was associated with this stream.
     */
    <V> V getProperty(Object key);

    /**
     * Returns and removes application-defined data if any was associated with this stream.
     */
    <V> V removeProperty(Object key);

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
    Http2Stream setPriority(int parentStreamId, short weight, boolean exclusive) throws Http2Exception;

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
     * Get the number of streams in the priority tree rooted at this node that are OK to exist in the priority
     * tree on their own right. Some streams may be in the priority tree because their dependents require them to
     * remain.
     */
    int prioritizableForTree();

    /**
     * Indicates whether or not this stream is a descendant in the priority tree from the given stream.
     */
    boolean isDescendantOf(Http2Stream stream);

    /**
     * Returns the number of child streams directly dependent on this stream.
     */
    int numChildren();

    /**
     * Provide a means of iterating over the children of this stream.
     *
     * @param visitor The visitor which will visit each child stream.
     * @return The stream before iteration stopped or {@code null} if iteration went past the end.
     */
    Http2Stream forEachChild(Http2StreamVisitor visitor) throws Http2Exception;
}

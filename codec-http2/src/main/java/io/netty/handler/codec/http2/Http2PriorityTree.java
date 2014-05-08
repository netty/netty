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
 * A tree for maintaining relative priority information among streams.
 */
public interface Http2PriorityTree<T> extends Iterable<Http2PriorityTree.Priority<T>> {

    /**
     * Priority node for a single stream.
     */
    interface Priority<T> {

        /**
         * Indicates whether or not this priority is the root of the tree.
         */
        boolean isRoot();

        /**
         * Indicates whether or not this is a leaf node (i.e. {@link #numChildren} is 0).
         */
        boolean isLeaf();

        /**
         * Returns the subject stream for this priority information.
         */
        int streamId();

        /**
         * Returns optional data associated with this stream.
         */
        T data();

        /**
         * Associates the given data with this priority node.
         */
        Priority<T> data(T data);

        /**
         * Returns weight assigned to the dependency with the parent. The weight will be a value
         * between 1 and 256.
         */
        short weight();

        /**
         * The total of the weights of all children of this node.
         */
        int totalChildWeights();

        /**
         * The parent (i.e. the node on which this node depends), or {@code null} if this is the
         * root node.
         */
        Priority<T> parent();

        /**
         * Indicates whether or not this priority is descended from the given priority.
         */
        boolean isDescendantOf(Priority<T> priority);

        /**
         * Returns the number of children directly dependent on this node.
         */
        int numChildren();

        /**
         * Indicates whether the priority for the given stream is a direct child of this node.
         */
        boolean hasChild(int streamId);

        /**
         * Attempts to find a child of this node for the given stream. If not found, returns
         * {@code null}.
         */
        Priority<T> child(int streamId);

        /**
         * Gets the children nodes that are dependent on this node.
         */
        Collection<? extends Priority<T>> children();
    }

    /**
     * Adds a new priority or updates an existing priority for the given stream, using default
     * priority values.
     *
     * @param streamId the stream to be prioritized
     * @param data optional user-defined data to associate to the stream
     * @return the priority for the stream.
     */
    Priority<T> prioritizeUsingDefaults(int streamId);

    /**
     * Adds a new priority or updates an existing priority for the given stream.
     *
     * @param streamId the stream to be prioritized.
     * @param parent an optional stream that the given stream should depend on. Zero, if no
     *            dependency.
     * @param weight the weight to be assigned to this stream relative to its parent. This value
     *            must be between 1 and 256 (inclusive)
     * @param exclusive indicates that the stream should be the exclusive dependent on its parent.
     *            This only applies if the stream has a parent.
     * @return the priority for the stream.
     */
    Priority<T> prioritize(int streamId, int parent, short weight, boolean exclusive);

    /**
     * Removes the priority information for the given stream. Adjusts other priorities if necessary.
     *
     * @return the data that was associated with the stream or {@code null} if the node was not
     *         found or no data was found in the node.
     */
    T remove(int streamId);

    /**
     * Gets the total number of streams that have been prioritized in the tree (not counting the
     * root node).
     */
    int size();

    /**
     * Returns the root of the tree. The root always exists and represents the connection itself.
     */
    Priority<T> root();

    /**
     * Returns the priority for the given stream, or {@code null} if not available.
     */
    Priority<T> get(int streamId);
}

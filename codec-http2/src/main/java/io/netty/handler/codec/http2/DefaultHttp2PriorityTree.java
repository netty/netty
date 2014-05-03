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

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Default implementation of {@link Http2PriorityTree}.
 */
public class DefaultHttp2PriorityTree<T> implements Http2PriorityTree<T> {

    private final DefaultPriority<T> root = new DefaultPriority<T>(0, (short) 0, null);
    private Map<Integer, Priority<T>> priorityMap = new HashMap<Integer, Priority<T>>();

    @Override
    public Priority<T> get(int streamId) {
        return priorityMap.get(streamId);
    }

    @Override
    public Iterator<Priority<T>> iterator() {
        return Collections.unmodifiableCollection(priorityMap.values()).iterator();
    }

    @Override
    public Priority<T> prioritizeUsingDefaults(int streamId, T data) {
        return prioritize(streamId, 0, DEFAULT_PRIORITY_WEIGHT, false, data);
    }

    @Override
    public Priority<T> prioritize(int streamId, int parent, short weight, boolean exclusive,
            T data) {
        if (streamId <= 0) {
            throw new IllegalArgumentException("Stream ID must be > 0");
        }
        if (streamId == parent) {
            throw new IllegalArgumentException("A stream cannot depend on itself");
        }
        if (parent < 0) {
            throw new IllegalArgumentException("Parent stream ID must be >= 0");
        }
        if (weight < 1 || weight > 256) {
            throw new IllegalArgumentException("Invalid weight: " + weight);
        }

        // Get the parent.
        DefaultPriority<T> newParent = root;
        if (parent > 0) {
            newParent = internalGet(parent);
        }
        if (newParent == null) {
            throw new IllegalArgumentException("Parent priority does not exist: " + parent);
        }

        DefaultPriority<T> priority = internalGet(streamId);
        if (priority == null) {
            // Add a new priority.
            priority = new DefaultPriority<T>(streamId, weight, data);
            newParent.addChild(priority, exclusive);
            priorityMap.put(streamId, priority);
            return priority;
        }

        // Already have a priority. Re-prioritize the stream.
        priority.setWeight(weight);
        priority.setData(data);

        if (newParent == priority.parent() && !exclusive) {
            // No changes were made to the tree structure.
            return priority;
        }

        // Break off the priority branch from it's current parent.
        DefaultPriority<T> oldParent = priority.parent();
        oldParent.removeChildBranch(priority);

        if (newParent.isDescendantOf(priority)) {
            // Adding a circular dependency (priority<->newParent). Break off the new parent's
            // branch and add it above this priority.
            newParent.parent().removeChildBranch(newParent);
            oldParent.addChild(newParent, false);
        }

        // Add the priority under the new parent.
        newParent.addChild(priority, exclusive);
        return priority;
    }

    @Override
    public T remove(int streamId) {
        if (streamId <= 0) {
            throw new IllegalArgumentException("Stream ID must be > 0");
        }

        // Remove the priority from the map.
        DefaultPriority<T> priority = internalGet(streamId);
        if (priority != null) {
            // Remove it from the tree as well.
            priority.parent().removeChild(priority);
            return priority.data();
        }
        return null;
    }

    @Override
    public int size() {
        return priorityMap.size();
    }

    @Override
    public Priority<T> root() {
        return root;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(getClass().getSimpleName()).append("[");
        Deque<DefaultPriority<T>> nodes = new ArrayDeque<DefaultPriority<T>>();
        nodes.addLast(root);
        while (!nodes.isEmpty()) {
            DefaultPriority<T> p = nodes.pop();
            builder.append(p.streamId()).append("->")
                    .append(p.parent() == null ? "null" : p.parent().streamId).append(", ");
            for (DefaultPriority<T> child : p.children) {
                nodes.addLast(child);
            }
        }
        builder.append("]");
        return builder.toString();
    }

    private DefaultPriority<T> internalGet(int streamId) {
        return (DefaultPriority<T>) priorityMap.get(streamId);
    }

    /**
     * Internal implementation of {@link Priority}.
     */
    private static final class DefaultPriority<T> implements Priority<T> {
        private final int streamId;
        private short weight;
        private T data;
        private Set<DefaultPriority<T>> children = newChildSet();

        private DefaultPriority<T> parent;
        private int totalChildWeights;

        DefaultPriority(int streamId, short weight, T data) {
            this.streamId = streamId;
            this.weight = weight;
            this.data = data;
        }

        @Override
        public boolean isRoot() {
            return parent == null;
        }

        @Override
        public int streamId() {
            return streamId;
        }

        @Override
        public short weight() {
            return weight;
        }

        @Override
        public T data() {
            return data;
        }

        @Override
        public int totalChildWeights() {
            return totalChildWeights;
        }

        @Override
        public DefaultPriority<T> parent() {
            return parent;
        }

        @Override
        public boolean isDescendantOf(Priority<T> priority) {
            Priority<T> next = parent;
            while (next != null) {
                if (next == priority) {
                    return true;
                }
                next = next.parent();
            }
            return false;
        }

        @Override
        public boolean isLeaf() {
            return numChildren() == 0;
        }

        @Override
        public int numChildren() {
            return children.size();
        }

        @Override
        public Set<? extends Priority<T>> children() {
            return Collections.unmodifiableSet(children);
        }

        @Override
        public boolean hasChild(int streamId) {
            return getChild(streamId) != null;
        }

        @Override
        public Priority<T> getChild(int streamId) {
            for (DefaultPriority<T> child : children) {
                if (child.streamId() == streamId) {
                    return child;
                }
            }
            return null;
        }

        void setWeight(short weight) {
            if (parent != null && weight != this.weight) {
                int delta = weight - this.weight;
                parent.totalChildWeights += delta;
            }
            this.weight = weight;
        }

        void setData(T data) {
            this.data = data;
        }

        Set<DefaultPriority<T>> removeAllChildren() {
            if (children.isEmpty()) {
                return Collections.emptySet();
            }

            totalChildWeights = 0;
            Set<DefaultPriority<T>> prevChildren = children;
            children = newChildSet();
            return prevChildren;
        }

        /**
         * Adds a child to this priority. If exclusive is set, any children of this node are moved
         * to being dependent on the child.
         */
        void addChild(DefaultPriority<T> child, boolean exclusive) {
            if (exclusive) {
                // If it was requested that this child be the exclusive dependency of this node,
                // move any previous children to the child node, becoming grand children
                // of this node.
                for (DefaultPriority<T> grandchild : removeAllChildren()) {
                    child.addChild(grandchild, false);
                }
            }

            child.parent = this;
            if (children.add(child)) {
                totalChildWeights += child.weight();
            }
        }

        /**
         * Removes the child priority and moves any of its dependencies to being direct dependencies
         * on this node.
         */
        void removeChild(DefaultPriority<T> child) {
            if (children.remove(child)) {
                child.parent = null;
                totalChildWeights -= child.weight();

                // Move up any grand children to be directly dependent on this node.
                for (DefaultPriority<T> grandchild : child.children) {
                    addChild(grandchild, false);
                }
            }
        }

        /**
         * Removes the child priority but unlike {@link #removeChild}, leaves its branch unaffected.
         */
        void removeChildBranch(DefaultPriority<T> child) {
            if (children.remove(child)) {
                child.parent = null;
                totalChildWeights -= child.weight();
            }
        }

        private static <T> Set<DefaultPriority<T>> newChildSet() {
            return new LinkedHashSet<DefaultPriority<T>>(2);
        }
    }
}

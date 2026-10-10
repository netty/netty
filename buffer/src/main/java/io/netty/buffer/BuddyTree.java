/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.buffer;

import java.util.Arrays;

/**
 * The buddy tree of a chunk: hands out power-of-two blocks of at least a minimum block size from a capacity that is
 * a power-of-two multiple of it, and merges freed buddies back. Not thread-safe: used by the chunk's magazine only
 * (frees from other threads reach it through the chunk's free list).
 * <p>
 * An implicit binary tree over the blocks: node 1 is the whole chunk, the children of node {@code i} are its two
 * halves {@code 2i} and {@code 2i + 1}, and the leaves are the minimum blocks. Each node stores the order of the
 * largest free block in its subtree plus one, 0 when nothing in it is free (order {@code k} is a block of
 * {@code minBlockSize << k}). A claim follows the leftmost child that fits down to the order asked for, and a
 * release walks up merging buddies: both are one pass along a root-to-leaf path.
 */
final class BuddyTree {
    private final int minBlockSize;
    private final byte[] nodes;
    private final int maxOrder;

    /**
     * @param capacity     the chunk size, a power-of-two multiple of {@code minBlockSize}
     * @param minBlockSize the smallest block handed out, a power of two
     */
    BuddyTree(int capacity, int minBlockSize) {
        assert minBlockSize > 0 && (minBlockSize & minBlockSize - 1) == 0 : "minBlockSize " + minBlockSize;
        this.minBlockSize = minBlockSize;
        int leaves = capacity / minBlockSize;
        assert leaves > 0 && (leaves & leaves - 1) == 0 : "capacity " + capacity;
        maxOrder = Integer.numberOfTrailingZeros(leaves);
        byte[] nodes = new byte[leaves << 1];
        // All free: the nodes at depth d, [2^d, 2^(d+1)), are whole blocks of order maxOrder - d,
        // one constant per level.
        for (int depth = 0; depth <= maxOrder; depth++) {
            Arrays.fill(nodes, 1 << depth, 2 << depth, (byte) (maxOrder - depth + 1));
        }
        this.nodes = nodes;
    }

    /**
     * The order of the largest free block, or -1 when no block is free: exact, it is the root's value.
     */
    int largestFreeOrder() {
        return nodes[1] - 1;
    }

    /**
     * Whether no block is claimed.
     */
    boolean isWhollyFree() {
        return nodes[1] == maxOrder + 1;
    }

    /**
     * Claim the leftmost free block of {@code size} and return its offset, or -1 if there is none. Blocks are
     * powers of two of at least the minimum block size: any other size is never claimed, and returns -1.
     */
    int claim(int size) {
        if (size < minBlockSize || (size & size - 1) != 0) {
            return -1;
        }
        int order = Integer.numberOfTrailingZeros(size / minBlockSize);
        byte[] nodes = this.nodes;
        int wanted = order + 1;
        if (order > maxOrder || nodes[1] < wanted) {
            return -1;
        }
        int index = 1;
        for (int depth = maxOrder - order; depth > 0; depth--) {
            index <<= 1;
            if (nodes[index] < wanted) {
                index++;
            }
        }
        nodes[index] = 0;
        updateAncestors(index, order);
        return (index - (1 << maxOrder - order)) * size;
    }

    /**
     * Give back the block of {@code size} at {@code offset}, claimed earlier.
     */
    void release(int offset, int size) {
        int order = Integer.numberOfTrailingZeros(size / minBlockSize);
        if ((offset & size - 1) != 0) {
            throw new IllegalStateException("No block of size " + size + " at offset " + offset);
        }
        int index = (1 << maxOrder - order) + offset / size;
        assert nodes[index] == 0 : "no block of size " + size + " claimed at offset " + offset;
        nodes[index] = (byte) (order + 1);
        updateAncestors(index, order);
    }

    /**
     * Recompute the ancestors of {@code index}, a node of {@code order}, after its value changed; stops at the
     * first ancestor whose value stays the same.
     */
    private void updateAncestors(int index, int order) {
        byte[] nodes = this.nodes;
        while (index > 1) {
            index >>= 1;
            order++;
            int left = nodes[index << 1];
            int right = nodes[(index << 1) + 1];
            // Two whole free halves (each of this node's order minus one, stored plus one) merge into one block.
            int value = left == order && right == order ? order + 1 : Math.max(left, right);
            if (nodes[index] == value) {
                return;
            }
            nodes[index] = (byte) value;
        }
    }
}

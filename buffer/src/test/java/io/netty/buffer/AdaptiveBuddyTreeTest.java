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

import io.netty.buffer.AdaptivePoolingAllocator.BuddyTree;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import static io.netty.buffer.AdaptivePoolingAllocator.BuddyTree.MIN_BLOCK_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AdaptiveBuddyTreeTest {

    /**
     * Random claims and releases on a {@link BuddyTree} hand out the same offsets as the original search, a
     * depth-first walk over claimed / has-claimed-children flags ({@link ReferenceTree}), for every chunk capacity,
     * including requests for sizes that are not block sizes.
     */
    @RepeatedTest(200)
    void claimsTheSameOffsetsAsTheOriginalSearch(RepetitionInfo info) {
        SplittableRandom rng = new SplittableRandom(info.getCurrentRepetition());
        int maxOrder = rng.nextInt(9); // chunks of 32 KiB .. 8 MiB
        int capacity = MIN_BLOCK_SIZE << maxOrder;
        BuddyTree tree = new BuddyTree(capacity);
        ReferenceTree reference = new ReferenceTree(capacity);
        List<int[]> claimed = new ArrayList<int[]>();
        for (int op = 0; op < 2000; op++) {
            if (claimed.isEmpty() || rng.nextInt(10) < 6) {
                // Mostly block sizes that fit, sometimes one larger than the chunk, sometimes a size that is not a
                // block size at all (a remaining capacity: a sum of free blocks, or less than the smallest block).
                int size;
                int kind = rng.nextInt(10);
                if (kind < 7) {
                    size = MIN_BLOCK_SIZE << rng.nextInt(maxOrder + 2);
                } else if (kind < 9) {
                    size = MIN_BLOCK_SIZE * (1 + rng.nextInt(2 << maxOrder));
                } else {
                    size = 1 + rng.nextInt(MIN_BLOCK_SIZE);
                }
                int offset = tree.claim(size);
                assertEquals(reference.claim(size), offset, "op " + op + ", size " + size);
                if (offset != -1) {
                    claimed.add(new int[] {offset, size});
                }
            } else {
                int[] block = claimed.remove(rng.nextInt(claimed.size()));
                tree.release(block[0], block[1]);
                reference.release(block[0], block[1]);
            }
        }
        for (int[] block : claimed) {
            tree.release(block[0], block[1]);
        }
        // Everything merged back: the whole chunk is one free block again.
        assertEquals(0, tree.claim(capacity));
    }

    /**
     * Enough trees for the JIT to compile the constructor: a new tree is one free block, whatever compiled it.
     * JDK 21's C2 once built corrupt trees here (a fill loop storing {@code numberOfLeadingZeros} into the byte
     * array, vectorized by SuperWord), after about two thousand constructions.
     */
    @Test
    void newTreesAreWhollyFreeAfterTheConstructorIsCompiled() {
        int capacity = 8 * 1024 * 1024;
        for (int i = 0; i < 50000; i++) {
            BuddyTree tree = new BuddyTree(capacity);
            assertEquals(0, tree.claim(MIN_BLOCK_SIZE), "tree " + i);
            assertEquals(capacity / 2, tree.claim(capacity / 2), "tree " + i);
        }
    }

    @Test
    void freedBuddiesMergeBackIntoTheWholeChunk() {
        int capacity = 8 * 1024 * 1024;
        BuddyTree tree = new BuddyTree(capacity);
        int blocks = capacity / MIN_BLOCK_SIZE;
        for (int i = 0; i < blocks; i++) {
            assertEquals(i * MIN_BLOCK_SIZE, tree.claim(MIN_BLOCK_SIZE));
        }
        assertEquals(-1, tree.claim(MIN_BLOCK_SIZE));
        // Free every other block: nothing merges, nothing larger than the minimum fits.
        for (int i = 0; i < blocks; i += 2) {
            tree.release(i * MIN_BLOCK_SIZE, MIN_BLOCK_SIZE);
        }
        assertEquals(-1, tree.claim(2 * MIN_BLOCK_SIZE));
        for (int i = 1; i < blocks; i += 2) {
            tree.release(i * MIN_BLOCK_SIZE, MIN_BLOCK_SIZE);
        }
        assertEquals(0, tree.claim(capacity));
        assertEquals(-1, tree.claim(MIN_BLOCK_SIZE));
    }

    /**
     * The search {@link BuddyTree} replaced, kept as the model the tests compare against: each node holds a
     * claimed flag, a has-claimed-children flag and its size shift, and a claim is a recursive depth-first search.
     */
    private static final class ReferenceTree {
        private static final byte IS_CLAIMED = (byte) (1 << 7);
        private static final byte HAS_CLAIMED_CHILDREN = 1 << 6;
        private static final byte SHIFT_MASK = ~(IS_CLAIMED | HAS_CLAIMED_CHILDREN);

        private final byte[] buddies;

        ReferenceTree(int capacity) {
            int leaves = capacity / MIN_BLOCK_SIZE;
            int maxShift = Integer.numberOfTrailingZeros(leaves);
            buddies = new byte[leaves << 1];
            int index = 1;
            int runLength = 1;
            int currentRun = 0;
            while (maxShift > 0) {
                buddies[index++] = (byte) maxShift;
                if (++currentRun == runLength) {
                    currentRun = 0;
                    runLength <<= 1;
                    maxShift--;
                }
            }
        }

        int claim(int size) {
            return chooseFirstFreeBuddy(1, size, 0);
        }

        void release(int offset, int size) {
            unreserveMatchingBuddy(1, size, offset, 0);
        }

        private int chooseFirstFreeBuddy(int index, int size, int currOffset) {
            while (index < buddies.length) {
                byte buddy = buddies[index];
                int currValue = MIN_BLOCK_SIZE << (buddy & SHIFT_MASK);
                if (currValue < size || (buddy & IS_CLAIMED) == IS_CLAIMED) {
                    return -1;
                }
                if (currValue == size && (buddy & HAS_CLAIMED_CHILDREN) == 0) {
                    buddies[index] |= IS_CLAIMED;
                    return currOffset;
                }
                int found = chooseFirstFreeBuddy(index << 1, size, currOffset);
                if (found != -1) {
                    buddies[index] |= HAS_CLAIMED_CHILDREN;
                    return found;
                }
                index = (index << 1) + 1;
                currOffset += currValue >> 1;
            }
            return -1;
        }

        private boolean unreserveMatchingBuddy(int index, int size, int offset, int currOffset) {
            if (buddies.length <= index) {
                return false;
            }
            byte buddy = buddies[index];
            int currSize = MIN_BLOCK_SIZE << (buddy & SHIFT_MASK);
            if (currSize == size) {
                if (currOffset == offset) {
                    buddies[index] &= SHIFT_MASK;
                    return false;
                }
                throw new IllegalStateException("no block of size " + size + " at offset " + offset);
            }
            boolean claims;
            int siblingIndex;
            if (offset < currOffset + (currSize >> 1)) {
                claims = unreserveMatchingBuddy(index << 1, size, offset, currOffset);
                siblingIndex = (index << 1) + 1;
            } else {
                claims = unreserveMatchingBuddy((index << 1) + 1, size, offset, currOffset + (currSize >> 1));
                siblingIndex = index << 1;
            }
            if (!claims) {
                byte sibling = buddies[siblingIndex];
                if ((sibling & SHIFT_MASK) == sibling) {
                    buddies[index] &= SHIFT_MASK;
                    return false;
                }
            }
            return true;
        }
    }
}

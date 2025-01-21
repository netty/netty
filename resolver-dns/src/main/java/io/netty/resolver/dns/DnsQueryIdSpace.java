/*
 * Copyright 2024 The Netty Project
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
package io.netty.resolver.dns;

import io.netty.util.internal.MathUtil;
import io.netty.util.internal.PlatformDependent;

import java.util.Random;

/**
 * Special data-structure that will allow to retrieve the next query id to use, while still guarantee some sort
 * of randomness.
 * The query id will be between 0 (inclusive) and 65535 (inclusive) as defined by the RFC.
 */
final class DnsQueryIdSpace {
    private static final int MAX_ID = 65535;
    private static final int BUCKETS = 4;
    // Each bucket is 16kb of size.
    private static final int BUCKET_SIZE = (MAX_ID + 1) / BUCKETS;

    // If there are other buckets left that have at least 500 usable ids we will drop an unused bucket.
    private static final int BUCKET_DROP_THRESHOLD = 500;
    private final DnsQueryIdRange[] idBuckets = new DnsQueryIdRange[BUCKETS];

    DnsQueryIdSpace() {
        assert idBuckets.length == MathUtil.findNextPositivePowerOfTwo(idBuckets.length);
        // We start with 1 bucket.
        idBuckets[0] = newBucket(0);
    }

    private static DnsQueryIdRange newBucket(int idBucketsIdx) {
        return new DnsQueryIdRange(BUCKET_SIZE, idBucketsIdx * BUCKET_SIZE);
    }

    /**
     * Returns the next ID to use for a query or {@code -1} if there is none left to use.
     *
     * @return next id to use.
     */
    int nextId() {
        int freeIdx = -1;
        for (int bucketIdx = 0; bucketIdx < idBuckets.length; bucketIdx++) {
            DnsQueryIdRange bucket = idBuckets[bucketIdx];
            if (bucket != null) {
                int id = bucket.nextId();
                if (id != -1) {
                    return id;
                }
            } else if (freeIdx == -1 ||
                    // Let's make it somehow random which free slot is used.
                    PlatformDependent.threadLocalRandom().nextBoolean()) {
                // We have a slot that we can use to create a new bucket if we need to.
                freeIdx = bucketIdx;
            }
        }
        if (freeIdx == -1) {
            // No ids left and no slot left to create a new bucket.
            return -1;
        }

        // We still have some slots free to store a new bucket. Let's do this now and use it to generate the next id.
        DnsQueryIdRange bucket = newBucket(freeIdx);
        idBuckets[freeIdx] = bucket;
        int id = bucket.nextId();
        assert id >= 0;
        return id;
    }

    /**
     * Push back the id, so it can be used again for the next query.
     *
     * @param id the id.
     */
    void pushId(int id) {
        int bucketIdx = id / BUCKET_SIZE;
        if (bucketIdx >= idBuckets.length) {
            throw new IllegalArgumentException("id too large: " + id);
        }
        DnsQueryIdRange bucket = idBuckets[bucketIdx];
        assert bucket != null;
        bucket.pushId(id);

        if (bucket.usableIds() == bucket.maxUsableIds()) {
            // All ids are usable in this bucket. Let's check if there are other buckets left that have still
            // some space left and if so drop this bucket.
            for (int idx = 0; idx < idBuckets.length; idx++) {
                if (idx != bucketIdx) {
                    DnsQueryIdRange otherBucket = idBuckets[idx];
                    if (otherBucket != null && otherBucket.usableIds() > BUCKET_DROP_THRESHOLD) {
                        // Drop bucket on the floor to reduce memory usage, there is another bucket left we can
                        // use that still has enough ids to use.
                        idBuckets[bucketIdx] = null;
                        return;
                    }
                }
            }
        }
    }

    /**
     * Return how much more usable ids are left.
     *
     * @return the number of ids that are left for usage.
     */
    int usableIds() {
        int usableIds = 0;
        for (DnsQueryIdRange bucket: idBuckets) {
            // If there is nothing stored in the index yet we can assume the whole bucket is usable
            usableIds += bucket == null ? BUCKET_SIZE : bucket.usableIds();
        }
        return usableIds;
    }

    /**
     * Return the maximum number of ids that are supported.
     *
     * @return the maximum number of ids.
     */
    int maxUsableIds() {
        return BUCKET_SIZE * idBuckets.length;
    }

    /**
     * Provides a query if from a range of possible ids.
     */
    private static final class DnsQueryIdRange {

        // Holds all possible ids which are stored as unsigned shorts
        private final short[] ids;
        private final int startId;
        private int count;

        DnsQueryIdRange(int bucketSize, int startId) {
            this.ids = new short[bucketSize];
            this.startId = startId;
            for (int v = startId; v < bucketSize + startId; v++) {
                pushId(v);
            }
        }

        /**
         * Returns the next ID to use for a query or {@code -1} if there is none left to use.
         *
         * @return next id to use.
         */
        int nextId() {
            assert count >= 0;
            if (count == 0) {
                return -1;
            }
            short id = ids[count - 1];
            count--;

            return id & 0xFFFF;
        }

        /**
         * Push back the id, so it can be used again for the next query.
         *
         * @param id the id.
         */
        void pushId(int id) {
            if (count == ids.length) {
                throw new IllegalStateException("overflow");
            }
            assert id <= startId + ids.length && id >= startId;
            // pick a slot for our index, and whatever was in that slot before will get moved to the tail.
            Random random = PlatformDependent.threadLocalRandom();
            int insertionPosition = random.nextInt(count + 1);
            short moveId = ids[insertionPosition];
            short insertId = (short) id;

            // Assert that the ids are different or its the same index.
            assert moveId != insertId || insertionPosition == count;

            ids[count] = moveId;
            ids[insertionPosition] = insertId;
            count++;
        }

        /**
         * Return how much more usable ids are left.
         *
         * @return the number of ids that are left for usage.
         */
        int usableIds() {
            return count;
        }

        /**
         * Return the maximum number of ids that are supported.
         *
         * @return the maximum number of ids.
         */
        int maxUsableIds() {
            return ids.length;
        }
    }
}

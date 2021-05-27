/*
 * Copyright 2021 The Netty Project
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

package io.netty.incubator.codec.http3;

/**
 * A strategy that determines when to send <a
 * href="https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#section-2.2.2.3">acknowledgment</a> of new table
 * entries on the QPACK decoder stream.
 */
public interface QpackDecoderStateSyncStrategy {

    /**
     * Callback when an <a
     * href="https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-encoded-field-section-prefi">
     * encoded header field section</a> is decoded successfully by the decoder.
     *
     * @param requiredInsertCount for the encoded field section.
     */
    void sectionAcknowledged(int requiredInsertCount);

    /**
     * When a header field entry is added to the decoder dynamic table.
     *
     * @param insertCount for the entry.
     * @return {@code true} if an <a
     * href="https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-insert-count-increment">insert count
     * increment decoder instruction</a> should be sent.
     */
    boolean entryAdded(int insertCount);

    /**
     * Returns a {@link QpackDecoderStateSyncStrategy} that will acknowledge each entry added via
     * {@link #entryAdded(int)} unless a prior {@link #sectionAcknowledged(int)} call has implicitly acknowledged the
     * addition.
     *
     * @return A {@link QpackDecoderStateSyncStrategy} that will acknowledge each entry added via
     * {@link #entryAdded(int)} unless a prior {@link #sectionAcknowledged(int)} call has implicitly acknowledged the
     * addition.
     */
    static QpackDecoderStateSyncStrategy ackEachInsert() {
        return new QpackDecoderStateSyncStrategy() {
            private int lastCountAcknowledged;

            @Override
            public void sectionAcknowledged(int requiredInsertCount) {
                if (lastCountAcknowledged < requiredInsertCount) {
                    lastCountAcknowledged = requiredInsertCount;
                }
            }

            @Override
            public boolean entryAdded(int insertCount) {
                if (lastCountAcknowledged < insertCount) {
                    lastCountAcknowledged = insertCount;
                    return true;
                }
                return false;
            }
        };
    }
}

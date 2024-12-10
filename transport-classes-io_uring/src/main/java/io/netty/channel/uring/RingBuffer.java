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
package io.netty.channel.uring;


final class RingBuffer {
    private final SubmissionQueue ioUringSubmissionQueue;
    private final CompletionQueue ioUringCompletionQueue;
    private final int features;
    RingBuffer(SubmissionQueue ioUringSubmissionQueue, CompletionQueue ioUringCompletionQueue, int features) {
        this.ioUringSubmissionQueue = ioUringSubmissionQueue;
        this.ioUringCompletionQueue = ioUringCompletionQueue;
        this.features = features;
    }

    int fd() {
        return ioUringCompletionQueue.ringFd;
    }

    int features() {
        return features;
    }

    SubmissionQueue ioUringSubmissionQueue() {
        return this.ioUringSubmissionQueue;
    }

    CompletionQueue ioUringCompletionQueue() {
        return this.ioUringCompletionQueue;
    }

    void close() {
        ioUringSubmissionQueue.release();
        Native.ioUringExit(
                ioUringSubmissionQueue.submissionQueueArrayAddress,
                ioUringSubmissionQueue.ringEntries,
                ioUringSubmissionQueue.ringAddress,
                ioUringSubmissionQueue.ringSize,
                ioUringCompletionQueue.ringAddress,
                ioUringCompletionQueue.ringSize,
                ioUringCompletionQueue.ringFd);
    }
}

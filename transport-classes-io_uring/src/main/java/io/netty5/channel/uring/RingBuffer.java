/*
 * Copyright 2020 The Netty Project
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
package io.netty5.channel.uring;

final class RingBuffer {
    private final SubmissionQueue submissionQueue;
    private final CompletionQueue completionQueue;

    RingBuffer(SubmissionQueue submissionQueue, CompletionQueue completionQueue) {
        this.submissionQueue = submissionQueue;
        this.completionQueue = completionQueue;
    }

    int fd() {
        return completionQueue.ringFd;
    }

    SubmissionQueue ioUringSubmissionQueue() {
        return this.submissionQueue;
    }

    CompletionQueue ioUringCompletionQueue() {
        return this.completionQueue;
    }

    void close() {
        Native.ioUringExit(
                submissionQueue.submissionQueueArrayAddress,
                submissionQueue.ringEntries,
                submissionQueue.ringAddress,
                submissionQueue.ringSize,
                completionQueue.ringAddress,
                completionQueue.ringSize,
                completionQueue.ringFd);
        submissionQueue.release();
    }
}

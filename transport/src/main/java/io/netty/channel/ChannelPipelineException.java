/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel;

/**
 * A {@link ChannelException} which is thrown when a {@link ChannelPipeline}
 * failed to execute an operation.
 */
public class ChannelPipelineException extends ChannelException {

    private static final long serialVersionUID = 3379174210419885980L;

    /**
     * Creates a new instance.
     */
    public ChannelPipelineException() {
    }

    /**
     * Creates a new instance.
     */
    public ChannelPipelineException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new instance.
     */
    public ChannelPipelineException(String message) {
        super(message);
    }

    /**
     * Creates a new instance.
     */
    public ChannelPipelineException(Throwable cause) {
        super(cause);
    }
}

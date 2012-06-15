/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

/**
 * A {@link ChannelPipelineException} which is raised if an inbound or outbound buffer of
 * the expected type is not found while transferring data between {@link ChannelHandler}s.
 * This exception is usually triggered by an incorrectly configured {@link ChannelPipeline}.
 */
public class NoSuchBufferException extends ChannelPipelineException {

    private static final String DEFAULT_MESSAGE =
            "Could not find a suitable destination buffer.  Double-check if the pipeline is " +
            "configured correctly and its handlers works as expected.";

    private static final long serialVersionUID = -131650785896627090L;

    public NoSuchBufferException() {
        this(DEFAULT_MESSAGE);
    }

    public NoSuchBufferException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoSuchBufferException(String message) {
        super(message);
    }

    public NoSuchBufferException(Throwable cause) {
        super(cause);
    }
}

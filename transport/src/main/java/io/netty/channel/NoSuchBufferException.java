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
 * @deprecated not used anymore, will be removed
 */
@Deprecated
public class NoSuchBufferException extends ChannelPipelineException {

    private static final long serialVersionUID = -131650785896627090L;

    public NoSuchBufferException() {
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

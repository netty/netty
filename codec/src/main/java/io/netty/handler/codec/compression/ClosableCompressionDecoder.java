/*
 * Copyright 2017 The Netty Project
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
package io.netty.handler.codec.compression;

/**
 * A {@link CompressionDecoder} which decodes compressed streams with end of stream flag.
 * <p/>
 * If the end of the compressed stream is reached all new inbound bytes will be skipped.
 * Note, that all closable compression decoders are not reusable. You have to create and add a new
 * {@link ClosableCompressionDecoder} to a pipeline if current decoder is closed.
 * <p/>
 * Note, that you can safely remove current handler from a pipeline only if it is already closed.
 */
public interface ClosableCompressionDecoder extends CompressionDecoder {

    /**
     * Returns {@code true} if and only if the end of the compressed stream has been reached.
     */
    boolean isClosed();
}

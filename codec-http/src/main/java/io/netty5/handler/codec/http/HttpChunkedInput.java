/*
 * Copyright 2014 The Netty Project
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
package io.netty5.handler.codec.http;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.Send;
import io.netty5.handler.stream.ChunkedInput;

/**
 * A {@link ChunkedInput} that fetches data chunk by chunk for use with HTTP chunked transfers.
 * <p>
 * Each chunk from the input data will be wrapped within a {@link HttpContent}. At the end of the input data,
 * {@link LastHttpContent} will be written.
 * <p>
 * Ensure that your HTTP response header contains {@code Transfer-Encoding: chunked}.
 * <p>
 * <pre>
 * public void messageReceived(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
 *     HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
 *     response.headers().set(TRANSFER_ENCODING, CHUNKED);
 *     ctx.write(response);
 *
 *     HttpContentChunkedInput httpChunkWriter = new HttpChunkedInput(
 *         new ChunkedFile(&quot;/tmp/myfile.txt&quot;));
 *     Future&lt;Void&gt; sendFileFuture = ctx.write(httpChunkWriter);
 * }
 * </pre>
 */
public class HttpChunkedInput /*implements ChunkedInput<HttpContent> */ {
    // TODO: Migrate ChunkedInput
/*
    private final ChunkedInput<Buffer> input;
    private final LastHttpContent lastHttpContent;
    private boolean sentLastChunk;

    */
/**
     * Creates a new instance using the specified input. {@code lastHttpContent} will be written as the terminating
     * chunk.
     * @param input {@link ChunkedInput} containing data to write
     * @param lastHttpContent {@link LastHttpContent} that will be written as the terminating chunk. Use this for
     * training headers.
     *//*

    public HttpChunkedInput(ChunkedInput<Buffer> input, LastHttpContent lastHttpContent) {
        this.input = input;
        this.lastHttpContent = lastHttpContent;
    }

    @Override
    public boolean isEndOfInput() throws Exception {
        if (input.isEndOfInput()) {
            // Only end of input after last HTTP chunk has been sent
            return sentLastChunk;
        } else {
            return false;
        }
    }

    @Override
    public Send<ChunkedInput<HttpContent>> send() {
        final Send<ChunkedInput<Buffer>> inputSend = input.send();
        final Send<HttpContent> lhcSend = lastHttpContent.send();
        return new Send<>() {
            @Override
            public ChunkedInput<HttpContent> receive() {
                final HttpContent lhc = lhcSend.receive();
                assert lhcSend.referentIsInstanceOf(lhc.getClass());
                return new HttpChunkedInput(inputSend.receive(), (LastHttpContent) lhc);
            }

            @Override
            public void close() {
                try {
                    inputSend.close();
                } finally {
                    lhcSend.close();
                }
            }

            @Override
            public boolean referentIsInstanceOf(Class<?> cls) {
                return cls.isAssignableFrom(HttpChunkedInput.class);
            }
        };
    }

    @Override
    public void close() {
        try {
            input.close();
        } finally {
            lastHttpContent.close();
        }
    }

    @Override
    public boolean isAccessible() {
        return input.isAccessible() && lastHttpContent.isAccessible();
    }

    @Override
    public HttpContent readChunk(BufferAllocator allocator) throws Exception {
        if (input.isEndOfInput()) {
            if (sentLastChunk) {
                return null;
            } else {
                // Send last chunk for this input
                sentLastChunk = true;
                return lastHttpContent;
            }
        } else {
            Buffer buf = input.readChunk(allocator);
            if (buf == null) {
                return null;
            }
            return new DefaultHttpContent(buf);
        }
    }

    @Override
    public long length() {
        return input.length();
    }

    @Override
    public long progress() {
        return input.progress();
    }
*/
}

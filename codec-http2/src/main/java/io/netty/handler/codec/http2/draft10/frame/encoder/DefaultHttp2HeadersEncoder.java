/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2.draft10.frame.encoder;

import static io.netty.handler.codec.http2.draft10.connection.Http2ConnectionUtil.DEFAULT_HEADER_TABLE_SIZE;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.http2.draft10.Http2Error;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.Http2Headers;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Map.Entry;

import com.google.common.base.Charsets;
import com.twitter.hpack.Encoder;

public class DefaultHttp2HeadersEncoder implements Http2HeadersEncoder {
  private static final Charset DEFAULT_CHARSET = Charsets.UTF_8;

  private final Encoder encoder;

  public DefaultHttp2HeadersEncoder() {
    this.encoder = new Encoder(DEFAULT_HEADER_TABLE_SIZE);
  }

  @Override
  public void encodeHeaders(Http2Headers headers, ByteBuf buffer) throws Http2Exception {
    try {
      OutputStream stream = new ByteBufOutputStream(buffer);
      for (Entry<String, String> header : headers) {
        byte[] key = header.getKey().getBytes(DEFAULT_CHARSET);
        byte[] value = header.getValue().getBytes(DEFAULT_CHARSET);
        encoder.encodeHeader(stream, key, value);
      }
      encoder.endHeaders(stream);
    } catch (IOException e) {
      throw Http2Exception.format(Http2Error.COMPRESSION_ERROR, "Failed encoding headers block: %s",
          e.getMessage());
    }
  }

  @Override
  public void setHeaderTableSize(int size) throws Http2Exception {
    // TODO: can we throw away the encoder and create a new one?
  }

}

/*
 * Copyright 2023 The Netty Project
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
package io.netty.handler.codec.socksx.v5;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.util.internal.UnstableApi;

import java.util.List;

/**
 * Decodes a single {@link Socks5PrivateAuthResponse} from the inbound {@link ByteBuf}s.
 * On successful decode, this decoder will forward the received data to the next handler, so that
 * other handler can remove or replace this decoder later. On failed decode, this decoder will
 * discard the received data, so that other handler closes the connection later.
 * <p>
 * The default format follows a simple structure:
 * <ul>
 *   <li>1 byte: version (must be 1)</li>
 *   <li>1 byte: status (0x00 for success, 0xFF for failure)</li>
 * </ul>
 * </p>
 * <p>
 * For custom private authentication protocols, this decoder can be extended by:
 * <ul>
 *   <li>Subclassing this decoder</li>
 *   <li>Overriding the decode method to handle additional fields</li>
 *   <li>Creating a custom response class that extends {@link DefaultSocks5PrivateAuthResponse}</li>
 * </ul>
 * </p>
 */
public final class Socks5PrivateAuthResponseDecoder extends ReplayingDecoder<Socks5PrivateAuthResponseDecoder.State> {

  /**
   * Decoder states for SOCKS5 private authentication responses.
   */
  @UnstableApi
  public enum State {
    /** Initial state. */
    INIT,
    /** Authentication successful. */
    SUCCESS,
    /** Authentication failed. */
    FAILURE
  }

  /**
   * Creates a new SOCKS5 private authentication response decoder.
   */
  public Socks5PrivateAuthResponseDecoder() {
    super(State.INIT);
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf in,
                        List<Object> out) throws Exception {
    try {
      switch (state()) {
        case INIT:
          final byte version = in.readByte();
          if (version != 1) {
            throw new DecoderException(
                "unsupported subnegotiation version: " + version + " (expected: 1)");
          }

          out.add(new DefaultSocks5PrivateAuthResponse(
              Socks5PrivateAuthStatus.valueOf(in.readByte())));
          checkpoint(State.SUCCESS);
          break;
        case SUCCESS:
          int readableBytes = actualReadableBytes();
          if (readableBytes > 0) {
            out.add(in.readRetainedSlice(readableBytes));
          }
          break;
        case FAILURE:
          in.skipBytes(actualReadableBytes());
          break;
        default:
          throw new Error();
      }
    } catch (Exception e) {
      fail(out, e);
    }
  }

  /**
   * Handles decoder failures by setting the appropriate error state.
   *
   * @param out the output list to add the failure message to
   * @param cause the exception that caused the failure
   */
  private void fail(List<Object> out, Exception cause) {
    if (!(cause instanceof DecoderException)) {
      cause = new DecoderException(cause);
    }

    checkpoint(State.FAILURE);

    Socks5Message m = new DefaultSocks5PrivateAuthResponse(Socks5PrivateAuthStatus.FAILURE);
    m.setDecoderResult(DecoderResult.failure(cause));
    out.add(m);
  }
}

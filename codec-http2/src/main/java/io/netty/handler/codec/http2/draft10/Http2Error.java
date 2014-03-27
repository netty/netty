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

package io.netty.handler.codec.http2.draft10;

/**
 * All error codes identified by the HTTP2 spec.
 */
public enum Http2Error {
  NO_ERROR(0),
  PROTOCOL_ERROR(1),
  INTERNAL_ERROR(2),
  FLOW_CONTROL_ERROR(3),
  SETTINGS_TIMEOUT(4),
  STREAM_CLOSED(5),
  FRAME_SIZE_ERROR(6),
  REFUSED_STREAM(7),
  CANCEL(8),
  COMPRESSION_ERROR(9),
  CONNECT_ERROR(10),
  ENHANCE_YOUR_CALM(11),
  INADEQUATE_SECURITY(12);

  private final int code;

  private Http2Error(int code) {
    this.code = code;
  }

  public int getCode() {
    return this.code;
  }
}

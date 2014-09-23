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
package io.netty.handler.codec.http2;

/**
 * Provides common functionality for {@link Http2HeaderTable}
 */
class DefaultHttp2HeaderTableListSize {
    private int maxHeaderListSize = Integer.MAX_VALUE;

    public void maxHeaderListSize(int max) throws Http2Exception {
        if (max < 0) {
            throw Http2Exception.protocolError("Header List Size must be non-negative but was %d", max);
        }
        maxHeaderListSize = max;
    }

    public int maxHeaderListSize() {
        return maxHeaderListSize;
    }
}

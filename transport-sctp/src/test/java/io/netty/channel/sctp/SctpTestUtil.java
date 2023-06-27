/*
 * Copyright 2021 The Netty Project
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
package io.netty.channel.sctp;

import com.sun.nio.sctp.SctpChannel;
import io.netty.util.SuppressForbidden;

import java.io.IOException;
import java.nio.channels.Channel;

@SuppressForbidden(reason = "test-only")
final class SctpTestUtil {

    private static final boolean SCTP_SUPPORTED;

    static {
        boolean supported = true;
        Channel channel = null;
        try {
            channel = SctpChannel.open();
        } catch (UnsupportedOperationException e) {
            supported = false;
        } catch (IOException e) {
            // ignore
        } finally {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException ignore) {
                    // ignore
                }
            }
        }
        SCTP_SUPPORTED = supported;
    }

    public static boolean isSctpSupported() {
        return SCTP_SUPPORTED;
    }

    private SctpTestUtil() { }
}

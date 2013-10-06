/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.spdy;

public enum SpdyVersion {
    SPDY_2   (2, false, false),
    SPDY_3   (3, true,  false),
    SPDY_3_1 (3, true,  true);

    static SpdyVersion valueOf(int version) {
        if (version == 2) {
            return SPDY_2;
        }
        if (version == 3) {
            return SPDY_3;
        }
        throw new IllegalArgumentException(
                "unsupported version: " + version);
    }

    private final int version;
    private final boolean flowControl;
    private final boolean sessionFlowControl;

    private SpdyVersion(int version, boolean flowControl, boolean sessionFlowControl) {
        this.version = version;
        this.flowControl = flowControl;
        this.sessionFlowControl = sessionFlowControl;
    }

    int getVersion() {
        return version;
    }

    boolean useFlowControl() {
        return flowControl;
    }

    boolean useSessionFlowControl() {
        return sessionFlowControl;
    }
}

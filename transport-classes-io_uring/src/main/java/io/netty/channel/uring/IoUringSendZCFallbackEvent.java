/*
 * Copyright 2025 The Netty Project
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
package io.netty.channel.uring;

/**
 * Event that is fired when cqe.res & IORING_NOTIF_USAGE_ZC_COPIED != 0
 */
public final class IoUringSendZCFallbackEvent {

    static final IoUringSendZCFallbackEvent INSTANCE = new IoUringSendZCFallbackEvent();

    private IoUringSendZCFallbackEvent() {
    }

}

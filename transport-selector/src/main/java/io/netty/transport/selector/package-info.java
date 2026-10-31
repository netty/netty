/*
 * Copyright 2026 The Netty Project
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

/**
 * Automatic transport selection API.
 *
 * The {@link io.netty.transport.selector.Transports} class provides a simple, opinionated API
 * for automatically selecting the optimal transport (IoUring, Epoll, KQueue, or NIO)
 * for the current platform, eliminating the need for libraries and applications to
 * implement their own transport detection logic.
 */
package io.netty.transport.selector;

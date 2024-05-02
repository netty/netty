/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel;

/**
 * An IO op that can be submitted to an {@link IoRegistration} via {@link IoRegistration#submit(IoOps)}.
// * These submitted {@link IoOps} will result in {@link IoEvent}s on the related {@link IoHandle}.
 * Concrete {@link IoRegistration} implementations support different concrete {@link IoOps} implementations and
 * will so also "produce" concrete {@link IoEvent}s.
 */
public interface IoOps {
    // Marker interface.
}

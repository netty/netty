/*
 * Copyright 2012 The Netty Project
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
 * Encoder, decoder and their compatibility stream implementations which
 * transform a {@link java.io.Serializable} object into a byte buffer and
 * vice versa.
 * <p>
 * <strong>Security:</strong> serialization can be a security liability,
 * and should not be used without defining a list of classes that are
 * allowed to be desirialized. Such a list can be specified with the
 * <tt>jdk.serialFilter</tt> system property, for instance.
 * See the <a href="https://docs.oracle.com/en/java/javase/17/core/serialization-filtering1.html">
 * serialization filtering</a> article for more information.
 *
 * @deprecated This package has been deprecated with no replacement,
 * because serialization can be a security liability
 */
@Deprecated
package io.netty.handler.codec.serialization;

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
package io.netty5.util;

/**
 * A {@code SafeCloseable} can be safely closed without risk of an exception being thrown.
 * <p>
 * This interface extends {@link AutoCloseable} so instances can be used in try-with-resources clauses.
 *
 * @implNote Similar to {@link java.io.Closeable}, implementors should make their {@link #close()} idempotent.
 * In other words, it should be safe to call {@link #close()} multiple times on the same instance.
 * This is a restriction beyond what {@link AutoCloseable} provides.
 */
public interface SafeCloseable extends AutoCloseable {
    @Override
    void close();
}

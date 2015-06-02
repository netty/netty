/*
 * Copyright 2015 The Netty Project
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

/**
 * This packages handles Linux libaio at a low level.
 * Buffers needs to be specially allocated as they need to be aligned to 512 or 4096
 *
 * Three main classes on this package:
 * {@link LibaioContext} which will handle the native methods and the queue
 * {@link LibaioFile} which represents the file itself
 * {@link ErrorInfo} to be placed on the callback results in case of errors.
 */
package io.netty.channel.libaio;

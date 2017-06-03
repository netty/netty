/*
 * Copyright 2014 The Netty Project
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
 * This package provides a generic logical router that is independent of the
 * underlying network processing layer, e.g. Netty. For a router that is
 * specific to Netty, see {@code io.netty.handler.codec.http.router} package.
 */
package io.netty.handler.codec.http.routing;

// Implementation note:
// The signature of the routers is a little verbose because of this problem:
// http://stackoverflow.com/questions/1069528
// http://stackoverflow.com/questions/9655335

/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket.http;

/**
 * Represents the state change of a chanel in response in the amount of pending data to be
 * sent - either no change occurs, the channel becomes desaturated (indicating that writing
 * can safely commence) or it becomes saturated (indicating that writing should cease).
 * 



 */
enum SaturationStateChange {
    NO_CHANGE, DESATURATED, SATURATED
}

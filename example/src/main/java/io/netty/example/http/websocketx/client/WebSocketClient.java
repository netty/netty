//The MIT License
//
//Copyright (c) 2009 Carl Bystršm
//
//Permission is hereby granted, free of charge, to any person obtaining a copy
//of this software and associated documentation files (the "Software"), to deal
//in the Software without restriction, including without limitation the rights
//to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
//copies of the Software, and to permit persons to whom the Software is
//furnished to do so, subject to the following conditions:
//
//The above copyright notice and this permission notice shall be included in
//all copies or substantial portions of the Software.
//
//THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
//THE SOFTWARE.
package io.netty.example.http.websocketx.client;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

/**
 * Copied from https://github.com/cgbystrom/netty-tools
 */
public interface WebSocketClient {

    /**
     * Connect to server Host and port is setup by the factory.
     * 
     * @return Connect future. Fires when connected.
     */
    ChannelFuture connect();

    /**
     * Disconnect from the server
     * 
     * @return Disconnect future. Fires when disconnected.
     */
    ChannelFuture disconnect();

    /**
     * Send data to server
     * 
     * @param frame
     *            Data for sending
     * @return Write future. Will fire when the data is sent.
     */
    ChannelFuture send(WebSocketFrame frame);
}

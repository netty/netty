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

package org.jboss.netty.example.http.websocketx.client;

import org.jboss.netty.handler.codec.http.websocketx.WebSocketFrame;


/**
 * Copied from https://github.com/cgbystrom/netty-tools
 * 
 * Callbacks for the {@link WebSocketClient}. Implement and get notified when events happen.
 * 
 * @author <a href="http://www.pedantique.org/">Carl Bystr&ouml;m</a>
 * @author <a href="http://www.veebsbraindump.com/">Vibul Imtarnasan</a>
 */
public interface WebSocketCallback {

    /**
     * Called when the client is connected to the server
     * 
     * @param client
     *            Current client used to connect
     */
    public void onConnect(WebSocketClient client);

    /**
     * Called when the client got disconnected from the server.
     * 
     * @param client
     *            Current client that was disconnected
     */
    public void onDisconnect(WebSocketClient client);

    /**
     * Called when a message arrives from the server.
     * 
     * @param client
     *            Current client connected
     * @param frame
     *            Data received from server
     */
    public void onMessage(WebSocketClient client, WebSocketFrame frame);

    /**
     * Called when an unhandled errors occurs.
     * 
     * @param t
     *            The causing error
     */
    public void onError(Throwable t);
}

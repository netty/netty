/*
 * Copyright 2010 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.example.http.websocket;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.util.CharsetUtil;


/**
 * Generates the demo HTML page which is served at http://localhost:8080/
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class WebSocketServerIndexPage {

    private static final String NEWLINE = "\r\n";

    public static ChannelBuffer getContent(String webSocketLocation) {
        return ChannelBuffers.copiedBuffer(
        "<html><head><title>Web Socket Test</title></head>" + NEWLINE +
        "<body>" + NEWLINE +
        "<script type=\"text/javascript\">" + NEWLINE +
        "var socket;" + NEWLINE +
        "if (window.WebSocket) {" + NEWLINE +
        "  socket = new WebSocket(\"" + webSocketLocation + "\");" + NEWLINE +
        "  socket.onmessage = function(event) { alert(event.data); };" + NEWLINE +
        "  socket.onopen = function(event) { alert(\"Web Socket opened!\"); };" + NEWLINE +
        "  socket.onclose = function(event) { alert(\"Web Socket closed.\"); };" + NEWLINE +
        "} else {" + NEWLINE +
        "  alert(\"Your browser does not support Web Socket.\");" + NEWLINE +
        "}" + NEWLINE +
        "" + NEWLINE +
        "function send(message) {" + NEWLINE +
        "  if (!window.WebSocket) { return; }" + NEWLINE +
        "  if (socket.readyState == WebSocket.OPEN) {" + NEWLINE +
        "    socket.send(message);" + NEWLINE +
        "  } else {" + NEWLINE +
        "    alert(\"The socket is not open.\");" + NEWLINE +
        "  }" + NEWLINE +
        "}" + NEWLINE +
        "</script>" + NEWLINE +
        "<form onsubmit=\"return false;\">" + NEWLINE +
        "<input type=\"text\" name=\"message\" value=\"Hello, World!\"/>" +
        "<input type=\"button\" value=\"Send Web Socket Data\" onclick=\"send(this.form.message.value)\" />" + NEWLINE +
        "</form>" + NEWLINE +
        "</body>" + NEWLINE +
        "</html>" + NEWLINE,
        CharsetUtil.US_ASCII);
    }
}

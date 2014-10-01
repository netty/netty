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
package io.netty.example.http.websocketx.benchmarkserver;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;

/**
 * Generates the benchmark HTML page which is served at http://localhost:8080/
 */
public final class WebSocketServerBenchmarkPage {

    private static final String NEWLINE = "\r\n";

    public static ByteBuf getContent(String webSocketLocation) {
        return Unpooled.copiedBuffer(
                "<html><head><title>Web Socket Performance Test</title></head>" + NEWLINE +
                "<body>" + NEWLINE +
                "<h2>WebSocket Performance Test</h2>" + NEWLINE +
                "<label>Connection Status:</label>" + NEWLINE +
                "<label id=\"connectionLabel\"></label><br />" + NEWLINE +

                "<form onsubmit=\"return false;\">" + NEWLINE +
                "Message size:" +
                "<input type=\"text\" id=\"messageSize\" value=\"1024\"/><br>" + NEWLINE +
                "Number of messages:" +
                "<input type=\"text\" id=\"nrMessages\" value=\"100000\"/><br>" + NEWLINE +
                "Data Type:" +
                "<input type=\"radio\" name=\"type\" id=\"typeText\" value=\"text\" checked>text" +
                "<input type=\"radio\" name=\"type\" id=\"typeBinary\" value=\"binary\">binary<br>" + NEWLINE +
                "Mode:<br>" + NEWLINE +
                "<input type=\"radio\" name=\"mode\" id=\"modeSingle\" value=\"single\" checked>" +
                "Wait for response after each messages<br>" + NEWLINE +
                "<input type=\"radio\" name=\"mode\" id=\"modeAll\" value=\"all\">" +
                "Send all messages and then wait for all responses<br>" + NEWLINE +
                "<input type=\"checkbox\" id=\"verifiyResponses\">Verify responded messages<br>" + NEWLINE +
                "<input type=\"button\" value=\"Start Benchmark\"" + NEWLINE +
                "       onclick=\"startBenchmark()\" />" + NEWLINE +
                "<h3>Output</h3>" + NEWLINE +
                "<textarea id=\"output\" style=\"width:500px;height:300px;\"></textarea>" + NEWLINE +
                "<br>" + NEWLINE +
                "<input type=\"button\" value=\"Clear\" onclick=\"clearText()\">" + NEWLINE +
                "</form>" + NEWLINE +

                "<script type=\"text/javascript\">" + NEWLINE +
                "var benchRunning = false;" + NEWLINE +
                "var messageSize = 0;" + NEWLINE +
                "var totalMessages = 0;" + NEWLINE +
                "var rcvdMessages = 0;" + NEWLINE +
                "var isBinary = true;" + NEWLINE +
                "var isSingle = true;" + NEWLINE +
                "var verifiyResponses = false;" + NEWLINE +
                "var benchData = null;" + NEWLINE +
                "var startTime;" + NEWLINE +
                "var endTime;" + NEWLINE +
                "var socket;" + NEWLINE +
                "var output = document.getElementById('output');" + NEWLINE +
                "var connectionLabel = document.getElementById('connectionLabel');" + NEWLINE +
                "if (!window.WebSocket) {" + NEWLINE +
                "  window.WebSocket = window.MozWebSocket;" + NEWLINE +
                '}' + NEWLINE +
                "if (window.WebSocket) {" + NEWLINE +
                "  socket = new WebSocket(\"" + webSocketLocation + "\");" + NEWLINE +
                "  socket.binaryType = 'arraybuffer';" + NEWLINE +
                "  socket.onmessage = function(event) {" + NEWLINE +
                "    if (verifiyResponses) {" + NEWLINE +
                "        if (isBinary) {" + NEWLINE +
                "            if (!(event.data instanceof ArrayBuffer) || " + NEWLINE +
                "                  event.data.byteLength != benchData.byteLength) {" + NEWLINE +
                "                onInvalidResponse(benchData, event.data);" + NEWLINE +
                "                return;" + NEWLINE +
                "            } else {" + NEWLINE +
                "                var v = new Uint8Array(event.data);" + NEWLINE +
                "                for (var j = 0; j < benchData.byteLength; j++) {" + NEWLINE +
                "                    if (v[j] != benchData[j]) {" + NEWLINE +
                "                        onInvalidResponse(benchData, event.data);" + NEWLINE +
                "                        return;" + NEWLINE +
                "                    }" + NEWLINE +
                "                }" + NEWLINE +
                "            }" + NEWLINE +
                "        } else {" + NEWLINE +
                "            if (event.data != benchData) {" + NEWLINE +
                "                onInvalidResponse(benchData, event.data);" + NEWLINE +
                "                return;" + NEWLINE +
                "            }" + NEWLINE +
                "        }" + NEWLINE +
                "    }" + NEWLINE +
                "    rcvdMessages++;" + NEWLINE +
                "    if (rcvdMessages == totalMessages) {" + NEWLINE +
                "        onFinished();" + NEWLINE +
                "    } else if (isSingle) {" + NEWLINE +
                "        socket.send(benchData);" + NEWLINE +
                "    }" + NEWLINE +
                "  };" + NEWLINE +
                "  socket.onopen = function(event) {" + NEWLINE +
                "    connectionLabel.innerHTML = \"Connected\";" + NEWLINE +
                "  };" + NEWLINE +
                "  socket.onclose = function(event) {" + NEWLINE +
                "    benchRunning = false;" + NEWLINE +
                "    connectionLabel.innerHTML = \"Disconnected\";" + NEWLINE +
                "  };" + NEWLINE +
                "} else {" + NEWLINE +
                "  alert(\"Your browser does not support Web Socket.\");" + NEWLINE +
                '}' + NEWLINE +
                NEWLINE +
                "function onInvalidResponse(sent,recvd) {" + NEWLINE +
                "    socket.close();" + NEWLINE +
                "    alert(\"Error: Sent data did not match the received data!\");" + NEWLINE +
                "}" + NEWLINE +
                NEWLINE +
                "function clearText() {" + NEWLINE +
                "    output.value=\"\";" + NEWLINE +
                "}" + NEWLINE +
                NEWLINE +
                "function createBenchData() {" + NEWLINE +
                "    if (isBinary) {" + NEWLINE +
                "        benchData = new Uint8Array(messageSize);" + NEWLINE +
                "        for (var i=0; i < messageSize; i++) {" + NEWLINE +
                "            benchData[i] += Math.floor(Math.random() * 255);" + NEWLINE +
                "        }" + NEWLINE +
                "    } else { " + NEWLINE +
                "        benchData = \"\";" + NEWLINE +
                "        for (var i=0; i < messageSize; i++) {" + NEWLINE +
                "            benchData += String.fromCharCode(Math.floor(Math.random() * (123 - 65) + 65));" + NEWLINE +
                "        }" + NEWLINE +
                "    }" + NEWLINE +
                "}" + NEWLINE +
                NEWLINE +
                "function startBenchmark(message) {" + NEWLINE +
                "  if (!window.WebSocket || benchRunning) { return; }" + NEWLINE +
                "  if (socket.readyState == WebSocket.OPEN) {" + NEWLINE +
                "    isBinary = document.getElementById('typeBinary').checked;" + NEWLINE +
                "    isSingle = document.getElementById('modeSingle').checked;" + NEWLINE +
                "    verifiyResponses = document.getElementById('verifiyResponses').checked;" + NEWLINE +
                "    messageSize = parseInt(document.getElementById('messageSize').value);" + NEWLINE +
                "    totalMessages = parseInt(document.getElementById('nrMessages').value);" + NEWLINE +
                "    if (isNaN(messageSize) || isNaN(totalMessages)) return;" + NEWLINE +
                "    createBenchData();" + NEWLINE +
                "    output.value = output.value + '\\nStarting Benchmark';" + NEWLINE +
                "    rcvdMessages = 0;" + NEWLINE +
                "    benchRunning = true;" + NEWLINE +
                "    startTime = new Date();" + NEWLINE +
                "    if (isSingle) {" + NEWLINE +
                "        socket.send(benchData);" + NEWLINE +
                "    } else {" + NEWLINE +
                "        for (var i = 0; i < totalMessages; i++) socket.send(benchData);" + NEWLINE +
                "    }" + NEWLINE +
                "  } else {" + NEWLINE +
                "    alert(\"The socket is not open.\");" + NEWLINE +
                "  }" + NEWLINE +
                '}' + NEWLINE +
                NEWLINE +
                "function onFinished() {" + NEWLINE +
                "    endTime = new Date();" + NEWLINE +
                "    var duration = (endTime - startTime) / 1000.0;" + NEWLINE +
                "    output.value = output.value + '\\nTest took: ' + duration + 's';" + NEWLINE +
                "    var messagesPerS = totalMessages / duration;" + NEWLINE +
                "    output.value = output.value + '\\nPerformance: ' + messagesPerS + ' Messages/s';" + NEWLINE +
                "    output.value = output.value + ' in each direction';" + NEWLINE +
                "    output.value = output.value + '\\nRound trip: ' + 1000.0/messagesPerS + 'ms';" + NEWLINE +
                "    var throughput = messageSize * totalMessages / duration;" + NEWLINE +
                "    var throughputText;" + NEWLINE +
                "    if (isBinary) throughputText = throughput / (1024*1024) + ' MB/s';" + NEWLINE +
                "    else throughputText = throughput / (1000*1000) + ' MChars/s';" + NEWLINE +
                "    output.value = output.value + '\\nThroughput: ' + throughputText;" + NEWLINE +
                "    output.value = output.value + ' in each direction';" + NEWLINE +
                "    benchRunning = false;" + NEWLINE +
                "}" + NEWLINE +
                "</script>" + NEWLINE +
                "</body>" + NEWLINE +
                "</html>" + NEWLINE, CharsetUtil.US_ASCII);
    }

    private WebSocketServerBenchmarkPage() {
        // Unused
    }
}

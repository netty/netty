/*
 * Copyright 2012 The Netty Project
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
 * <p>This package contains an example web socket web server with server SSL.
 * <p>To run this example, follow the steps below:
 * <dl>
 *   <dt>Step 1. Generate Your Key
 *   <dd>
 *     {@code keytool -genkey -keystore mySrvKeystore -keyalg RSA}.
 *     Make sure that you set the key password to be the same the key file password.
 *   <dt>Step 2. Specify your key store file and password as system properties
 *   <dd>
 *     {@code -Dkeystore.file.path=<path to mySrvKeystore> -Dkeystore.file.password=<password>}
 *   <dt>Step 3. Run WebSocketSslServer as a Java application
 *   <dd>
 *     Once started, you can test the web server against your browser by navigating to https://localhost:8081/
 * </dl>
 * <p>To find out more about setting up key stores, refer to this
 * <a href="http://download.oracle.com/javase/6/docs/technotes/guides/security/jsse/JSSERefGuide.html">giude</a>.
 */
package io.netty.example.http.websocketx.sslserver;

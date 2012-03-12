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

/**
 * This package is intended for use with testing against the Python 
 * <a href="http://www.tavendo.de/autobahn/testsuite.html">AutoBahn test suite</a>.
 *
 * <h3>How to run the tests on Ubuntu</h3>
 * 
 * <p>01. Add <tt>ppa:twisted-dev/ppa</tt> to your system's Software Sources
 * 
 * <p>02. Install Twisted V11: <tt>sudo apt-get install python-twisted</tt>
 * 
 * <p>03. Intall Python Setup Tools: <tt>sudo apt-get install python-setuptools</tt>
 * 
 * <p>04. Install AutoBahn: <tt>sudo easy_install Autobahn</tt>. If you already have Autobahn installed, you may need
 * to upgrade it: <tt>sudo easy_install --upgrade Autobahn</tt>. Make suer v0.4.10 is installed.
 * 
 * <p>05. Get AutoBahn testsuite source code: <tt>git clone git@github.com:tavendo/AutobahnPython.git</tt>
 * 
 * <p>06. Go to AutoBahn directory: <tt>cd AutobahnPython</tt>
 * 
 * <p>07. Checkout stable version: <tt>git checkout v0.4.10</tt>
 * 
 * <p>08. Go to test suite directory: <tt>cd testsuite/websockets</tt>
 * 
 * <p>09. Edit <tt>fuzzing_clinet_spec.json</tt> and set the hybi specification version to 10 or 17 (RFC 6455).
 * <code>
 *    {
 *       "options": {"failByDrop": false},
 *       "servers": [{"agent": "Netty", "url": "ws://localhost:9000", "options": {"version": 17}}],
 *       "cases": ["*"],
 *       "exclude-cases": [],
 *       "exclude-agent-cases": {"FoobarServer*": ["4.*", "1.1.3"]}
 *    }
 * </code>
 * 
 * <p>10. Run our <tt>AutobahnServer</tt> located in this package. If you are in Eclipse IDE, right click on 
 * <tt>AutobahnServer.java</tt> and select Run As > Java Application.
 * 
 * <p>11. Run the Autobahn test <tt>python fuzzing_client.py</tt>. Note that the actual test case python code is 
 * located with the easy_install package (e.g. in <tt>/usr/local/lib/python2.7/dist-packages/
 * autobahn-0.4.10-py2.7.egg/autobahn/cases</tt>) and not in the checked out git repository.
 *   
 * <p>12. See the results in <tt>reports/servers/index.html</tt>
 */
package org.jboss.netty.example.http.websocketx.autobahn;


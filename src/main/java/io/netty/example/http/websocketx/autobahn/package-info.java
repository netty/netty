/*
 * Copyright 2009 Red Hat, Inc.
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
 * <p>04. Install AutoBahn: <tt>sudo easy_install Autobahn</tt>
 * 
 * <p>05. Get AutoBahn testsuite source code: <tt>git clone git@github.com:oberstet/Autobahn.git</tt>
 * 
 * <p>06. Go to AutoBahn directory: <tt>cd Autobahn</tt>
 * 
 * <p>07. Checkout stable version: <tt>git checkout v0.4.3</tt>
 * 
 * <p>08. Go to test suite directory: <tt>cd testsuite/websockets</tt>
 * 
 * <p>09. Edit <tt>fuzzing_clinet_spec.json</tt> and set the version to 10 or 17.
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
 * <p>10. Run the test <tt>python fuzzing_client.py</tt>. Note that the actual test case python code is 
 * located in <tt>/usr/local/lib/python2.6/dist-packages/autobahn-0.4.3-py2.6.egg/autobahn/cases</tt> 
 * and not in the checked out git repository.
 *   
 * <p>11. See the results in <tt>reports/servers/index.html</tt>
 */
package io.netty.example.http.websocketx.autobahn;


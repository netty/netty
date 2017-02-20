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
 * This package is intended for use with testing against the Python
 * <a href="http://www.tavendo.de/autobahn/testsuite.html">AutoBahn test suite</a>.
 *
 * Autobahn installation documentation can be found <a href="http://autobahn.ws/testsuite/installation">here</a>.
 *
 * <h3>How to run the tests on Ubuntu.</h3>
 *
 * <p>01. Install <a href="http://python.org/">python</a> (if not already installed).
 *
 * <p>02. Install <a href="http://pypi.python.org/pypi/setuptools">Python Setup Tools</a> if not already
 * installed. <tt>sudo apt-get install python-setuptools</tt>
 *
 * <p>03. Add <tt>ppa:twisted-dev/ppa</tt> to your system's Software Sources
 *
 * <p>04. Install Twisted: <tt>sudo apt-get install python-twisted</tt>
 *
 * <p>05. Install AutoBahn: <tt>sudo easy_install autobahntestsuite</tt>.  Test using <tt>wstest --help</tt>.
 *
 * <p>06. Create a directory for test configuration and results: <tt>mkdir autobahn</tt> <tt>cd autobahn</tt>.
 *
 * <p>07. Create <tt>fuzzing_clinet_spec.json</tt> in the above directory
 * {@code
 * {
 *    "options": {"failByDrop": false},
 *    "outdir": "./reports/servers",
 *
 *    "servers": [
 *                 {"agent": "Netty4",
 *                  "url": "ws://localhost:9000",
 *                  "options": {"version": 18}}
 *               ],
 *
 *    "cases": ["*"],
 *    "exclude-cases": [],
 *    "exclude-agent-cases": {}
 * }
 * }
 *
 * <p>08. Run the <tt>AutobahnServer</tt> located in this package. If you are in Eclipse IDE, right click on
 * <tt>AutobahnServer.java</tt> and select Run As &gt; Java Application.
 *
 * <p>09. Run the Autobahn test <tt>wstest -m fuzzingclient -s fuzzingclient.json</tt>.
 *
 * <p>10. See the results in <tt>./reports/servers/index.html</tt>
 */
package io.netty.testsuite.autobahn;


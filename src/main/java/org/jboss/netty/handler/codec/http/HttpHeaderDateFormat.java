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
package org.jboss.netty.handler.codec.http;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author <a href="http://www.rogiel.com/">Rogiel Josias Sulzbach</a>
 * @version $Rev$, $Date$
 */
final class HttpHeaderDateFormat extends SimpleDateFormat {
	private static final long serialVersionUID = -925286159755905325L;

	/*
	 * Official documentations says that the "E, d-MMM-y HH:mm:ss z" format is
	 * now obsolete. "E, d MMM yyyy HH:mm:ss z" should be used.
	 */

	HttpHeaderDateFormat() {
		super("E, d MMM yyyy HH:mm:ss z", Locale.ENGLISH);
		setTimeZone(TimeZone.getTimeZone("GMT"));
	}
}

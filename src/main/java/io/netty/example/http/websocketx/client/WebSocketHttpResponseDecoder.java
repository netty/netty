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
package io.netty.example.http.websocketx.client;

import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseDecoder;

/**
 * Fix bug in standard HttpResponseDecoder for web socket clients. When status 101 is received for Hybi00, there are 16
 * bytes of contents expected
 */
public class WebSocketHttpResponseDecoder extends HttpResponseDecoder {

	@Override
	protected boolean isContentAlwaysEmpty(HttpMessage msg) {
		if (msg instanceof HttpResponse) {
			HttpResponse res = (HttpResponse) msg;
			int code = res.getStatus().getCode();

			// FIX force reading of protocol upgrade challenge data into the content buffer
			if (code == 101) {
				return false;
			}

			if (code < 200) {
				return true;
			}
			switch (code) {
			case 204:
			case 205:
			case 304:
				return true;
			}
		}
		return false;
	}
}

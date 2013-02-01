/*
 * Copyright 2013 The Netty Project
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
package io.netty.verify.osgi;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tester Service Component/Implementation.
 */
@Component
public class NettyServiceProvider implements NettyService {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public String getHelloNetty() {
        return "hello netty";
    }

    @Activate
    protected void activate() {
        log.info("activate");
    }

    @Deactivate
    protected void deactivate() {
        log.info("deactivate");
    }

}

/*
 * Copyright 2017 The Netty Project
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
package io.netty.resolver.dns;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.util.internal.logging.InternalLogLevel;
import io.netty.util.internal.logging.InternalLogger;

import java.net.InetSocketAddress;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

final class LoggingDnsQueryLifecycleObserver implements DnsQueryLifecycleObserver {
    private final InternalLogger logger;
    private final InternalLogLevel level;
    private final DnsQuestion question;
    private InetSocketAddress dnsServerAddress;

    LoggingDnsQueryLifecycleObserver(DnsQuestion question, InternalLogger logger, InternalLogLevel level) {
        this.question = checkNotNull(question, "question");
        this.logger = checkNotNull(logger, "logger");
        this.level = checkNotNull(level, "level");
    }

    @Override
    public void queryWritten(InetSocketAddress dnsServerAddress, ChannelFuture future) {
        this.dnsServerAddress = dnsServerAddress;
    }

    @Override
    public void queryCancelled(int queriesRemaining) {
        if (dnsServerAddress != null) {
            logger.log(level, "from {} : {} cancelled with {} queries remaining", dnsServerAddress, question,
                        queriesRemaining);
        } else {
            logger.log(level, "{} query never written and cancelled with {} queries remaining", question,
                        queriesRemaining);
        }
    }

    @Override
    public DnsQueryLifecycleObserver queryRedirected(List<InetSocketAddress> nameServers) {
        logger.log(level, "from {} : {} redirected", dnsServerAddress, question);
        return this;
    }

    @Override
    public DnsQueryLifecycleObserver queryCNAMEd(DnsQuestion cnameQuestion) {
        logger.log(level, "from {} : {} CNAME question {}", dnsServerAddress, question, cnameQuestion);
        return this;
    }

    @Override
    public DnsQueryLifecycleObserver queryNoAnswer(DnsResponseCode code) {
        logger.log(level, "from {} : {} no answer {}", dnsServerAddress, question, code);
        return this;
    }

    @Override
    public void queryFailed(Throwable cause) {
        if (dnsServerAddress != null) {
            logger.log(level, "from {} : {} failure", dnsServerAddress, question, cause);
        } else {
            logger.log(level, "{} query never written and failed", question, cause);
        }
    }

    @Override
    public void querySucceed() {
    }
}

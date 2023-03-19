/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.resolver.dns;

import io.netty5.handler.codec.dns.DnsQuestion;
import io.netty5.handler.codec.dns.DnsResponseCode;
import io.netty5.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.util.List;

import static java.util.Objects.requireNonNull;

final class LoggingDnsQueryLifecycleObserver implements DnsQueryLifecycleObserver {
    private final Logger logger;
    private final Level level;
    private final DnsQuestion question;
    private InetSocketAddress dnsServerAddress;

    LoggingDnsQueryLifecycleObserver(DnsQuestion question, Logger logger, Level level) {
        this.question = requireNonNull(question, "question");
        this.logger = requireNonNull(logger, "logger");
        this.level = requireNonNull(level, "level");
    }

    @Override
    public void queryWritten(InetSocketAddress dnsServerAddress, Future<Void> future) {
        this.dnsServerAddress = dnsServerAddress;
    }

    @Override
    public void queryCancelled(int queriesRemaining) {
        if (dnsServerAddress != null) {
            logger.atLevel(level).log("from {} : {} cancelled with {} queries remaining", dnsServerAddress, question,
                        queriesRemaining);
        } else {
            logger.atLevel(level).log("{} query never written and cancelled with {} queries remaining", question,
                        queriesRemaining);
        }
    }

    @Override
    public DnsQueryLifecycleObserver queryRedirected(List<InetSocketAddress> nameServers) {
        logger.atLevel(level).log("from {} : {} redirected", dnsServerAddress, question);
        return this;
    }

    @Override
    public DnsQueryLifecycleObserver queryCNAMEd(DnsQuestion cnameQuestion) {
        logger.atLevel(level).log("from {} : {} CNAME question {}", dnsServerAddress, question, cnameQuestion);
        return this;
    }

    @Override
    public DnsQueryLifecycleObserver queryNoAnswer(DnsResponseCode code) {
        logger.atLevel(level).log("from {} : {} no answer {}", dnsServerAddress, question, code);
        return this;
    }

    @Override
    public void queryFailed(Throwable cause) {
        if (dnsServerAddress != null) {
            logger.atLevel(level).log("from {} : {} failure", dnsServerAddress, question, cause);
        } else {
            logger.atLevel(level).log("{} query never written and failed", question, cause);
        }
    }

    @Override
    public void querySucceed() {
    }
}

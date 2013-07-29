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
package io.netty.dns.decoder;

import io.netty.handler.codec.dns.DnsEntry;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResource;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles the decoding of resource records. Some default decoders are mapped to
 * their resource types in the map {@code decoders}.
 */
public final class RecordDecoderFactory {

    private static RecordDecoderFactory factory = new RecordDecoderFactory(null);

    /**
     * Returns the active {@link RecordDecoderFactory}, which is the same as the
     * default factory if it has not been changed by the user.
     */
    public static RecordDecoderFactory getFactory() {
        return factory;
    }

    /**
     * Sets the active {@link RecordDecoderFactory} to be used for decoding
     * resource records.
     *
     * @param factory
     *            the {@link RecordDecoderFactory} to use
     */
    public static void setFactory(RecordDecoderFactory factory) {
        if (factory == null) {
            throw new NullPointerException("Cannot set record decoder factory to null.");
        }
        RecordDecoderFactory.factory = factory;
    }

    private final Map<Integer, RecordDecoder<?>> decoders = new HashMap<Integer, RecordDecoder<?>>();

    /**
     * Creates a new {@link RecordDecoderFactory} only using the default
     * decoders.
     */
    public RecordDecoderFactory() {
        this(true, null);
    }

    /**
     * Creates a new {@link RecordDecoderFactory} using the default decoders and
     * custom decoders (custom decoders override defaults).
     *
     * @param customDecoders
     *            user supplied resource record decoders, mapping the resource
     *            record's type to the decoder
     */
    public RecordDecoderFactory(Map<Integer, RecordDecoder<?>> customDecoders) {
        this(true, customDecoders);
    }

    /**
     * Creates a {@link RecordDecoderFactory} using either custom resource
     * record decoders, default decoders, or both. If a custom decoder has the
     * same record type as a default decoder, the default decoder is overriden.
     *
     * @param useDefaultDecoders
     *            if {@code true}, adds default decoders
     * @param customDecoders
     *            if not {@code null} or empty, adds custom decoders
     */
    public RecordDecoderFactory(boolean useDefaultDecoders, Map<Integer, RecordDecoder<?>> customDecoders) {
        if (!useDefaultDecoders && (customDecoders == null || customDecoders.isEmpty())) {
            throw new IllegalStateException("No decoders have been included to be used with this factory.");
        }
        if (useDefaultDecoders) {
            decoders.put(DnsEntry.TYPE_A, new AddressDecoder(4));
            decoders.put(DnsEntry.TYPE_AAAA, new AddressDecoder(16));
            decoders.put(DnsEntry.TYPE_MX, new MailExchangerDecoder());
            decoders.put(DnsEntry.TYPE_TXT, new TextDecoder());
            decoders.put(DnsEntry.TYPE_SRV, new ServiceDecoder());
            RecordDecoder<?> decoder = new DomainDecoder();
            decoders.put(DnsEntry.TYPE_NS, decoder);
            decoders.put(DnsEntry.TYPE_CNAME, decoder);
            decoders.put(DnsEntry.TYPE_PTR, decoder);
            decoders.put(DnsEntry.TYPE_SOA, new StartOfAuthorityDecoder());
        }
        if (customDecoders != null) {
            decoders.putAll(customDecoders);
        }
    }

    /**
     * Decodes a resource record and returns the result.
     *
     * @param type
     *            the type of resource record
     * @param response
     *            the DNS response that contains the resource record being
     *            decoded
     * @param resource
     *            the resource record being decoded
     * @return the decoded resource record
     */
    @SuppressWarnings("unchecked")
    public <T> T decode(int type, DnsResponse response, DnsResource resource) {
        RecordDecoder<?> decoder = decoders.get(type);
        if (decoder == null) {
            throw new IllegalStateException("Unsupported resource record type [id: " + type + "].");
        }
        T result = null;
        try {
            result = (T) decoder.decode(response, resource);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

}

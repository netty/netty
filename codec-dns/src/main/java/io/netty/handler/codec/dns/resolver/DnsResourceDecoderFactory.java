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
package io.netty.handler.codec.dns.resolver;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.dns.DnsEntry;
import io.netty.handler.codec.dns.DnsResource;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles the decoding of resource records. Some default decoders are mapped to
 * their resource types in the map {@code decoders}.
 */
public final class DnsResourceDecoderFactory {

    private static volatile DnsResourceDecoderFactory factory = new DnsResourceDecoderFactory(null);

    /**
     * Returns the active {@link DnsResourceDecoderFactory}, which is the same as the
     * default factory if it has not been changed by the user.
     */
    public static DnsResourceDecoderFactory getFactory() {
        return factory;
    }

    /**
     * Sets the active {@link DnsResourceDecoderFactory} to be used for decoding
     * resource records.
     *
     * @param factory
     *            the {@link DnsResourceDecoderFactory} to use
     */
    public static void setFactory(DnsResourceDecoderFactory factory) {
        if (factory == null) {
            throw new NullPointerException("Cannot set record decoder factory to null.");
        }
        DnsResourceDecoderFactory.factory = factory;
    }

    private final Map<Integer, DnsResourceDecoder<?>> decoders = new HashMap<Integer, DnsResourceDecoder<?>>();

    /**
     * Creates a new {@link DnsResourceDecoderFactory} only using the default
     * decoders.
     */
    public DnsResourceDecoderFactory() {
        this(true, null);
    }

    /**
     * Creates a new {@link DnsResourceDecoderFactory} using the default decoders and
     * custom decoders (custom decoders override defaults).
     *
     * @param customDecoders
     *            user supplied resource record decoders, mapping the resource
     *            record's type to the decoder
     */
    public DnsResourceDecoderFactory(Map<Integer, DnsResourceDecoder<?>> customDecoders) {
        this(true, customDecoders);
    }

    /**
     * Creates a {@link DnsResourceDecoderFactory} using either custom resource
     * record decoders, default decoders, or both. If a custom decoder has the
     * same record type as a default decoder, the default decoder is overriden.
     *
     * @param useDefaultDecoders
     *            if {@code true}, adds default decoders
     * @param customDecoders
     *            if not {@code null} or empty, adds custom decoders
     */
    public DnsResourceDecoderFactory(boolean useDefaultDecoders, Map<Integer, DnsResourceDecoder<?>> customDecoders) {
        if (!useDefaultDecoders && (customDecoders == null || customDecoders.isEmpty())) {
            throw new IllegalStateException("No decoders have been included to be used with this factory.");
        }
        if (useDefaultDecoders) {
            decoders.put(DnsEntry.TYPE_A, new AddressDecoder(4));
            decoders.put(DnsEntry.TYPE_AAAA, new AddressDecoder(16));
            decoders.put(DnsEntry.TYPE_MX, new MailExchangerDecoder());
            decoders.put(DnsEntry.TYPE_TXT, new TextDecoder());
            decoders.put(DnsEntry.TYPE_SRV, new ServiceDecoder());
            DnsResourceDecoder<?> decoder = new DomainDecoder();
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
     * @param resource
     *            the resource record being decoded
     * @return the decoded resource record
     */
    @SuppressWarnings("unchecked")
    public <T> T decode(DnsResource resource) {
        int type = resource.type();
        DnsResourceDecoder<?> decoder = decoders.get(type);
        if (decoder == null) {
            throw new IllegalStateException("Unsupported resource record type [id: " + type + "].");
        }
        try {
            return (T) decoder.decode(resource);
        } catch (Exception e) {
            throw new DecoderException(e);
        }
    }

}

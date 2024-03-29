/*
 * Copyright 2012 The Netty Project
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
package io.netty.example.http.ohttp;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.example.http.snoop.HttpSnoopClientHandler;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.ssl.SslContext;
import io.netty.incubator.codec.hpke.AEAD;
import io.netty.incubator.codec.hpke.AsymmetricCipherKeyPair;
import io.netty.incubator.codec.hpke.AsymmetricKeyParameter;
import io.netty.incubator.codec.hpke.KDF;
import io.netty.incubator.codec.hpke.KEM;
import io.netty.incubator.codec.hpke.OHttpCryptoProvider;
import io.netty.incubator.codec.hpke.bouncycastle.BouncyCastleOHttpCryptoProvider;
import io.netty.incubator.codec.ohttp.OHttpCiphersuite;
import io.netty.incubator.codec.ohttp.OHttpClientCodec;
import io.netty.incubator.codec.ohttp.OHttpClientCodec.EncapsulationParameters;
import io.netty.incubator.codec.ohttp.OHttpVersionDraft;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;

import java.util.function.Function;

public class OhttpSnoopClientInitializer extends ChannelInitializer<SocketChannel> {
    private final SslContext sslCtx;

    public OhttpSnoopClientInitializer(SslContext sslCtx) {
        this.sslCtx = sslCtx;
    }
    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();

        // Enable HTTPS if necessary.
        if (sslCtx != null) {
            p.addLast(sslCtx.newHandler(ch.alloc()));
        }

        // NOTE: To initialize both channels at the same time I would need to have the server private key,
        // i.e: search(OHttpCiphersuite ciphersuite = new OHttpCiphersuite(keyId,) in OhttpCodecTest.java
        // Initialize crypto provider
        OHttpCryptoProvider cryptoProvider = BouncyCastleOHttpCryptoProvider.INSTANCE;

        // Create a key pair from the provided private key (for testing purposes), extract public key to use in client
        byte keyId = 0x66;
        String privateKeyHex = "3c168975674b2fa8e465970b79c8dcf09f1c741626480bd4c6162fc5b6a98e1a";

        AsymmetricCipherKeyPair kpR = createX25519KeyPair(cryptoProvider, privateKeyHex);
        AsymmetricKeyParameter publicKey = cryptoProvider.deserializePublicKey(
                KEM.X25519_SHA256, kpR.publicParameters().encoded());

        OHttpClientCodec client = getoHttpClientCodec(keyId, cryptoProvider, publicKey);

        p.addLast(client);

        // Remove the following line if you don't want automatic content decompression.
        p.addLast(new HttpContentDecompressor());

        p.addLast(new HttpSnoopClientHandler());
    }

    private static OHttpClientCodec getoHttpClientCodec(byte keyId, OHttpCryptoProvider cryptoProvider,
                                                        AsymmetricKeyParameter publicKey) {
        OHttpCiphersuite ciphersuite = new OHttpCiphersuite(keyId,
                                                            KEM.X25519_SHA256,
                                                            KDF.HKDF_SHA256,
                                                            AEAD.AES_GCM128);

        Function<HttpRequest, EncapsulationParameters> encapsulationFunction = request -> {
            return EncapsulationParameters.newInstance(
                    OHttpVersionDraft.INSTANCE,
                    ciphersuite,
                    publicKey,
                    "/test?targetHost=elvaquero.xyz&targetPath=/upload);",
                    "cp4-canary.cloudflare.com");

        };

        return new OHttpClientCodec(cryptoProvider, encapsulationFunction);
    }

    static AsymmetricCipherKeyPair createX25519KeyPair(OHttpCryptoProvider cryptoProvider, String privateKeyHexBytes)  {
        X25519PrivateKeyParameters privateKey = new X25519PrivateKeyParameters(
                ByteBufUtil.decodeHexDump(privateKeyHexBytes));
        X25519PublicKeyParameters publicKey = privateKey.generatePublicKey();
        return cryptoProvider.deserializePrivateKey(
                KEM.X25519_SHA256, privateKey.getEncoded(), publicKey.getEncoded());
    }
}

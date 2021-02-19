/*
 * Copyright 2021 The Netty Project
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
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <openssl/ssl.h>

#include "netty_jni_util.h"
#include "netty_quic.h"
#include "netty_quic_boringssl.h"

// Add define if NETTY_BUILD_STATIC is defined so it is picked up in netty_jni_util.c
#ifdef NETTY_BUILD_STATIC
#define NETTY_JNI_UTIL_BUILD_STATIC
#endif

#define STATICALLY_CLASSNAME "io/netty/incubator/codec/quic/BoringSSLNativeStaticallyReferencedJniMethods"
#define CLASSNAME "io/netty/incubator/codec/quic/BoringSSL"

#define ERR_LEN 256

static jclass verifyCallbackClass = NULL;
static jmethodID verifyCallbackMethod = NULL;

static jclass certificateCallbackClass = NULL;
static jmethodID certificateCallbackMethod = NULL;

static jclass handshakeCompleteCallbackClass = NULL;
static jmethodID handshakeCompleteCallbackMethod = NULL;

static jclass byteArrayClass = NULL;
static jclass stringClass = NULL;

static int handshakeCompleteCallbackIdx = -1;
static int verifyCallbackIdx = -1;
static int certificateCallbackIdx = -1;
static int alpn_data_idx = -1;
static int crypto_buffer_pool_idx = -1;

static jint netty_boringssl_ssl_verify_none(JNIEnv* env, jclass clazz) {
    return SSL_VERIFY_NONE;
}

static jint netty_boringssl_ssl_verify_fail_if_no_peer_cert(JNIEnv* env, jclass clazz) {
    return SSL_VERIFY_FAIL_IF_NO_PEER_CERT;
}

static jint netty_boringssl_ssl_verify_peer(JNIEnv* env, jclass clazz) {
    return SSL_VERIFY_PEER;
}

static jint netty_boringssl_x509_v_ok(JNIEnv* env, jclass clazz) {
    return X509_V_OK;
}

static jint netty_boringssl_x509_v_err_cert_has_expired(JNIEnv* env, jclass clazz) {
    return X509_V_ERR_CERT_HAS_EXPIRED;
}

static jint netty_boringssl_x509_v_err_cert_not_yet_valid(JNIEnv* env, jclass clazz) {
    return X509_V_ERR_CERT_NOT_YET_VALID;
}

static jint netty_boringssl_x509_v_err_cert_revoked(JNIEnv* env, jclass clazz) {
    return X509_V_ERR_CERT_REVOKED;
}

static jint netty_boringssl_x509_v_err_unspecified(JNIEnv* env, jclass clazz) {
    return X509_V_ERR_UNSPECIFIED;
}

static STACK_OF(CRYPTO_BUFFER)* arrayToStack(JNIEnv* env, jobjectArray array, CRYPTO_BUFFER_POOL* pool) {
    if (array == NULL) {
        return NULL;
    }
    STACK_OF(CRYPTO_BUFFER) *stack = sk_CRYPTO_BUFFER_new_null();
    int arrayLen = (*env)->GetArrayLength(env, array);
    for (int i = 0; i < arrayLen; i++) {
        jbyteArray bytes = (*env)->GetObjectArrayElement(env, array, i);
        int data_len = (*env)->GetArrayLength(env, bytes);
        uint8_t* data = (uint8_t*) (*env)->GetByteArrayElements(env, bytes, 0);
        CRYPTO_BUFFER *buffer = CRYPTO_BUFFER_new(data, data_len, pool);
        if (buffer == NULL) {
            goto cleanup;
        }
        if (sk_CRYPTO_BUFFER_push(stack, buffer) <= 0) {
            // If we cant push for whatever reason ensure we release the buffer.
            CRYPTO_BUFFER_free(buffer);
            goto cleanup;
        }
    }
    return stack;
cleanup:
    sk_CRYPTO_BUFFER_pop_free(stack, CRYPTO_BUFFER_free);
    return NULL;
}

static jobjectArray stackToArray(JNIEnv *e, const STACK_OF(CRYPTO_BUFFER)* stack, int offset) {
    if (stack == NULL) {
        return NULL;
    }
    const int len = sk_CRYPTO_BUFFER_num(stack) - offset;
    if (len <= 0) {
        return NULL;
    }
    // Create the byte[][] array that holds all the certs
    jbyteArray array = (*e)->NewObjectArray(e, len, byteArrayClass, NULL);
    if (array == NULL) {
        return NULL;
    }

    for(int i = 0; i < len; i++) {
        CRYPTO_BUFFER* value = sk_CRYPTO_BUFFER_value(stack, i + offset);
        int length = CRYPTO_BUFFER_len(value);

        if (length <= 0) {
            return NULL;
        }

        jbyteArray bArray = (*e)->NewByteArray(e, length);
        if (bArray == NULL) {
            return NULL;
        }
        (*e)->SetByteArrayRegion(e, bArray, 0, length, (jbyte*) CRYPTO_BUFFER_data(value));
        (*e)->SetObjectArrayElement(e, array, i, bArray);
        // Delete the local reference as we not know how long the chain is and local references are otherwise
        // only freed once jni method returns.
        (*e)->DeleteLocalRef(e, bArray);
        bArray = NULL;
    }
    return array;
}


enum ssl_verify_result_t quic_SSL_cert_custom_verify(SSL* ssl, uint8_t *out_alert) {
    enum ssl_verify_result_t ret = ssl_verify_invalid;
    jint result = X509_V_ERR_UNSPECIFIED;
    JNIEnv *e = NULL;

    SSL_CTX* ctx = SSL_get_SSL_CTX(ssl);
    if (ctx == NULL) {
        goto complete;
    }

    if (quic_get_java_env(&e) != JNI_OK) {
        goto complete;
    }

    jobject verifyCallback = SSL_CTX_get_ex_data(ctx, verifyCallbackIdx);
    if (verifyCallback == NULL) {
        goto complete;
    }

    const STACK_OF(CRYPTO_BUFFER) *chain = SSL_get0_peer_certificates(ssl);
    if (chain == NULL) {
        goto complete;
    }

    // Create the byte[][] array that holds all the certs
    jobjectArray array = stackToArray(e, chain, 0);
    if (array == NULL) {
        goto complete;
    }

    const char* authentication_method = NULL;
    STACK_OF(SSL_CIPHER) *ciphers = SSL_get_ciphers(ssl);
    if (ciphers == NULL || sk_SSL_CIPHER_num(ciphers) <= 0) {
         // No cipher available so return UNKNOWN.
         authentication_method = "UNKNOWN";
    } else {
         authentication_method = SSL_CIPHER_get_kx_name(sk_SSL_CIPHER_value(ciphers, 0));
         if (authentication_method == NULL) {
              authentication_method = "UNKNOWN";
         }
    }

    jstring authMethodString = (*e)->NewStringUTF(e, authentication_method);
    if (authMethodString == NULL) {
        goto complete;
    }

    // Execute the java callback
    result = (*e)->CallIntMethod(e, verifyCallback, verifyCallbackMethod, (jlong)ssl, array, authMethodString);

    if ((*e)->ExceptionCheck(e) == JNI_TRUE) {
        (*e)->ExceptionClear(e);
        result = X509_V_ERR_UNSPECIFIED;
        goto complete;
    }

    int len = (*e)->GetArrayLength(e, array);
    // If we failed to verify for an unknown reason (currently this happens if we can't find a common root) then we should
    // fail with the same status as recommended in the OpenSSL docs https://www.openssl.org/docs/man1.0.2/ssl/SSL_set_verify.html
    if (result == X509_V_ERR_UNSPECIFIED && len < sk_CRYPTO_BUFFER_num(chain)) {
        result = X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT_LOCALLY;
    }
complete:
    if (result == X509_V_OK) {
        ret = ssl_verify_ok;
    } else {
        ret = ssl_verify_invalid;
        *out_alert = SSL_alert_from_verify_result(result);
    }
    return ret;
}

static jbyteArray keyTypes(JNIEnv* e, SSL* ssl) {
    jbyte* ctype_bytes = NULL;
    int ctype_num = SSL_get0_certificate_types(ssl, (const uint8_t **) &ctype_bytes);
    if (ctype_num <= 0) {
        // No idea what we should use... Let the caller handle it.
        return NULL;
    }
    jbyteArray types = (*e)->NewByteArray(e, ctype_num);
    if (types == NULL) {
        return NULL;
    }
    (*e)->SetByteArrayRegion(e, types, 0, ctype_num, ctype_bytes);
    return types;
}

// See https://www.openssl.org/docs/man1.0.2/man3/SSL_set_cert_cb.html for return values.
static int quic_certificate_cb(SSL* ssl, void* arg) {
    JNIEnv *e = NULL;
    CRYPTO_BUFFER** certs = NULL;
    jlong* elements = NULL;
    int ret = 0;
    int free = 0;

    if (quic_get_java_env(&e) != JNI_OK) {
        goto done;
    }

    jobjectArray authMethods = NULL;
    jobjectArray issuers = NULL;
    jbyteArray types = NULL;
    if (SSL_is_server(ssl) == 1) {
        const STACK_OF(SSL_CIPHER) *ciphers = SSL_get_ciphers(ssl);
        int len = sk_SSL_CIPHER_num(ciphers);
        authMethods = (*e)->NewObjectArray(e, len, stringClass, NULL);
        if (authMethods == NULL) {
            goto done;
        }

        for (int i = 0; i < len; i++) {
            jstring methodString = (*e)->NewStringUTF(e, SSL_CIPHER_get_kx_name(sk_SSL_CIPHER_value(ciphers, i)));
            if (methodString == NULL) {
                // Out of memory
                goto done;
            }
            (*e)->SetObjectArrayElement(e, authMethods, i, methodString);
        }

        // TODO: Consider filling these somehow.
        types = NULL;
        issuers = NULL;
    } else {
        authMethods = NULL;
        types = keyTypes(e, ssl);
        issuers = stackToArray(e, SSL_get0_server_requested_CAs(ssl), 0);
    }

    // Execute the java callback
    jlongArray result = (*e)->CallObjectMethod(e, arg, certificateCallbackMethod, (jlong) ssl, types, issuers, authMethods);

    // Check if java threw an exception and if so signal back that we should not continue with the handshake.
    if ((*e)->ExceptionCheck(e) == JNI_TRUE) {
        (*e)->ExceptionClear(e);
        goto done;
    }
    if (result == NULL) {
        goto done;
    }

    int arrayLen = (*e)->GetArrayLength(e, result);
    if (arrayLen != 3) {
        goto done;
    }

    elements = (*e)->GetLongArrayElements(e, result, NULL);
    EVP_PKEY* pkey = (EVP_PKEY *) elements[0];
    const STACK_OF(CRYPTO_BUFFER) *cchain = (STACK_OF(CRYPTO_BUFFER) *) elements[1];
    free = elements[2];

    int numCerts = sk_CRYPTO_BUFFER_num(cchain);
    if (numCerts == 0) {
        goto done;
    }
    certs = OPENSSL_malloc(sizeof(CRYPTO_BUFFER*) * numCerts);

    if (certs == NULL) {
        goto done;
    }

    for (int i = 0; i < numCerts; i++) {
        certs[i] = sk_CRYPTO_BUFFER_value(cchain, i);
    }

    if (SSL_set_chain_and_key(ssl, certs, numCerts, pkey, NULL) > 0) {
        ret = 1;
    }
done:
    if (elements != NULL) {
        (*e)->ReleaseLongArrayElements(e, result, elements, 0);
    }
    OPENSSL_free(certs);
    if (free == 1) {
        EVP_PKEY_free(pkey);
        sk_CRYPTO_BUFFER_pop_free((STACK_OF(CRYPTO_BUFFER) *) cchain, CRYPTO_BUFFER_free);
    }
    return ret;
}

struct alpn_data {
    unsigned char* proto_data;
    int proto_len;
} typedef alpn_data;

int BoringSSL_callback_alpn_select_proto(SSL* ssl, const unsigned char **out, unsigned char *outlen,
        const unsigned char *in, unsigned int inlen, void *arg) {
    unsigned int i = 0;
    unsigned char target_proto_len;
    unsigned char *p = NULL;
    const unsigned char *end = NULL;
    unsigned char *proto = NULL;
    unsigned char proto_len;
    alpn_data* data = (alpn_data*) arg;
    int supported_protos_len = data->proto_len;
    unsigned char* supported_protos = data->proto_data;
    while (i < supported_protos_len) {
        target_proto_len = *supported_protos;
        ++supported_protos;

        p = (unsigned char*) in;
        end = p + inlen;

        while (p < end) {
            proto_len = *p;
            proto = ++p;

            if (proto + proto_len <= end && target_proto_len == proto_len &&
                    memcmp(supported_protos, proto, proto_len) == 0) {

                // We found a match, so set the output and return with OK!
                *out = proto;
                *outlen = proto_len;

                return SSL_TLSEXT_ERR_OK;
            }
            // Move on to the next protocol.
            p += proto_len;
        }

        // increment len and pointers.
        i += target_proto_len;
        supported_protos += target_proto_len;
    }
    return SSL_TLSEXT_ERR_NOACK;
}

static jbyteArray netty_boringssl_SSL_getSessionId(JNIEnv* env, const SSL* ssl_) {
    SSL_SESSION *session = SSL_get_session(ssl_);
    if (session == NULL) {
        return NULL;
    }

    unsigned int len = 0;
    const unsigned char *session_id = SSL_SESSION_get_id(session, &len);
    if (len == 0 || session_id == NULL) {
        return NULL;
    }

    jbyteArray bArray = (*env)->NewByteArray(env, len);
    if (bArray == NULL) {
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, bArray, 0, len, (jbyte*) session_id);
    return bArray;
}


static jstring netty_boringssl_SSL_getCipher(JNIEnv* env, const SSL* ssl) {
    const char* cipher = SSL_get_cipher(ssl);
    if (cipher == NULL) {
        return NULL;
    }
    return (*env)->NewStringUTF(env, cipher);
}

static jstring netty_boringssl_SSL_getVersion(JNIEnv* env, const SSL* ssl) {
    const char* version = SSL_get_version(ssl);
    if (version == NULL) {
        return NULL;
    }
    return (*env)->NewStringUTF(env, version);
}

static jobjectArray netty_boringssl_SSL_getPeerCertChain(JNIEnv* env, const SSL* ssl_) {
    // Get a stack of all certs in the chain.
    const STACK_OF(CRYPTO_BUFFER) *chain = SSL_get0_peer_certificates(ssl_);
    if (chain == NULL) {
        return NULL;
    }
    int offset = SSL_is_server(ssl_) == 1 ? 1 : 0;
    return stackToArray(env, chain, offset);
}

static jbyteArray netty_boringssl_SSL_getPeerCertificate(JNIEnv* env, const SSL* ssl_) {
    // Get a stack of all certs in the chain, the first is the leaf.
    const STACK_OF(CRYPTO_BUFFER) *certs = SSL_get0_peer_certificates(ssl_);
    if (certs == NULL || sk_CRYPTO_BUFFER_num(certs) <= 0) {
        return NULL;
    }
    const CRYPTO_BUFFER *leafCert = sk_CRYPTO_BUFFER_value(certs, 0);
    int length = CRYPTO_BUFFER_len(leafCert);

    jbyteArray bArray = (*env)->NewByteArray(env, length);
    if (bArray == NULL) {
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, bArray, 0, length, (jbyte*) CRYPTO_BUFFER_data(leafCert));
    return bArray;
}


static jlong netty_boringssl_SSL_getTime(JNIEnv* env, const SSL* ssl_) {
    SSL_SESSION *session = SSL_get_session(ssl_);
    if (session == NULL) {
        // BoringSSL does not protect against a NULL session. OpenSSL
        // returns 0 if the session is NULL, so do that here.
        return 0;
    }

    return SSL_get_time(session);
}

static jlong netty_boringssl_SSL_getTimeout(JNIEnv* env, const SSL* ssl_) {
    SSL_SESSION *session = SSL_get_session(ssl_);
    if (session == NULL) {
        // BoringSSL does not protect against a NULL session. OpenSSL
        // returns 0 if the session is NULL, so do that here.
        return 0;
    }

    return SSL_get_timeout(session);
}

static jbyteArray netty_boringssl_SSL_getAlpnSelected(JNIEnv* env, const SSL* ssl_) {
    const unsigned char *proto = NULL;
    unsigned int proto_len = 0;

    SSL_get0_alpn_selected(ssl_, &proto, &proto_len);
    if (proto == NULL) {
        return NULL;
    }
    jbyteArray bytes = (*env)->NewByteArray(env, proto_len);
    if (bytes == NULL) {
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, bytes, 0, proto_len, (jbyte *)proto);
    return bytes;
}

void quic_SSL_info_callback(const SSL *ssl, int type, int value) {
    if (type == SSL_CB_HANDSHAKE_DONE) {
        SSL_CTX* ctx = SSL_get_SSL_CTX(ssl);
        if (ctx == NULL) {
            return;
        }

        JNIEnv* e = NULL;
        if (quic_get_java_env(&e) != JNI_OK) {
            return;
        }

        jobject handshakeCompleteCallback = SSL_CTX_get_ex_data(ctx, handshakeCompleteCallbackIdx);
        if (handshakeCompleteCallback == NULL) {
            return;
        }

        jbyteArray session_id = netty_boringssl_SSL_getSessionId(e, ssl);
        jstring cipher = netty_boringssl_SSL_getCipher(e, ssl);
        jstring version = netty_boringssl_SSL_getVersion(e, ssl);
        jbyteArray peerCert = netty_boringssl_SSL_getPeerCertificate(e, ssl);
        jobjectArray certChain = netty_boringssl_SSL_getPeerCertChain(e, ssl);
        jlong creationTime = netty_boringssl_SSL_getTime(e, ssl);
        jlong timeout = netty_boringssl_SSL_getTimeout(e, ssl);
        jbyteArray alpnSelected = netty_boringssl_SSL_getAlpnSelected(e, ssl);

        // Execute the java callback
        (*e)->CallVoidMethod(e, handshakeCompleteCallback, handshakeCompleteCallbackMethod,
                 (jlong) ssl, session_id, cipher, version, peerCert, certChain, creationTime, timeout, alpnSelected);
    }
}
static jlong netty_boringssl_SSLContext_new0(JNIEnv* env, jclass clazz, jboolean server, jbyteArray alpn_protos, jobject handshakeCompleteCallback, jobject certificateCallback, jobject verifyCallback, int verifyMode, jobjectArray subjectNames) {
    jobject handshakeCompleteCallbackRef = NULL;
    jobject certificateCallbackRef = NULL;
    jobject verifyCallbackRef = NULL;

    if ((handshakeCompleteCallbackRef = (*env)->NewGlobalRef(env, handshakeCompleteCallback)) == NULL) {
        goto error;
    }

    if ((certificateCallbackRef = (*env)->NewGlobalRef(env, certificateCallback)) == NULL) {
        goto error;
    }

    if ((verifyCallbackRef = (*env)->NewGlobalRef(env, verifyCallback)) == NULL) {
        goto error;
    }

    SSL_CTX *ctx = SSL_CTX_new(TLS_with_buffers_method());
    // When using BoringSSL we want to use CRYPTO_BUFFER to reduce memory usage and minimize overhead as we do not need
    // X509* at all and just need the raw bytes of the certificates to construct our Java X509Certificate.
    //
    // See https://github.com/google/boringssl/blob/chromium-stable/PORTING.md#crypto_buffer
    SSL_CTX_set_min_proto_version(ctx, TLS1_3_VERSION);
    SSL_CTX_set_max_proto_version(ctx, TLS1_3_VERSION);

    // Automatically release buffers
    SSL_CTX_set_mode(ctx, SSL_MODE_RELEASE_BUFFERS);

    // Set callback which will inform when handshake is done
    SSL_CTX_set_ex_data(ctx, handshakeCompleteCallbackIdx, handshakeCompleteCallbackRef);
    SSL_CTX_set_info_callback(ctx, quic_SSL_info_callback);

    // So we can access this in quic_SSL_cert_custom_verify
    SSL_CTX_set_ex_data(ctx, verifyCallbackIdx, verifyCallbackRef);
    SSL_CTX_set_custom_verify(ctx, verifyMode, quic_SSL_cert_custom_verify);

    SSL_CTX_set_ex_data(ctx, certificateCallbackIdx, certificateCallbackRef);
    SSL_CTX_set_cert_cb(ctx, quic_certificate_cb, certificateCallbackRef);

    // Use a pool for our certificates so we can share these across connections.
    SSL_CTX_set_ex_data(ctx, crypto_buffer_pool_idx, CRYPTO_BUFFER_POOL_new());

    STACK_OF(CRYPTO_BUFFER) *names = arrayToStack(env, subjectNames, NULL);
    if (names != NULL) {
        SSL_CTX_set0_client_CAs(ctx, names);
    }

    if (alpn_protos != NULL) {
        int alpn_length = (*env)->GetArrayLength(env, alpn_protos);
        alpn_data* alpn = (alpn_data*) OPENSSL_malloc(sizeof(alpn_data));
        if (alpn != NULL) {
            // Fill the alpn_data struct
            alpn->proto_data = OPENSSL_malloc(alpn_length);
            alpn->proto_len = alpn_length;
            (*env)->GetByteArrayRegion(env, alpn_protos, 0, alpn_length, (jbyte*) alpn->proto_data);

            SSL_CTX_set_ex_data(ctx, alpn_data_idx, alpn);
            if (server == JNI_TRUE) {
                SSL_CTX_set_alpn_select_cb(ctx, BoringSSL_callback_alpn_select_proto, (void*) alpn);
            } else {
                SSL_CTX_set_alpn_protos(ctx, alpn->proto_data, alpn->proto_len);
            }
        }
    }
    return (jlong) ctx;
error:
    if (handshakeCompleteCallbackRef != NULL) {
        (*env)->DeleteGlobalRef(env, handshakeCompleteCallbackRef);
    }
    if (certificateCallbackRef != NULL) {
        (*env)->DeleteGlobalRef(env, certificateCallbackRef);
    }
    if (certificateCallbackRef != NULL) {
        (*env)->DeleteGlobalRef(env, certificateCallbackRef);
    }
    return -1;
}

static void netty_boringssl_SSLContext_free(JNIEnv* env, jclass clazz, long ctx) {
    SSL_CTX* ssl_ctx = (SSL_CTX*) ctx;

    jobject handshakeCompleteCallbackRef = SSL_CTX_get_ex_data(ssl_ctx, handshakeCompleteCallbackIdx);
    if (handshakeCompleteCallbackRef != NULL) {
        (*env)->DeleteGlobalRef(env, handshakeCompleteCallbackRef);
    }
    jobject verifyCallbackRef = SSL_CTX_get_ex_data(ssl_ctx, verifyCallbackIdx);
    if (verifyCallbackRef != NULL) {
        (*env)->DeleteLocalRef(env, verifyCallbackRef);
    }
    jobject certificateCallbackRef = SSL_CTX_get_ex_data(ssl_ctx, certificateCallbackIdx);
    if (certificateCallbackRef != NULL) {
        (*env)->DeleteLocalRef(env, certificateCallbackRef);
    }

    alpn_data* data = SSL_CTX_get_ex_data(ssl_ctx, alpn_data_idx);
    OPENSSL_free(data);

    CRYPTO_BUFFER_POOL* pool = SSL_CTX_get_ex_data(ssl_ctx, crypto_buffer_pool_idx);
    SSL_CTX_free(ssl_ctx);

    // The pool should be freed last in case that the SSL_CTX has a reference to things tha are stored in the
    // pool itself. Otherwise we may see an assert error when trying to call CRYPTO_BUFFER_POOL_free.
    if (pool != NULL) {
        CRYPTO_BUFFER_POOL_free(pool);
    }
}

static jlong netty_boringssl_SSLContext_setSessionCacheTimeout(JNIEnv* env, jclass clazz, jlong ctx, jlong timeout){
    return SSL_CTX_set_timeout((SSL_CTX*) ctx, timeout);
}

static jlong netty_boringssl_SSLContext_setSessionCacheSize(JNIEnv* env, jclass clazz, jlong ctx, jlong size) {
    if (size >= 0) {
        SSL_CTX* ssl_ctx = (SSL_CTX*) ctx;
        // Caching only works on the server side for now.
        SSL_CTX_set_session_cache_mode(ssl_ctx, SSL_SESS_CACHE_SERVER);
        return SSL_CTX_sess_set_cache_size(ssl_ctx, size);
    }

    return 0;
}

static void netty_boringssl_SSLContext_set_early_data_enabled(JNIEnv* env, jclass clazz, jlong ctx, jboolean enabled){
    SSL_CTX_set_early_data_enabled((SSL_CTX*) ctx, enabled == JNI_TRUE ? 1 : 0);
}

jlong netty_boringssl_SSL_new0(JNIEnv* env, jclass clazz, jlong ctx, jboolean server, jstring hostname) {
    SSL* ssl = SSL_new((SSL_CTX*) ctx);

    if (ssl == NULL) {
        return -1;
    }

    if (server == JNI_TRUE) {
        SSL_set_accept_state(ssl);
    } else {
        SSL_set_connect_state(ssl);
        if (hostname != NULL) {
            const char *charHostname = (*env)->GetStringUTFChars(env, hostname, 0);
            SSL_set_tlsext_host_name(ssl, charHostname);
            (*env)->ReleaseStringUTFChars(env, hostname, charHostname);
        }
    }

    return (jlong) ssl;
}

void netty_boringssl_SSL_free(JNIEnv* env, jclass clazz, jlong ssl) {
    SSL_free((SSL *) ssl);
}

int netty_boringssl_password_callback(char *buf, int bufsiz, int verify, void *cb) {
    char *password = (char *) cb;
    if (password == NULL) {
        return 0;
    }
    strncpy(buf, password, bufsiz);
    return (int) strlen(buf);
}

jlong netty_boringssl_EVP_PKEY_parse(JNIEnv* env, jclass clazz, jbyteArray array, jstring password) {
    int dataLen = (*env)->GetArrayLength(env, array);
    char* data = (char*) (*env)->GetByteArrayElements(env, array, 0);
    BIO* bio = BIO_new_mem_buf(data, dataLen);

    const char *charPass;
    if (password == NULL) {
        charPass = NULL;
    } else {
        charPass = (*env)->GetStringUTFChars(env, password, 0);
    }

    EVP_PKEY *key = PEM_read_bio_PrivateKey(bio, NULL,
                    (pem_password_cb *)netty_boringssl_password_callback,
                    (void *)charPass);
    BIO_free(bio);
    if (charPass != NULL) {
        (*env)->ReleaseStringUTFChars(env, password, charPass);
    }
    if (key == NULL) {
        return -1;
    }
    return (jlong) key;
}

void netty_boringssl_EVP_PKEY_free(JNIEnv* env, jclass clazz, jlong privateKey) {
    EVP_PKEY_free((EVP_PKEY*) privateKey); // Safe to call with NULL as well.
}

jlong netty_boringssl_CRYPTO_BUFFER_stack_new(JNIEnv* env, jclass clazz, jlong ssl, jobjectArray x509Chain){
    CRYPTO_BUFFER_POOL* pool = NULL;
    SSL_CTX* ctx = SSL_get_SSL_CTX((SSL*) ssl);
    if (ctx != NULL) {
        pool = SSL_CTX_get_ex_data(ctx, crypto_buffer_pool_idx);
    }
    STACK_OF(CRYPTO_BUFFER) *chain = arrayToStack(env, x509Chain, pool);
    if (chain == NULL) {
        return 0;
    }
    return (jlong) chain;

}

void netty_boringssl_CRYPTO_BUFFER_stack_free(JNIEnv* env, jclass clazz, jlong chain) {
    sk_CRYPTO_BUFFER_pop_free((STACK_OF(CRYPTO_BUFFER) *) chain, CRYPTO_BUFFER_free);
}

jstring netty_boringssl_ERR_last_error(JNIEnv* env, jclass clazz) {
    char buf[ERR_LEN];
    unsigned long err = ERR_get_error();
    if (err == 0) {
        return NULL;
    }
    ERR_error_string_n(err, buf, ERR_LEN);
    return (*env)->NewStringUTF(env, buf);
}


// JNI Registered Methods End

// JNI Method Registration Table Begin
static const JNINativeMethod statically_referenced_fixed_method_table[] = {
  { "ssl_verify_none", "()I", (void *) netty_boringssl_ssl_verify_none },
  { "ssl_verify_fail_if_no_peer_cert", "()I", (void *) netty_boringssl_ssl_verify_fail_if_no_peer_cert },
  { "ssl_verify_peer", "()I", (void *) netty_boringssl_ssl_verify_peer },
  { "x509_v_ok", "()I", (void *) netty_boringssl_x509_v_ok },
  { "x509_v_err_cert_has_expired", "()I", (void *) netty_boringssl_x509_v_err_cert_has_expired },
  { "x509_v_err_cert_not_yet_valid", "()I", (void *) netty_boringssl_x509_v_err_cert_not_yet_valid },
  { "x509_v_err_cert_revoked", "()I", (void *) netty_boringssl_x509_v_err_cert_revoked },
  { "x509_v_err_unspecified", "()I", (void *) netty_boringssl_x509_v_err_unspecified }
};

static const jint statically_referenced_fixed_method_table_size = sizeof(statically_referenced_fixed_method_table) / sizeof(statically_referenced_fixed_method_table[0]);
static const JNINativeMethod fixed_method_table[] = {
  { "SSLContext_new0", "(Z[BLjava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;I[[B)J", (void *) netty_boringssl_SSLContext_new0 },
  { "SSLContext_free", "(J)V", (void *) netty_boringssl_SSLContext_free },
  { "SSLContext_setSessionCacheTimeout", "(JJ)J", (void *) netty_boringssl_SSLContext_setSessionCacheTimeout },
  { "SSLContext_setSessionCacheSize", "(JJ)J", (void *) netty_boringssl_SSLContext_setSessionCacheSize },
  { "SSLContext_set_early_data_enabled", "(JZ)V", (void *) netty_boringssl_SSLContext_set_early_data_enabled },
  { "SSL_new0", "(JZLjava/lang/String;)J", (void *) netty_boringssl_SSL_new0 },
  { "SSL_free", "(J)V", (void *) netty_boringssl_SSL_free },
  { "EVP_PKEY_parse", "([BLjava/lang/String;)J", (void *) netty_boringssl_EVP_PKEY_parse },
  { "EVP_PKEY_free", "(J)V", (void *) netty_boringssl_EVP_PKEY_free },
  { "CRYPTO_BUFFER_stack_new", "(J[[B)J", (void *) netty_boringssl_CRYPTO_BUFFER_stack_new },
  { "CRYPTO_BUFFER_stack_free", "(J)V", (void *) netty_boringssl_CRYPTO_BUFFER_stack_free },
  { "ERR_last_error", "()Ljava/lang/String;", (void *) netty_boringssl_ERR_last_error }
};

static const jint fixed_method_table_size = sizeof(fixed_method_table) / sizeof(fixed_method_table[0]);

// JNI Method Registration Table End

jint netty_boringssl_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    int ret = JNI_ERR;
    int staticallyRegistered = 0;
    int nativeRegistered = 0;
    char* name = NULL;

    // We must register the statically referenced methods first!
    if (netty_jni_util_register_natives(env,
            packagePrefix,
            STATICALLY_CLASSNAME,
            statically_referenced_fixed_method_table,
            statically_referenced_fixed_method_table_size) != 0) {
        goto done;
    }
    staticallyRegistered = 1;

    if (netty_jni_util_register_natives(env,
            packagePrefix,
            CLASSNAME,
            fixed_method_table,
            fixed_method_table_size) != 0) {
        goto done;
    }
    nativeRegistered = 1;
    // Initialize this module


    NETTY_JNI_UTIL_LOAD_CLASS(env, byteArrayClass, "[B", done);
    NETTY_JNI_UTIL_LOAD_CLASS(env, stringClass, "Ljava/lang/String;", done);

    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/incubator/codec/quic/BoringSSLCertificateCallback", name, done);
    NETTY_JNI_UTIL_LOAD_CLASS(env, certificateCallbackClass, name, done);
    NETTY_JNI_UTIL_GET_METHOD(env, certificateCallbackClass, certificateCallbackMethod, "handle", "(J[B[[B[Ljava/lang/String;)[J", done);
    free((void*) packagePrefix);
    packagePrefix = NULL;


    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/incubator/codec/quic/BoringSSLCertificateVerifyCallback", name, done);
    NETTY_JNI_UTIL_LOAD_CLASS(env, verifyCallbackClass, name, done);
    NETTY_JNI_UTIL_GET_METHOD(env, verifyCallbackClass, verifyCallbackMethod, "verify", "(J[[BLjava/lang/String;)I", done);


    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/incubator/codec/quic/BoringSSLHandshakeCompleteCallback", name, done);
    NETTY_JNI_UTIL_LOAD_CLASS(env, handshakeCompleteCallbackClass, name, done);
    NETTY_JNI_UTIL_GET_METHOD(env, handshakeCompleteCallbackClass, handshakeCompleteCallbackMethod, "handshakeComplete", "(J[BLjava/lang/String;Ljava/lang/String;[B[[BJJ[B)V", done);

    verifyCallbackIdx = SSL_CTX_get_ex_new_index(0, NULL, NULL, NULL, NULL);
    certificateCallbackIdx = SSL_CTX_get_ex_new_index(0, NULL, NULL, NULL, NULL);
    handshakeCompleteCallbackIdx = SSL_CTX_get_ex_new_index(0, NULL, NULL, NULL, NULL);
    alpn_data_idx = SSL_CTX_get_ex_new_index(0, NULL, NULL, NULL, NULL);
    crypto_buffer_pool_idx = SSL_CTX_get_ex_new_index(0, NULL, NULL, NULL, NULL);
    ret = NETTY_JNI_UTIL_JNI_VERSION;
done:
    if (ret == JNI_ERR) {
        if (staticallyRegistered == 1) {
            netty_jni_util_unregister_natives(env, packagePrefix, STATICALLY_CLASSNAME);
        }
        if (nativeRegistered == 1) {
            netty_jni_util_unregister_natives(env, packagePrefix, CLASSNAME);
        }

        NETTY_JNI_UTIL_UNLOAD_CLASS(env, byteArrayClass);
        NETTY_JNI_UTIL_UNLOAD_CLASS(env, stringClass);
        NETTY_JNI_UTIL_UNLOAD_CLASS(env, certificateCallbackClass);
        NETTY_JNI_UTIL_UNLOAD_CLASS(env, verifyCallbackClass);
        NETTY_JNI_UTIL_UNLOAD_CLASS(env, handshakeCompleteCallbackClass);

    }
    return ret;
}

void netty_boringssl_JNI_OnUnload(JNIEnv* env, const char* packagePrefix) {
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, byteArrayClass);
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, certificateCallbackClass);
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, verifyCallbackClass);
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, handshakeCompleteCallbackClass);


    netty_jni_util_unregister_natives(env, packagePrefix, STATICALLY_CLASSNAME);
    netty_jni_util_unregister_natives(env, packagePrefix, CLASSNAME);
}

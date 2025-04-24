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
#include <openssl/rand.h>
#include <openssl/hmac.h>

#include "netty_jni_util.h"
#include "netty_quic.h"
#include "netty_quic_boringssl.h"

// Add define if NETTY_BUILD_STATIC is defined so it is picked up in netty_jni_util.c
#ifdef NETTY_BUILD_STATIC
#define NETTY_JNI_UTIL_BUILD_STATIC
#endif

#define STATICALLY_CLASSNAME "io/netty/handler/codec/quic/BoringSSLNativeStaticallyReferencedJniMethods"
#define CLASSNAME "io/netty/handler/codec/quic/BoringSSL"

#define ERR_LEN 256

// For encoding of keys see BoringSSLSessionTicketCallback.setSessionTicketKeys(...)
#define SSL_SESSION_TICKET_KEY_NAME_OFFSET 1
#define SSL_SESSION_TICKET_KEY_HMAC_OFFSET 17
#define SSL_SESSION_TICKET_KEY_EVP_OFFSET 33
#define SSL_SESSION_TICKET_KEY_NAME_LEN 16
#define SSL_SESSION_TICKET_AES_KEY_LEN  16
#define SSL_SESSION_TICKET_HMAC_KEY_LEN 16
#define SSL_SESSION_TICKET_KEY_LEN 49

static jweak sslTaskClassWeak = NULL;
static jmethodID sslTaskDestroyMethod = NULL;
static jfieldID sslTaskReturnValue = NULL;
static jfieldID sslTaskComplete = NULL;

static jweak sslPrivateKeyMethodTaskClassWeak = NULL;
static jfieldID sslPrivateKeyMethodTaskResultBytesField = NULL;

static jweak sslPrivateKeyMethodSignTaskClassWeak = NULL;
static jmethodID sslPrivateKeyMethodSignTaskInitMethod = NULL;

static jweak sslPrivateKeyMethodDecryptTaskClassWeak = NULL;
static jmethodID sslPrivateKeyMethodDecryptTaskInitMethod = NULL;

static jweak verifyTaskClassWeak = NULL;
static jmethodID verifyTaskClassInitMethod = NULL;

static jweak certificateTaskClassWeak = NULL;
static jmethodID certificateTaskClassInitMethod = NULL;
static jfieldID certificateTaskClassChainField;
static jfieldID certificateTaskClassKeyField;

static jweak handshakeCompleteCallbackClassWeak = NULL;
static jmethodID handshakeCompleteCallbackMethod = NULL;

static jweak servernameCallbackClassWeak = NULL;
static jmethodID servernameCallbackMethod = NULL;

static jweak keylogCallbackClassWeak = NULL;
static jmethodID keylogCallbackMethod = NULL;

static jweak sessionCallbackClassWeak = NULL;
static jmethodID sessionCallbackMethod = NULL;

static jweak sessionTicketCallbackClassWeak = NULL;
static jmethodID sessionTicketCallbackMethod = NULL;

static jclass byteArrayClass = NULL;
static jclass stringClass = NULL;

static int handshakeCompleteCallbackIdx = -1;
static int verifyCallbackIdx = -1;
static int certificateCallbackIdx = -1;
static int servernameCallbackIdx = -1;
static int keylogCallbackIdx = -1;
static int sessionCallbackIdx = -1;
static int sslPrivateKeyMethodIdx = -1;
static int sslTaskIdx = -1;
static int sessionTicketCallbackIdx = -1;
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

static jint netty_boringssl_ssl_sign_rsa_pkcs_sha1(JNIEnv* env, jclass clazz) {
    return SSL_SIGN_RSA_PKCS1_SHA1;
}

static jint netty_boringssl_ssl_sign_rsa_pkcs_sha256(JNIEnv* env, jclass clazz) {
    return SSL_SIGN_RSA_PKCS1_SHA256;
}

static jint netty_boringssl_ssl_sign_rsa_pkcs_sha384(JNIEnv* env, jclass clazz) {
    return SSL_SIGN_RSA_PKCS1_SHA384;
}

static jint netty_boringssl_ssl_sign_rsa_pkcs_sha512(JNIEnv* env, jclass clazz) {
    return SSL_SIGN_RSA_PKCS1_SHA512;
}

static jint netty_boringssl_ssl_sign_ecdsa_pkcs_sha1(JNIEnv* env, jclass clazz) {
    return SSL_SIGN_ECDSA_SHA1;
}

static jint netty_boringssl_ssl_sign_ecdsa_secp256r1_sha256(JNIEnv* env, jclass clazz) {
    return SSL_SIGN_ECDSA_SECP256R1_SHA256;
}

static jint netty_boringssl_ssl_sign_ecdsa_secp384r1_sha384(JNIEnv* env, jclass clazz) {
    return SSL_SIGN_ECDSA_SECP384R1_SHA384;
}

static jint netty_boringssl_ssl_sign_ecdsa_secp521r1_sha512(JNIEnv* env, jclass clazz) {
    return SSL_SIGN_ECDSA_SECP521R1_SHA512;
}

static jint netty_boringssl_ssl_sign_rsa_pss_rsae_sha256(JNIEnv* env, jclass clazz) {
    return SSL_SIGN_RSA_PSS_RSAE_SHA256;
}

static jint netty_boringssl_ssl_sign_rsa_pss_rsae_sha384(JNIEnv* env, jclass clazz) {
    return SSL_SIGN_RSA_PSS_RSAE_SHA384;
}

static jint netty_boringssl_ssl_sign_rsa_pss_rsae_sha512(JNIEnv* env, jclass clazz) {
    return SSL_SIGN_RSA_PSS_RSAE_SHA512;
}

static jint netty_boringssl_ssl_sign_ed25519(JNIEnv* env, jclass clazz) {
    return SSL_SIGN_ED25519;
}

static jint netty_boringssl_ssl_sign_rsa_pkcs1_md5_sha1(JNIEnv* env, jclass clazz) {
    return SSL_SIGN_RSA_PKCS1_MD5_SHA1;
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
        if (data == NULL) {
            goto cleanup;
        }
        CRYPTO_BUFFER *buffer = CRYPTO_BUFFER_new(data, data_len, pool);
        (*env)->ReleaseByteArrayElements(env, bytes, (jbyte*)data, JNI_ABORT);
        (*env)->DeleteLocalRef(env, bytes);

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

static jbyteArray to_byte_array(JNIEnv* env, uint8_t* bytes, size_t len) {
    if (bytes == NULL || len == 0) {
        return NULL;
    }
     jbyteArray array = (*env)->NewByteArray(env, len);
     if (array == NULL) {
        return NULL;
     }
     (*env)->SetByteArrayRegion(env,array, 0, len, (jbyte*) bytes);
     return array;
}

// Store the callback to run and also if it was consumed via SSL.getTask(...).
typedef struct netty_boringssl_ssl_task_t netty_boringssl_ssl_task_t;
struct netty_boringssl_ssl_task_t {
    jboolean consumed;
    jobject task;
};


static netty_boringssl_ssl_task_t* netty_boringssl_ssl_task_new(JNIEnv* e, jobject task) {
    if (task == NULL) {
        // task was NULL which most likely means we did run out of memory when calling NewObject(...). Signal a failure back by returning NULL.
        return NULL;
    }
    netty_boringssl_ssl_task_t* sslTask = (netty_boringssl_ssl_task_t*) OPENSSL_malloc(sizeof(netty_boringssl_ssl_task_t));
    if (sslTask == NULL) {
        return NULL;
    }

    if ((sslTask->task = (*e)->NewGlobalRef(e, task)) == NULL) {
        // NewGlobalRef failed because we ran out of memory, free what we malloc'ed and fail the handshake.
        OPENSSL_free(sslTask);
        return NULL;
    }
    sslTask->consumed = JNI_FALSE;
    return sslTask;
}

static void netty_boringssl_ssl_task_free(JNIEnv* e, netty_boringssl_ssl_task_t* sslTask) {
    if (sslTask == NULL) {
        return;
    }

    if (sslTask->task != NULL) {
        // Execute the destroy method
        (*e)->CallVoidMethod(e, sslTask->task, sslTaskDestroyMethod);

        // As we created a Global reference before we need to delete the reference as otherwise we will leak memory.
        (*e)->DeleteGlobalRef(e, sslTask->task);
        sslTask->task = NULL;
    }

    // The task was malloc'ed before, free it and clear it from the SSL storage.
    OPENSSL_free(sslTask);
}

enum ssl_verify_result_t quic_SSL_cert_custom_verify(SSL* ssl, uint8_t *out_alert) {
    enum ssl_verify_result_t ret = ssl_verify_invalid;
    jint result = X509_V_ERR_UNSPECIFIED;
    JNIEnv *e = NULL;
    jclass verifyTaskClass = NULL;

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

    netty_boringssl_ssl_task_t* ssl_task = (netty_boringssl_ssl_task_t*) SSL_get_ex_data(ssl, sslTaskIdx);
    // Let's check if we retried the operation and so have stored a sslTask that runs the certificiate callback.
    if (ssl_task != NULL) {
        // Check if the task complete yet. If not the complete field will be still false.
        if ((*e)->GetBooleanField(e, ssl_task->task, sslTaskComplete) == JNI_FALSE) {
            // Not done yet, try again later.
            ret = ssl_verify_retry;
            goto complete;
        }

        // The task is complete, retrieve the return value that should be signaled back.
        result = (*e)->GetIntField(e, ssl_task->task, sslTaskReturnValue);

        SSL_set_ex_data(ssl, sslTaskIdx, NULL);
        netty_boringssl_ssl_task_free(e, ssl_task);
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

    NETTY_JNI_UTIL_NEW_LOCAL_FROM_WEAK(e, verifyTaskClass, verifyTaskClassWeak, complete);
    jobject task = (*e)->NewObject(e, verifyTaskClass, verifyTaskClassInitMethod, (jlong) ssl, array, authMethodString, verifyCallback);
    NETTY_JNI_UTIL_DELETE_LOCAL(e, verifyTaskClass);
    if (task == NULL) {
        goto complete;
    }

    ssl_task = netty_boringssl_ssl_task_new(e, task);
    if (ssl_task == NULL) {
        goto complete;
    }

    SSL_set_ex_data(ssl, sslTaskIdx, ssl_task);

    // Signal back that we want to suspend the handshake.
    ret = ssl_verify_retry;
    goto complete;
complete:
    if (ret != ssl_verify_retry) {
        if (result == X509_V_OK) {
            ret = ssl_verify_ok;
        } else {
            ret = ssl_verify_invalid;
            *out_alert = SSL_alert_from_verify_result(result);
        }
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


static enum ssl_private_key_result_t netty_boringssl_private_key_sign_java(SSL *ssl, uint8_t *out, size_t *out_len, size_t max_out, uint16_t signature_algorithm, const uint8_t *in, size_t in_len) {
    enum ssl_private_key_result_t ret = ssl_private_key_failure;
    jclass sslPrivateKeyMethodSignTaskClass = NULL;
    jbyteArray inputArray = NULL;
    JNIEnv *e = NULL;

    if (quic_get_java_env(&e) != JNI_OK) {
        goto complete;
    }

    jobject ssl_private_key_method = SSL_CTX_get_ex_data(SSL_get_SSL_CTX(ssl), sslPrivateKeyMethodIdx);
    if (ssl_private_key_method == NULL) {
        goto complete;
    }

    if ((inputArray = (*e)->NewByteArray(e, in_len)) == NULL) {
        goto complete;
    }
    (*e)->SetByteArrayRegion(e, inputArray, 0, in_len, (jbyte*) in);

    // Lets create the BoringSSLPrivateKeyMethodSignTask and store it on the SSL object. We then later retrieve it via
    // BoringSSL.SSL_getTask(ssl) and run it.

    NETTY_JNI_UTIL_NEW_LOCAL_FROM_WEAK(e, sslPrivateKeyMethodSignTaskClass, sslPrivateKeyMethodSignTaskClassWeak, complete);
    jobject task = (*e)->NewObject(e, sslPrivateKeyMethodSignTaskClass, sslPrivateKeyMethodSignTaskInitMethod, (jlong) ssl,
                    signature_algorithm, inputArray, ssl_private_key_method);
    NETTY_JNI_UTIL_DELETE_LOCAL(e, sslPrivateKeyMethodSignTaskClass);
    if (task == NULL) {
        goto complete;
    }
    netty_boringssl_ssl_task_t* ssl_task = netty_boringssl_ssl_task_new(e, task);
    if (ssl_task == NULL) {
        goto complete;
    }
    SSL_set_ex_data(ssl, sslTaskIdx, ssl_task);
    ret = ssl_private_key_retry;
complete:
    // Free up any allocated memory and return.
    NETTY_JNI_UTIL_DELETE_LOCAL(e, inputArray);
    return ret;
}

static enum ssl_private_key_result_t netty_boringssl_private_key_decrypt_java(SSL *ssl, uint8_t *out, size_t *out_len, size_t max_out, const uint8_t *in, size_t in_len) {
    enum ssl_private_key_result_t ret = ssl_private_key_failure;
    jclass sslPrivateKeyMethodDecryptTaskClass = NULL;
    jbyteArray inArray = NULL;
    JNIEnv *e = NULL;

    if (quic_get_java_env(&e) != JNI_OK) {
        goto complete;
    }

    jobject ssl_private_key_method = SSL_CTX_get_ex_data(SSL_get_SSL_CTX(ssl), sslPrivateKeyMethodIdx);
    if (ssl_private_key_method == NULL) {
        goto complete;
    }

    if ((inArray = (*e)->NewByteArray(e, in_len)) == NULL) {
        goto complete;
    }
    (*e)->SetByteArrayRegion(e, inArray, 0, in_len, (jbyte*) in);

    // Lets create the SSLPrivateKeyMethodDecryptTask and store it on the SSL object. We then later retrieve it via
    // BoringSSL.SSL_getTask(ssl) and run it.
    NETTY_JNI_UTIL_NEW_LOCAL_FROM_WEAK(e, sslPrivateKeyMethodDecryptTaskClass, sslPrivateKeyMethodDecryptTaskClassWeak, complete);
    jobject task = (*e)->NewObject(e, sslPrivateKeyMethodDecryptTaskClass, sslPrivateKeyMethodDecryptTaskInitMethod,
                (jlong) ssl, inArray, ssl_private_key_method);
    if (task == NULL) {
        goto complete;
    }
    netty_boringssl_ssl_task_t* ssl_task = netty_boringssl_ssl_task_new(e, task);
    if (ssl_task == NULL) {
        goto complete;
    }
    SSL_set_ex_data(ssl, sslTaskIdx, ssl_task);
    ret = ssl_private_key_retry;
complete:
    // Delete the local reference as this is executed by a callback.
    NETTY_JNI_UTIL_DELETE_LOCAL(e, inArray);
    NETTY_JNI_UTIL_DELETE_LOCAL(e, sslPrivateKeyMethodDecryptTaskClass);
    return ret;
}

static enum ssl_private_key_result_t netty_boringssl_private_key_complete_java(SSL *ssl, uint8_t *out, size_t *out_len, size_t max_out) {
    jbyte* b = NULL;
    int arrayLen = 0;
    JNIEnv *e = NULL;

    netty_boringssl_ssl_task_t* ssl_task = SSL_get_ex_data(ssl, sslTaskIdx);

    // Let's check if we retried the operation and so have stored a sslTask that runs the sign / decrypt callback.
    if (ssl_task != NULL) {
        if (quic_get_java_env(&e) != JNI_OK) {
            return ssl_private_key_failure;
        }

        // Check if the task complete yet. If not the complete field will be still false.
        if ((*e)->GetBooleanField(e, ssl_task->task, sslTaskComplete) == JNI_FALSE) {
            // Not done yet, try again later.
            return ssl_private_key_retry;
        }

        // The task is complete, retrieve the return value that should be signaled back.
        jbyteArray resultBytes = (*e)->GetObjectField(e, ssl_task->task, sslPrivateKeyMethodTaskResultBytesField);

        SSL_set_ex_data(ssl, sslTaskIdx, NULL);
        netty_boringssl_ssl_task_free(e, ssl_task);

        if (resultBytes == NULL) {
            return ssl_private_key_failure;
        }

        arrayLen = (*e)->GetArrayLength(e, resultBytes);
        if (max_out < arrayLen) {
             // We need to fail as otherwise we would end up writing into memory which does not
             // belong to us.
            (*e)->DeleteLocalRef(e, resultBytes);
            return ssl_private_key_failure;
        }
        b = (*e)->GetByteArrayElements(e, resultBytes, NULL);
        if (b == NULL) {
            (*e)->DeleteLocalRef(e, resultBytes);
            return ssl_private_key_failure;
        }
        memcpy(out, b, arrayLen);
        (*e)->ReleaseByteArrayElements(e, resultBytes, b, JNI_ABORT);
        (*e)->DeleteLocalRef(e, resultBytes);
        *out_len = arrayLen;
        return ssl_private_key_success;
    }
    return ssl_private_key_failure;
}

const SSL_PRIVATE_KEY_METHOD netty_boringssl_private_key_method = {
    &netty_boringssl_private_key_sign_java,
    &netty_boringssl_private_key_decrypt_java,
    &netty_boringssl_private_key_complete_java
};

// See https://www.openssl.org/docs/man1.0.2/man3/SSL_set_cert_cb.html for return values.
static int quic_certificate_cb(SSL* ssl, void* arg) {
    jclass certificateTaskClass = NULL;
    JNIEnv *e = NULL;
    if (quic_get_java_env(&e) != JNI_OK) {
        return 0;
    }

    netty_boringssl_ssl_task_t* ssl_task = (netty_boringssl_ssl_task_t*) SSL_get_ex_data(ssl, sslTaskIdx);

    // Let's check if we retried the operation and so have stored a sslTask that runs the certificiate callback.
    if (ssl_task == NULL) {
        jobjectArray authMethods = NULL;
        jobjectArray issuers = NULL;
        jbyteArray types = NULL;
        if (SSL_is_server(ssl) == 1) {
            const STACK_OF(SSL_CIPHER) *ciphers = SSL_get_ciphers(ssl);
            int len = sk_SSL_CIPHER_num(ciphers);
            authMethods = (*e)->NewObjectArray(e, len, stringClass, NULL);
            if (authMethods == NULL) {
                return 0;
            }

            for (int i = 0; i < len; i++) {
                jstring methodString = (*e)->NewStringUTF(e, SSL_CIPHER_get_kx_name(sk_SSL_CIPHER_value(ciphers, i)));
                if (methodString == NULL) {
                    // Out of memory
                    return 0;
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

        // Lets create the CertificateCallbackTask and store it on the SSL object. We then later retrieve it via
        // SSL.getTask(ssl) and run it.
        NETTY_JNI_UTIL_NEW_LOCAL_FROM_WEAK(e, certificateTaskClass, certificateTaskClassWeak, done);
        jobject task = (*e)->NewObject(e, certificateTaskClass, certificateTaskClassInitMethod, (jlong) ssl, types, issuers, authMethods, arg);
        NETTY_JNI_UTIL_DELETE_LOCAL(e, certificateTaskClass);
        if (task == NULL) {
            return 0;
        }
        if ((ssl_task = netty_boringssl_ssl_task_new(e, task)) == NULL) {
            return 0;
        }

        SSL_set_ex_data(ssl, sslTaskIdx, ssl_task);

        // Signal back that we want to suspend the handshake.
        return -1;
    }

    // Check if the task complete yet. If not the complete field will be still false.
    if ((*e)->GetBooleanField(e, ssl_task->task, sslTaskComplete) == JNI_FALSE) {
        // Not done yet, try again later.
        return -1;
    }

    // The task is complete, retrieve the return value that should be signaled back.
    jint retValue = (*e)->GetIntField(e, ssl_task->task, sslTaskReturnValue);
    if (retValue == 0) {
        return 0;
    }

    int ret = 0;
    EVP_PKEY* pkey = (EVP_PKEY *) (*e)->GetLongField(e, ssl_task->task, certificateTaskClassKeyField);
    const STACK_OF(CRYPTO_BUFFER) *cchain = (STACK_OF(CRYPTO_BUFFER) *) (*e)->GetLongField(e, ssl_task->task, certificateTaskClassChainField);

    // Set both fields to 0 so destroy() will not destroy the native allocated memory.
    (*e)->SetLongField(e, ssl_task->task, certificateTaskClassKeyField, 0);
    (*e)->SetLongField(e, ssl_task->task, certificateTaskClassChainField, 0);

    SSL_set_ex_data(ssl, sslTaskIdx, NULL);
    netty_boringssl_ssl_task_free(e, ssl_task);

    if (pkey == NULL && cchain == NULL) {
        // No key material found.
        return 1;
    }

    int numCerts = sk_CRYPTO_BUFFER_num(cchain);
    if (numCerts == 0) {
        goto done;
    }
    CRYPTO_BUFFER** certs = OPENSSL_malloc(sizeof(CRYPTO_BUFFER*) * numCerts);

    if (certs == NULL) {
        goto done;
    }

    for (int i = 0; i < numCerts; i++) {
        certs[i] = sk_CRYPTO_BUFFER_value(cchain, i);
    }

    if (pkey != NULL) {
        if (SSL_set_chain_and_key(ssl, certs, numCerts, pkey, NULL) > 0) {
            ret = 1;
        }
    } else {
        if (SSL_set_chain_and_key(ssl, certs, numCerts, NULL, &netty_boringssl_private_key_method) > 0) {
            ret = 1;
        }
    }
done:
    OPENSSL_free(certs);
    EVP_PKEY_free(pkey);
    if (cchain != NULL) {
        sk_CRYPTO_BUFFER_pop_free((STACK_OF(CRYPTO_BUFFER) *) cchain, CRYPTO_BUFFER_free);
    }
    NETTY_JNI_UTIL_DELETE_LOCAL(e, certificateTaskClass);

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
        jboolean sessionReused = SSL_session_reused((SSL *) ssl) == 1 ? JNI_TRUE : JNI_FALSE;

        // Execute the java callback
        (*e)->CallVoidMethod(e, handshakeCompleteCallback, handshakeCompleteCallbackMethod,
                 (jlong) ssl, session_id, cipher, version, peerCert, certChain, creationTime, timeout, alpnSelected, sessionReused);
    }
}

int quic_tlsext_servername_callback(SSL *ssl, int *out_alert, void *arg) {
    SSL_CTX* ctx = SSL_get_SSL_CTX((SSL*) ssl);
    jobject servernameCallback = SSL_CTX_get_ex_data(ctx, servernameCallbackIdx);
    if (servernameCallback == NULL) {
        // No SNI should be used
        return SSL_TLSEXT_ERR_NOACK;
    }

    JNIEnv *e = NULL;
    if (quic_get_java_env(&e) != JNI_OK) {
        // There is something serious wrong just fail the SSL in a fatal way.
        return SSL_TLSEXT_ERR_ALERT_FATAL;
    }

    jstring servername = NULL;
    int resultValue = SSL_TLSEXT_ERR_OK;
    int type = SSL_get_servername_type(ssl);
    if (type == TLSEXT_NAMETYPE_host_name) {
        const char *name = SSL_get_servername(ssl, type);
        if (name != NULL) {
            servername = (*e)->NewStringUTF(e, name);
            if (servername == NULL) {
                // There is something serious wrong just fail the SSL in a fatal way.
                return SSL_TLSEXT_ERR_ALERT_FATAL;
            }
        } else {
            // There was no SNI infos provided so not ack at the end.
            resultValue = SSL_TLSEXT_ERR_NOACK;
        }
    }

    jlong result = (*e)->CallLongMethod(e, servernameCallback, servernameCallbackMethod, (jlong) ssl, servername);

    if ((*e)->ExceptionCheck(e) == JNI_TRUE) {
        // Some exception was thrown. Let's fail.
        (*e)->ExceptionClear(e);
        return SSL_TLSEXT_ERR_ALERT_FATAL;
    }
    if (result < 0) {
        // If we returned a negative number we want to fail.
        return SSL_TLSEXT_ERR_ALERT_FATAL;
    }

    // Change the ctx to the one that was returned.
    SSL_CTX* newCtx = SSL_set_SSL_CTX(ssl, (SSL_CTX*) result);
    if (newCtx == NULL) {
        // Setting the SSL_CTX failed.
        return SSL_TLSEXT_ERR_ALERT_FATAL;
    }
    return resultValue;
}

// see https://www.openssl.org/docs/man1.1.1/man3/SSL_CTX_set_keylog_callback.html
void keylog_callback(const SSL* ssl, const char* line) {
    SSL_CTX* ctx = SSL_get_SSL_CTX(ssl);
    if (ctx == NULL) {
        return;
    }

    JNIEnv* e = NULL;
    if (quic_get_java_env(&e) != JNI_OK) {
        return;
    }

    jobject keylogCallback = SSL_CTX_get_ex_data(ctx, keylogCallbackIdx);
    if (keylogCallback == NULL) {
        return;
    }

    jstring keyString = NULL;
    if (line != NULL) {
        keyString = (*e)->NewStringUTF(e, line);
        if (keyString == NULL) {
            return;
        }
    }

    // Execute the java callback
    (*e)->CallVoidMethod(e, keylogCallback, keylogCallbackMethod, (jlong) ssl, keyString);
}

// Always return 0 as we serialize the session / params to byte[] and so no take ownership.
// See https://commondatastorage.googleapis.com/chromium-boringssl-docs/ssl.h.html#SSL_CTX_sess_set_new_cb
int new_session_callback(SSL *ssl, SSL_SESSION *session) {
    SSL_CTX* ctx = SSL_get_SSL_CTX(ssl);
    if (ctx == NULL) {
        return 0;
    }

    JNIEnv* e = NULL;
    if (quic_get_java_env(&e) != JNI_OK) {
        return 0;
    }

    jobject sessionCallback = SSL_CTX_get_ex_data(ctx, sessionCallbackIdx);
    if (sessionCallback == NULL) {
        return 0;
    }

    uint8_t *session_data = NULL;
    size_t session_data_len = 0;
    if (SSL_SESSION_to_bytes(session, &session_data, &session_data_len) == 0) {
        // Get session error
        return 0;
    }

    jbyteArray sessionBytes = to_byte_array(e, session_data, session_data_len);
    // We need to explicit free the session_data after we copied it to byte[].
    // See  https://commondatastorage.googleapis.com/chromium-boringssl-docs/ssl.h.html#SSL_SESSION_to_bytes
    OPENSSL_free((void *)session_data);
    if (sessionBytes == NULL) {
        // Get session error
        return 0;
    }

    jbyteArray peerParamsBytes = NULL;
    // There is not need to explicit free peer_params as it will be freed as soon as SSL*.
    // See https://commondatastorage.googleapis.com/chromium-boringssl-docs/ssl.h.html#SSL_get_peer_quic_transport_params
    const uint8_t *peer_params = NULL;
    size_t peer_params_len = 0;
    SSL_get_peer_quic_transport_params((SSL*) ssl, &peer_params, &peer_params_len);
    if (peer_params_len != 0) {
        peerParamsBytes = to_byte_array(e, (uint8_t *) peer_params, peer_params_len);
    }

    jboolean singleUse = SSL_SESSION_should_be_single_use(session) == 1 ? JNI_TRUE : JNI_FALSE;

    // Execute the java callback
    (*e)->CallVoidMethod(e, sessionCallback, sessionCallbackMethod, (jlong) ssl, (jlong) SSL_SESSION_get_time(session), (jlong) SSL_SESSION_get_timeout(session), sessionBytes, singleUse, peerParamsBytes);

    return 0;
}

static jlong netty_boringssl_SSLContext_new(JNIEnv* env, jclass clazz) {
    SSL_CTX *ctx = SSL_CTX_new(TLS_with_buffers_method());
    return (jlong) ctx;
}

static jlong netty_boringssl_SSLContext_new0(JNIEnv* env, jclass clazz, jboolean server, jbyteArray alpn_protos, jobject handshakeCompleteCallback, jobject certificateCallback, jobject verifyCallback, jobject servernameCallback, jobject keylogCallback, jobject sessionCallback, jobject privateKeyMethod, jobject sessionTicketCallback, jint verifyMode, jobjectArray subjectNames) {
    jobject handshakeCompleteCallbackRef = NULL;
    jobject certificateCallbackRef = NULL;
    jobject verifyCallbackRef = NULL;
    jobject servernameCallbackRef = NULL;
    jobject keylogCallbackRef = NULL;
    jobject sessionCallbackRef = NULL;
    jobject privateKeyMethodRef = NULL;
    jobject sessionTicketCallbackRef = NULL;

    if ((handshakeCompleteCallbackRef = (*env)->NewGlobalRef(env, handshakeCompleteCallback)) == NULL) {
        goto error;
    }

    if ((certificateCallbackRef = (*env)->NewGlobalRef(env, certificateCallback)) == NULL) {
        goto error;
    }

    if ((verifyCallbackRef = (*env)->NewGlobalRef(env, verifyCallback)) == NULL) {
        goto error;
    }

    if (servernameCallback != NULL) {
        if ((servernameCallbackRef = (*env)->NewGlobalRef(env, servernameCallback)) == NULL) {
            goto error;
        }
    }

    if (keylogCallback != NULL) {
        if ((keylogCallbackRef = (*env)->NewGlobalRef(env, keylogCallback)) == NULL) {
            goto error;
        }
    }

    if (sessionCallback != NULL) {
        if ((sessionCallbackRef = (*env)->NewGlobalRef(env, sessionCallback)) == NULL) {
            goto error;
        }
    }

    if (privateKeyMethod != NULL) {
        if ((privateKeyMethodRef = (*env)->NewGlobalRef(env, privateKeyMethod)) == NULL) {
            goto error;
        }
    }
    if ((sessionTicketCallbackRef = (*env)->NewGlobalRef(env, sessionTicketCallback)) == NULL) {
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

    if (servernameCallbackRef != NULL) {
        SSL_CTX_set_ex_data(ctx, servernameCallbackIdx, servernameCallbackRef);
        SSL_CTX_set_tlsext_servername_callback(ctx, quic_tlsext_servername_callback);
    }

    if (keylogCallbackRef != NULL) {
        SSL_CTX_set_ex_data(ctx, keylogCallbackIdx, keylogCallbackRef);
        SSL_CTX_set_keylog_callback(ctx, keylog_callback);
    }

    if (sessionCallbackRef != NULL) {
        SSL_CTX_set_ex_data(ctx, sessionCallbackIdx, sessionCallbackRef);
        // The internal cache is never used on a client, this only enables the callbacks.
        SSL_CTX_set_session_cache_mode(ctx, SSL_SESS_CACHE_CLIENT);
        SSL_CTX_sess_set_new_cb(ctx, new_session_callback);
    }

    if (privateKeyMethodRef != NULL) {
        SSL_CTX_set_ex_data(ctx, sslPrivateKeyMethodIdx, privateKeyMethodRef);
    }

    SSL_CTX_set_ex_data(ctx, sessionTicketCallbackIdx, sessionTicketCallbackRef);

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
    if (verifyCallbackRef != NULL) {
        (*env)->DeleteGlobalRef(env, verifyCallbackRef);
    }
    if (servernameCallbackRef != NULL) {
        (*env)->DeleteGlobalRef(env, servernameCallbackRef);
    }
    if (keylogCallbackRef != NULL) {
        (*env)->DeleteGlobalRef(env, keylogCallbackRef);
    }
    if (sessionCallbackRef != NULL) {
        (*env)->DeleteGlobalRef(env, sessionCallbackRef);
    }
    if (privateKeyMethodRef != NULL) {
        (*env)->DeleteGlobalRef(env, privateKeyMethodRef);
    }
    if (sessionTicketCallbackRef != NULL) {
        (*env)->DeleteGlobalRef(env, sessionTicketCallbackRef);
    }
    return -1;
}

static void netty_boringssl_SSLContext_free(JNIEnv* env, jclass clazz, jlong ctx) {
    SSL_CTX* ssl_ctx = (SSL_CTX*) ctx;

    jobject handshakeCompleteCallbackRef = SSL_CTX_get_ex_data(ssl_ctx, handshakeCompleteCallbackIdx);
    if (handshakeCompleteCallbackRef != NULL) {
        (*env)->DeleteGlobalRef(env, handshakeCompleteCallbackRef);
    }
    jobject verifyCallbackRef = SSL_CTX_get_ex_data(ssl_ctx, verifyCallbackIdx);
    if (verifyCallbackRef != NULL) {
        (*env)->DeleteGlobalRef(env, verifyCallbackRef);
    }
    jobject certificateCallbackRef = SSL_CTX_get_ex_data(ssl_ctx, certificateCallbackIdx);
    if (certificateCallbackRef != NULL) {
        (*env)->DeleteGlobalRef(env, certificateCallbackRef);
    }

    jobject servernameCallbackRef = SSL_CTX_get_ex_data(ssl_ctx, servernameCallbackIdx);
    if (servernameCallbackRef != NULL) {
        (*env)->DeleteGlobalRef(env, servernameCallbackRef);
    }

    jobject keylogCallbackRef = SSL_CTX_get_ex_data(ssl_ctx, keylogCallbackIdx);
    if (keylogCallbackRef != NULL) {
        (*env)->DeleteGlobalRef(env, keylogCallbackRef);
    }

    jobject sessionCallbackRef = SSL_CTX_get_ex_data(ssl_ctx, sessionCallbackIdx);
    if (sessionCallbackRef != NULL) {
        (*env)->DeleteGlobalRef(env, sessionCallbackRef);
    }

    jobject privateKeyMethodRef = SSL_CTX_get_ex_data(ssl_ctx, sslPrivateKeyMethodIdx);
    if (privateKeyMethodRef != NULL) {
        (*env)->DeleteGlobalRef(env, privateKeyMethodRef);
    }

    alpn_data* data = SSL_CTX_get_ex_data(ssl_ctx, alpn_data_idx);
    OPENSSL_free(data);

    jobject sessionTicketCallbackRef = SSL_CTX_get_ex_data(ssl_ctx, sessionTicketCallbackIdx);
    if (sessionCallbackRef != NULL) {
        (*env)->DeleteGlobalRef(env, sessionTicketCallbackRef);
    }

    CRYPTO_BUFFER_POOL* pool = SSL_CTX_get_ex_data(ssl_ctx, crypto_buffer_pool_idx);
    SSL_CTX_free(ssl_ctx);

    // The pool should be freed last in case that the SSL_CTX has a reference to things that are stored in the
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
        int mode = SSL_CTX_get_session_cache_mode(ssl_ctx);
        // Internal Cache only works on the server side for now.
        SSL_CTX_set_session_cache_mode(ssl_ctx, SSL_SESS_CACHE_SERVER | mode);
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

jobject netty_boringssl_SSL_getTask(JNIEnv* env, jclass clazz, jlong ssl) {
    netty_boringssl_ssl_task_t* ssl_task = SSL_get_ex_data((SSL*) ssl, sslTaskIdx);

    if (ssl_task == NULL || ssl_task->consumed == JNI_TRUE) {
        // Either no task was produced or it was already consumed by SSL.getTask(...).
        return NULL;
    }
    ssl_task->consumed = JNI_TRUE;
    return ssl_task->task;
}

void netty_boringssl_SSL_cleanup(JNIEnv* env, jclass clazz, jlong ssl) {
    netty_boringssl_ssl_task_t* sslTask = SSL_get_ex_data((SSL *) ssl, sslTaskIdx);
    if (sslTask != NULL) {
        SSL_set_ex_data((SSL *) ssl, sslTaskIdx, NULL);
        netty_boringssl_ssl_task_free(env, sslTask);
    }
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
    if (data == NULL) {
        return -1;
    }
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
    (*env)->ReleaseByteArrayElements(env, array, (jbyte*)data, JNI_ABORT);
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

static int netty_boringssl_tlsext_ticket_key_cb(SSL *s, unsigned char key_name[16], unsigned char *iv, EVP_CIPHER_CTX *ctx, HMAC_CTX *hctx, int enc) {
    SSL_CTX *c = SSL_get_SSL_CTX(s);
    if (c == NULL) {
        return 0;
    }

    jobject sessionTicketCallback = SSL_CTX_get_ex_data(c, sessionTicketCallbackIdx);
    if (sessionTicketCallback == NULL) {
       return 0;
    }
    JNIEnv* env = NULL;
    if (quic_get_java_env(&env) != JNI_OK) {
        return 0;
    }

    if (enc) { /* create new session */
        jbyteArray key = (jbyteArray) (*env)->CallObjectMethod(env, sessionTicketCallback, sessionTicketCallbackMethod, NULL);
        if (key != NULL) {
            int keyLen = (*env)->GetArrayLength(env, key);
            if (keyLen != SSL_SESSION_TICKET_KEY_LEN) {
                return -1;
            }
            if (RAND_bytes(iv, EVP_MAX_IV_LENGTH) <= 0) {
                return -1; /* insufficient random */
            }

            uint8_t* data = (uint8_t*) (*env)->GetByteArrayElements(env, key, 0);
            if (data == NULL) {
                return -1;
            }
            memcpy(key_name, data + SSL_SESSION_TICKET_KEY_NAME_OFFSET, SSL_SESSION_TICKET_KEY_NAME_LEN);

            HMAC_Init_ex(hctx, (void*) (data + SSL_SESSION_TICKET_KEY_HMAC_OFFSET), SSL_SESSION_TICKET_HMAC_KEY_LEN, EVP_sha256(), NULL);

            EVP_EncryptInit_ex(ctx, EVP_aes_128_cbc(), NULL, (void*) (data + SSL_SESSION_TICKET_KEY_EVP_OFFSET), iv);

            (*env)->ReleaseByteArrayElements(env, key, (jbyte*) data, JNI_ABORT);

            return 1;
        }
        // No ticket configured
        return 0;
    } else { /* retrieve session */
        jbyteArray name = to_byte_array(env, (uint8_t*) key_name, 16);
        jbyteArray key = (jbyteArray) (*env)->CallObjectMethod(env, sessionTicketCallback, sessionTicketCallbackMethod, name);

        if (key != NULL) {
            int keyLen = (*env)->GetArrayLength(env, key);
            if (keyLen != SSL_SESSION_TICKET_KEY_LEN) {
                return -1;
            }

            uint8_t* data = (uint8_t*) (*env)->GetByteArrayElements(env, key, 0);
            if (data == NULL) {
                return -1;
            }
            // The first byte is used to encode if the key needs to be upgraded.
            int is_current_key = *data != 0;

            HMAC_Init_ex(hctx, (void*) (data + SSL_SESSION_TICKET_KEY_HMAC_OFFSET), SSL_SESSION_TICKET_HMAC_KEY_LEN, EVP_sha256(), NULL);

            EVP_DecryptInit_ex(ctx, EVP_aes_128_cbc(), NULL, (void*) (data + SSL_SESSION_TICKET_KEY_EVP_OFFSET), iv);

            (*env)->ReleaseByteArrayElements(env, key, (jbyte*) data, JNI_ABORT);

            if (!is_current_key) {
                // The ticket matched a key in the list, and we want to upgrade it to the current
                // key.
                return 2;
            }
            // The ticket matched the current key.
            return 1;
        }
        // No matching ticket.
        return 0;
    }
}

void netty_boringssl_SSLContext_setSessionTicketKeys(JNIEnv* env, jclass clazz, jlong ctx, jboolean enableCallback) {
    if (enableCallback == JNI_TRUE) {
        SSL_CTX_set_tlsext_ticket_key_cb((SSL_CTX *) ctx, netty_boringssl_tlsext_ticket_key_cb);
    } else {
        SSL_CTX_set_tlsext_ticket_key_cb((SSL_CTX *) ctx, NULL);
    }
}

jint netty_boringssl_SSLContext_set1_groups_list(JNIEnv* env, jclass clazz, jlong ctx, jstring groups) {
    const char *nativeString = (*env)->GetStringUTFChars(env, groups, 0);
    int ret = SSL_CTX_set1_groups_list((SSL_CTX *) ctx, nativeString);
    (*env)->ReleaseStringUTFChars(env, groups, nativeString);
    return (jint) ret;
}

jint netty_boringssl_SSLContext_set1_sigalgs_list(JNIEnv* env, jclass clazz, jlong ctx, jstring sigalgs) {
    const char *nativeString = (*env)->GetStringUTFChars(env, sigalgs, 0);
    int ret = SSL_CTX_set1_sigalgs_list((SSL_CTX *) ctx, nativeString);
    (*env)->ReleaseStringUTFChars(env, sigalgs, nativeString);
    return (jint) ret;
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
  { "x509_v_err_unspecified", "()I", (void *) netty_boringssl_x509_v_err_unspecified },
  { "ssl_sign_rsa_pkcs_sha1", "()I", (void *) netty_boringssl_ssl_sign_rsa_pkcs_sha1 },
  { "ssl_sign_rsa_pkcs_sha256", "()I", (void *) netty_boringssl_ssl_sign_rsa_pkcs_sha256 },
  { "ssl_sign_rsa_pkcs_sha384", "()I", (void *) netty_boringssl_ssl_sign_rsa_pkcs_sha384 },
  { "ssl_sign_rsa_pkcs_sha512", "()I", (void *) netty_boringssl_ssl_sign_rsa_pkcs_sha512 },
  { "ssl_sign_ecdsa_pkcs_sha1", "()I", (void *) netty_boringssl_ssl_sign_ecdsa_pkcs_sha1 },
  { "ssl_sign_ecdsa_secp256r1_sha256", "()I", (void *) netty_boringssl_ssl_sign_ecdsa_secp256r1_sha256 },
  { "ssl_sign_ecdsa_secp384r1_sha384", "()I", (void *) netty_boringssl_ssl_sign_ecdsa_secp384r1_sha384 },
  { "ssl_sign_ecdsa_secp521r1_sha512", "()I", (void *) netty_boringssl_ssl_sign_ecdsa_secp521r1_sha512 },
  { "ssl_sign_rsa_pss_rsae_sha256", "()I", (void *) netty_boringssl_ssl_sign_rsa_pss_rsae_sha256 },
  { "ssl_sign_rsa_pss_rsae_sha384", "()I", (void *) netty_boringssl_ssl_sign_rsa_pss_rsae_sha384 },
  { "ssl_sign_rsa_pss_rsae_sha512", "()I", (void *) netty_boringssl_ssl_sign_rsa_pss_rsae_sha512 },
  { "ssl_sign_ed25519", "()I", (void *) netty_boringssl_ssl_sign_ed25519 },
  { "ssl_sign_rsa_pkcs1_md5_sha1", "()I", (void *) netty_boringssl_ssl_sign_rsa_pkcs1_md5_sha1 }
};

static const jint statically_referenced_fixed_method_table_size = sizeof(statically_referenced_fixed_method_table) / sizeof(statically_referenced_fixed_method_table[0]);
static const JNINativeMethod fixed_method_table[] = {
  { "SSLContext_new", "()J", (void *) netty_boringssl_SSLContext_new },
  { "SSLContext_new0", "(Z[BLjava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;I[[B)J", (void *) netty_boringssl_SSLContext_new0 },
  { "SSLContext_free", "(J)V", (void *) netty_boringssl_SSLContext_free },
  { "SSLContext_setSessionCacheTimeout", "(JJ)J", (void *) netty_boringssl_SSLContext_setSessionCacheTimeout },
  { "SSLContext_setSessionCacheSize", "(JJ)J", (void *) netty_boringssl_SSLContext_setSessionCacheSize },
  { "SSLContext_set_early_data_enabled", "(JZ)V", (void *) netty_boringssl_SSLContext_set_early_data_enabled },
  { "SSLContext_setSessionTicketKeys", "(JZ)V", (void *) netty_boringssl_SSLContext_setSessionTicketKeys },
  { "SSLContext_set1_groups_list", "(JLjava/lang/String;)I", (void *) netty_boringssl_SSLContext_set1_groups_list },
  { "SSLContext_set1_sigalgs_list", "(JLjava/lang/String;)I", (void *) netty_boringssl_SSLContext_set1_sigalgs_list },
  { "SSL_new0", "(JZLjava/lang/String;)J", (void *) netty_boringssl_SSL_new0 },
  { "SSL_free", "(J)V", (void *) netty_boringssl_SSL_free },
  { "SSL_getTask", "(J)Ljava/lang/Runnable;", (void *) netty_boringssl_SSL_getTask },
  { "SSL_cleanup", "(J)V", (void *) netty_boringssl_SSL_cleanup },
  { "EVP_PKEY_parse", "([BLjava/lang/String;)J", (void *) netty_boringssl_EVP_PKEY_parse },
  { "EVP_PKEY_free", "(J)V", (void *) netty_boringssl_EVP_PKEY_free },
  { "CRYPTO_BUFFER_stack_new", "(J[[B)J", (void *) netty_boringssl_CRYPTO_BUFFER_stack_new },
  { "CRYPTO_BUFFER_stack_free", "(J)V", (void *) netty_boringssl_CRYPTO_BUFFER_stack_free },
  { "ERR_last_error", "()Ljava/lang/String;", (void *) netty_boringssl_ERR_last_error }
};

static const jint fixed_method_table_size = sizeof(fixed_method_table) / sizeof(fixed_method_table[0]);

// JNI Method Registration Table End

static void unload_all_classes(JNIEnv* env) {
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, byteArrayClass);
    NETTY_JNI_UTIL_UNLOAD_CLASS(env, stringClass);
    NETTY_JNI_UTIL_UNLOAD_CLASS_WEAK(env, sslTaskClassWeak);
    NETTY_JNI_UTIL_UNLOAD_CLASS_WEAK(env, sslPrivateKeyMethodTaskClassWeak);
    NETTY_JNI_UTIL_UNLOAD_CLASS_WEAK(env, sslPrivateKeyMethodSignTaskClassWeak);
    NETTY_JNI_UTIL_UNLOAD_CLASS_WEAK(env, sslPrivateKeyMethodDecryptTaskClassWeak);
    NETTY_JNI_UTIL_UNLOAD_CLASS_WEAK(env, certificateTaskClassWeak);
    NETTY_JNI_UTIL_UNLOAD_CLASS_WEAK(env, verifyTaskClassWeak);
    NETTY_JNI_UTIL_UNLOAD_CLASS_WEAK(env, handshakeCompleteCallbackClassWeak);
    NETTY_JNI_UTIL_UNLOAD_CLASS_WEAK(env, servernameCallbackClassWeak);
    NETTY_JNI_UTIL_UNLOAD_CLASS_WEAK(env, keylogCallbackClassWeak);
    NETTY_JNI_UTIL_UNLOAD_CLASS_WEAK(env, sessionCallbackClassWeak);
    NETTY_JNI_UTIL_UNLOAD_CLASS_WEAK(env, sessionTicketCallbackClassWeak);
}

// IMPORTANT: If you add any NETTY_JNI_UTIL_LOAD_CLASS or NETTY_JNI_UTIL_FIND_CLASS calls you also need to update
//            Quiche to reflect that.
jint netty_boringssl_JNI_OnLoad(JNIEnv* env, const char* packagePrefix) {
    int ret = JNI_ERR;
    int staticallyRegistered = 0;
    int nativeRegistered = 0;
    char* name = NULL;
    char* combinedName = NULL;

    jclass sslTaskClass = NULL;
    jclass sslPrivateKeyMethodTaskClass = NULL;
    jclass sslPrivateKeyMethodSignTaskClass = NULL;
    jclass sslPrivateKeyMethodDecryptTaskClass = NULL;
    jclass certificateTaskClass = NULL;
    jclass verifyTaskClass = NULL;
    jclass handshakeCompleteCallbackClass = NULL;
    jclass servernameCallbackClass = NULL;
    jclass keylogCallbackClass = NULL;
    jclass sessionCallbackClass = NULL;
    jclass sessionTicketCallbackClass = NULL;

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
    NETTY_JNI_UTIL_LOAD_CLASS(env, stringClass, "java/lang/String", done);

    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/handler/codec/quic/BoringSSLTask", name, done);

    NETTY_JNI_UTIL_LOAD_CLASS_WEAK(env, sslTaskClassWeak, name, done);
    NETTY_JNI_UTIL_NEW_LOCAL_FROM_WEAK(env, sslTaskClass, sslTaskClassWeak, done);

    NETTY_JNI_UTIL_GET_FIELD(env, sslTaskClass, sslTaskReturnValue, "returnValue", "I", done);
    NETTY_JNI_UTIL_GET_FIELD(env, sslTaskClass, sslTaskComplete, "complete", "Z", done);
    NETTY_JNI_UTIL_GET_METHOD(env, sslTaskClass, sslTaskDestroyMethod, "destroy", "()V", done);

    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/handler/codec/quic/BoringSSLPrivateKeyMethodTask", name, done);
    NETTY_JNI_UTIL_LOAD_CLASS_WEAK(env, sslPrivateKeyMethodTaskClassWeak, name, done);
    NETTY_JNI_UTIL_NEW_LOCAL_FROM_WEAK(env, sslPrivateKeyMethodTaskClass, sslPrivateKeyMethodTaskClassWeak, done);
    NETTY_JNI_UTIL_GET_FIELD(env, sslPrivateKeyMethodTaskClass, sslPrivateKeyMethodTaskResultBytesField, "resultBytes", "[B", done);

    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/handler/codec/quic/BoringSSLPrivateKeyMethodSignTask", name, done);
    NETTY_JNI_UTIL_LOAD_CLASS_WEAK(env, sslPrivateKeyMethodSignTaskClassWeak, name, done);
    NETTY_JNI_UTIL_NEW_LOCAL_FROM_WEAK(env, sslPrivateKeyMethodSignTaskClass, sslPrivateKeyMethodSignTaskClassWeak, done);
    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/handler/codec/quic/BoringSSLPrivateKeyMethod;)V", name, done);
    NETTY_JNI_UTIL_PREPEND("(JI[BL", name, combinedName, done);
    free(name);
    name = combinedName;
    combinedName = NULL;
    NETTY_JNI_UTIL_GET_METHOD(env, sslPrivateKeyMethodSignTaskClass, sslPrivateKeyMethodSignTaskInitMethod, "<init>", name, done);

    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/handler/codec/quic/BoringSSLPrivateKeyMethodDecryptTask", name, done);
    NETTY_JNI_UTIL_LOAD_CLASS_WEAK(env, sslPrivateKeyMethodDecryptTaskClassWeak, name, done);
    NETTY_JNI_UTIL_NEW_LOCAL_FROM_WEAK(env, sslPrivateKeyMethodDecryptTaskClass, sslPrivateKeyMethodDecryptTaskClassWeak, done);
    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/handler/codec/quic/BoringSSLPrivateKeyMethod;)V", name, done);
    NETTY_JNI_UTIL_PREPEND("(J[BL", name, combinedName, done);
    free(name);
    name = combinedName;
    combinedName = NULL;
    NETTY_JNI_UTIL_GET_METHOD(env, sslPrivateKeyMethodDecryptTaskClass, sslPrivateKeyMethodDecryptTaskInitMethod, "<init>", name, done);

    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/handler/codec/quic/BoringSSLCertificateCallbackTask", name, done);
    NETTY_JNI_UTIL_LOAD_CLASS_WEAK(env, certificateTaskClassWeak, name, done);
    NETTY_JNI_UTIL_NEW_LOCAL_FROM_WEAK(env, certificateTaskClass, certificateTaskClassWeak, done);
    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/handler/codec/quic/BoringSSLCertificateCallback;)V", name, done);
    NETTY_JNI_UTIL_PREPEND("(J[B[[B[Ljava/lang/String;L", name, combinedName, done);
    free(name);
    name = combinedName;
    combinedName = NULL;
    NETTY_JNI_UTIL_GET_METHOD(env, certificateTaskClass, certificateTaskClassInitMethod, "<init>", name, done);
    NETTY_JNI_UTIL_GET_FIELD(env, certificateTaskClass, certificateTaskClassChainField, "chain", "J", done);
    NETTY_JNI_UTIL_GET_FIELD(env, certificateTaskClass, certificateTaskClassKeyField, "key", "J", done);

    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/handler/codec/quic/BoringSSLCertificateVerifyCallbackTask", name, done);
    NETTY_JNI_UTIL_LOAD_CLASS_WEAK(env, verifyTaskClassWeak, name, done);
    NETTY_JNI_UTIL_NEW_LOCAL_FROM_WEAK(env, verifyTaskClass, verifyTaskClassWeak, done);
    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/handler/codec/quic/BoringSSLCertificateVerifyCallback;)V", name, done);
    NETTY_JNI_UTIL_PREPEND("(J[[BLjava/lang/String;L", name, combinedName, done);
    free(name);
    name = combinedName;
    combinedName = NULL;
    NETTY_JNI_UTIL_GET_METHOD(env, verifyTaskClass, verifyTaskClassInitMethod, "<init>", name, done);

    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/handler/codec/quic/BoringSSLHandshakeCompleteCallback", name, done);
    NETTY_JNI_UTIL_LOAD_CLASS_WEAK(env, handshakeCompleteCallbackClassWeak, name, done);
    NETTY_JNI_UTIL_NEW_LOCAL_FROM_WEAK(env, handshakeCompleteCallbackClass, handshakeCompleteCallbackClassWeak, done);
    NETTY_JNI_UTIL_GET_METHOD(env, handshakeCompleteCallbackClass, handshakeCompleteCallbackMethod, "handshakeComplete", "(J[BLjava/lang/String;Ljava/lang/String;[B[[BJJ[BZ)V", done);

    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/handler/codec/quic/BoringSSLTlsextServernameCallback", name, done);
    NETTY_JNI_UTIL_LOAD_CLASS_WEAK(env, servernameCallbackClassWeak, name, done);
    NETTY_JNI_UTIL_NEW_LOCAL_FROM_WEAK(env, servernameCallbackClass, servernameCallbackClassWeak, done);
    NETTY_JNI_UTIL_GET_METHOD(env, servernameCallbackClass, servernameCallbackMethod, "selectCtx", "(JLjava/lang/String;)J", done);

    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/handler/codec/quic/BoringSSLKeylogCallback", name, done);
    NETTY_JNI_UTIL_LOAD_CLASS_WEAK(env, keylogCallbackClassWeak, name, done);
    NETTY_JNI_UTIL_NEW_LOCAL_FROM_WEAK(env, keylogCallbackClass, keylogCallbackClassWeak, done);
    NETTY_JNI_UTIL_GET_METHOD(env, keylogCallbackClass, keylogCallbackMethod, "logKey", "(JLjava/lang/String;)V", done);

    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/handler/codec/quic/BoringSSLSessionCallback", name, done);
    NETTY_JNI_UTIL_LOAD_CLASS_WEAK(env, sessionCallbackClassWeak, name, done);
    NETTY_JNI_UTIL_NEW_LOCAL_FROM_WEAK(env, sessionCallbackClass, sessionCallbackClassWeak, done);
    NETTY_JNI_UTIL_GET_METHOD(env, sessionCallbackClass, sessionCallbackMethod, "newSession", "(JJJ[BZ[B)V", done);

    NETTY_JNI_UTIL_PREPEND(packagePrefix, "io/netty/handler/codec/quic/BoringSSLSessionTicketCallback", name, done);
    NETTY_JNI_UTIL_LOAD_CLASS_WEAK(env, sessionTicketCallbackClassWeak, name, done);
    NETTY_JNI_UTIL_NEW_LOCAL_FROM_WEAK(env, sessionTicketCallbackClass, sessionTicketCallbackClassWeak, done);
    NETTY_JNI_UTIL_GET_METHOD(env, sessionTicketCallbackClass, sessionTicketCallbackMethod, "findSessionTicket", "([B)[B", done);

    verifyCallbackIdx = SSL_CTX_get_ex_new_index(0, NULL, NULL, NULL, NULL);
    certificateCallbackIdx = SSL_CTX_get_ex_new_index(0, NULL, NULL, NULL, NULL);
    handshakeCompleteCallbackIdx = SSL_CTX_get_ex_new_index(0, NULL, NULL, NULL, NULL);
    servernameCallbackIdx = SSL_CTX_get_ex_new_index(0, NULL, NULL, NULL, NULL);
    keylogCallbackIdx = SSL_CTX_get_ex_new_index(0, NULL, NULL, NULL, NULL);
    sessionCallbackIdx = SSL_CTX_get_ex_new_index(0, NULL, NULL, NULL, NULL);
    sslPrivateKeyMethodIdx = SSL_CTX_get_ex_new_index(0, NULL, NULL, NULL, NULL);
    sslTaskIdx = SSL_get_ex_new_index(0, NULL, NULL, NULL, NULL);
    alpn_data_idx = SSL_CTX_get_ex_new_index(0, NULL, NULL, NULL, NULL);
    crypto_buffer_pool_idx = SSL_CTX_get_ex_new_index(0, NULL, NULL, NULL, NULL);
    sessionTicketCallbackIdx = SSL_CTX_get_ex_new_index(0, NULL, NULL, NULL, NULL);

    ret = NETTY_JNI_UTIL_JNI_VERSION;
done:
    if (ret == JNI_ERR) {
        if (staticallyRegistered == 1) {
            netty_jni_util_unregister_natives(env, packagePrefix, STATICALLY_CLASSNAME);
        }
        if (nativeRegistered == 1) {
            netty_jni_util_unregister_natives(env, packagePrefix, CLASSNAME);
        }

        unload_all_classes(env);
    }

    NETTY_JNI_UTIL_DELETE_LOCAL(env, sslTaskClass);
    NETTY_JNI_UTIL_DELETE_LOCAL(env, sslPrivateKeyMethodTaskClass);
    NETTY_JNI_UTIL_DELETE_LOCAL(env, sslPrivateKeyMethodSignTaskClass);
    NETTY_JNI_UTIL_DELETE_LOCAL(env, sslPrivateKeyMethodDecryptTaskClass);
    NETTY_JNI_UTIL_DELETE_LOCAL(env, certificateTaskClass);
    NETTY_JNI_UTIL_DELETE_LOCAL(env, verifyTaskClass);
    NETTY_JNI_UTIL_DELETE_LOCAL(env, handshakeCompleteCallbackClass);
    NETTY_JNI_UTIL_DELETE_LOCAL(env, servernameCallbackClass);
    NETTY_JNI_UTIL_DELETE_LOCAL(env, keylogCallbackClass);
    NETTY_JNI_UTIL_DELETE_LOCAL(env, sessionCallbackClass);
    NETTY_JNI_UTIL_DELETE_LOCAL(env, sessionTicketCallbackClass);

    return ret;
}

void netty_boringssl_JNI_OnUnload(JNIEnv* env, const char* packagePrefix) {
    unload_all_classes(env);

    netty_jni_util_unregister_natives(env, packagePrefix, STATICALLY_CLASSNAME);
    netty_jni_util_unregister_natives(env, packagePrefix, CLASSNAME);
}

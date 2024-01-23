#!/bin/sh
# ----------------------------------------------------------------------------
# Copyright 2021 The Netty Project
#
# The Netty Project licenses this file to you under the Apache License,
# version 2.0 (the "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at:
#
#   https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# Generate a new, self-signed root CA
openssl req -extensions v3_ca -new -x509 -days 30 -nodes -subj "/CN=NettyTestRoot" -newkey rsa:2048 -sha512 -out mutual_auth_ca.pem -keyout mutual_auth_ca.key

# Generate a certificate/key for the server
openssl req -new -keyout mutual_auth_server.key -nodes -newkey rsa:2048 -subj "/CN=NettyTestServer" | \
    openssl x509 -req -CAkey mutual_auth_ca.key -CA mutual_auth_ca.pem -days 36500 -set_serial $RANDOM -sha512 -out mutual_auth_server.pem

# Generate a certificate/key for the server to use for Hostname Verification via localhost
openssl req -new -keyout localhost_server_rsa.key -nodes -newkey rsa:2048 -subj "/CN=localhost" | \
    openssl x509 -req -CAkey mutual_auth_ca.key -CA mutual_auth_ca.pem -days 36500 -set_serial $RANDOM -sha512 -out localhost_server.pem
openssl pkcs8 -topk8 -inform PEM -outform PEM -in localhost_server_rsa.key -out localhost_server.key -nocrypt
rm localhost_server_rsa.key

# Generate a certificate/key for the server to fail for Hostname Verification via localhost
openssl req -new -keyout notlocalhost_server_rsa.key -nodes -newkey rsa:2048 -subj "/CN=NOTlocalhost" | \
    openssl x509 -req -CAkey mutual_auth_ca.key -CA mutual_auth_ca.pem -days 36500 -set_serial $RANDOM -sha512 -out notlocalhost_server.pem
openssl pkcs8 -topk8 -inform PEM -outform PEM -in notlocalhost_server_rsa.key -out notlocalhost_server.key -nocrypt
rm notlocalhost_server_rsa.key

# Generate an invalid intermediate CA which will be used to sign the client certificate
openssl req -new -keyout mutual_auth_invalid_intermediate_ca.key -nodes -newkey rsa:2048 -subj "/CN=NettyTestInvalidIntermediate" | \
    openssl x509 -req -CAkey mutual_auth_ca.key -CA mutual_auth_ca.pem -days 36500 -set_serial $RANDOM -sha512 -out mutual_auth_invalid_intermediate_ca.pem

# Generate a client certificate signed by the invalid intermediate CA
openssl req -new -keyout mutual_auth_invalid_client.key -nodes -newkey rsa:2048 -subj "/CN=NettyTestInvalidClient/UID=ClientWithInvalidCa" | \
    openssl x509 -req -CAkey mutual_auth_invalid_intermediate_ca.key -CA mutual_auth_invalid_intermediate_ca.pem -days 36500 -set_serial $RANDOM -sha512 -out mutual_auth_invalid_client.pem

# Generate a valid intermediate CA which will be used to sign the client certificate
openssl req -new -keyout mutual_auth_intermediate_ca.key -nodes -newkey rsa:2048 -out mutual_auth_intermediate_ca.key
openssl req -new -sha512 -key mutual_auth_intermediate_ca.key -subj "/CN=NettyTestIntermediate" -out intermediate.csr
openssl x509 -req -days 1825 -in intermediate.csr -extfile openssl.cnf -extensions v3_ca -CA mutual_auth_ca.pem -CAkey mutual_auth_ca.key -set_serial $RANDOM -out mutual_auth_intermediate_ca.pem

# Generate a client certificate signed by the intermediate CA
openssl req -new -keyout mutual_auth_client.key -nodes -newkey rsa:2048 -subj "/CN=NettyTestClient/UID=Client" | \
    openssl x509 -req -CAkey mutual_auth_intermediate_ca.key -CA mutual_auth_intermediate_ca.pem -days 36500 -set_serial $RANDOM -sha512 -out mutual_auth_client.pem

# For simplicity, squish everything down into PKCS#12 keystores
cat mutual_auth_invalid_intermediate_ca.pem mutual_auth_ca.pem > mutual_auth_invalid_client_cert_chain.pem
cat mutual_auth_intermediate_ca.pem mutual_auth_ca.pem > mutual_auth_client_cert_chain.pem
openssl pkcs12 -export -in mutual_auth_server.pem -inkey mutual_auth_server.key -certfile mutual_auth_ca.pem -out mutual_auth_server.p12 -password pass:example
openssl pkcs12 -export -in mutual_auth_invalid_client.pem -inkey mutual_auth_invalid_client.key -certfile mutual_auth_invalid_client_cert_chain.pem -out mutual_auth_invalid_client.p12 -password pass:example
openssl pkcs12 -export -in mutual_auth_client.pem -inkey mutual_auth_client.key -certfile mutual_auth_client_cert_chain.pem -out mutual_auth_client.p12 -password pass:example

#PKCS#1
openssl genrsa -out rsa_pkcs8_unencrypted.key 2048
openssl genrsa -des3 -out rsa_pkcs8_des3_encrypted.key -passout pass:example 2048
openssl genrsa -aes128 -out rsa_pkcs8_aes_encrypted.key -passout pass:example 2048
# If using OpenSSL >3 use -traditional with openssl genrsa to generate traditional PKCS#1 keys
openssl genrsa -traditional -out rsa_pkcs1_unencrypted.key 2048
openssl genrsa -traditional -des3 -out rsa_pkcs1_des3_encrypted.key -passout pass:example 2048
openssl genrsa -traditional -aes128 -out rsa_pkcs1_aes_encrypted.key -passout pass:example 2048
openssl dsaparam -out dsaparam.pem 2048
openssl gendsa -out dsa_pkcs1_unencrypted.key dsaparam.pem
openssl gendsa -des3 -out dsa_pkcs1_des3_encrypted.key -passout pass:example dsaparam.pem
openssl gendsa -aes128 -out dsa_pkcs1_aes_encrypted.key -passout pass:example dsaparam.pem

# PBES2
openssl genrsa -out rsa_pbes2.key
openssl req -new -subj "/CN=NettyTest" -key rsa_pbes2.key -out rsa_pbes2.csr
openssl x509 -req -days 36500 -in rsa_pbes2.csr -signkey rsa_pbes2.key -out rsa_pbes2.crt
openssl pkcs8 -topk8 -inform PEM -in rsa_pbes2.key -outform pem -out rsa_pbes2_enc_pkcs8.key -v2 aes-256-cbc -passin pass:12345678 -passout pass:12345678

# Clean up intermediate files
rm intermediate.csr
rm mutual_auth_ca.key mutual_auth_invalid_client.key mutual_auth_client.key mutual_auth_server.key mutual_auth_invalid_intermediate_ca.key mutual_auth_intermediate_ca.key
rm mutual_auth_invalid_client.pem mutual_auth_client.pem mutual_auth_server.pem mutual_auth_client_cert_chain.pem mutual_auth_invalid_intermediate_ca.pem mutual_auth_intermediate_ca.pem mutual_auth_invalid_client_cert_chain.pem
rm dsaparam.pem
rm rsa_pbes2.crt rsa_pbes2.csr rsa_pbes2.key

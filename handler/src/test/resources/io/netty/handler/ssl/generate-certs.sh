#!/bin/sh

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

# Clean up intermediate files
rm intermediate.csr
rm mutual_auth_ca.key mutual_auth_invalid_client.key mutual_auth_client.key mutual_auth_server.key mutual_auth_invalid_intermediate_ca.key mutual_auth_intermediate_ca.key
rm mutual_auth_invalid_client.pem mutual_auth_client.pem mutual_auth_server.pem mutual_auth_client_cert_chain.pem mutual_auth_invalid_intermediate_ca.pem mutual_auth_intermediate_ca.pem mutual_auth_invalid_client_cert_chain.pem

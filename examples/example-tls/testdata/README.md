## Key Generation
### CA Key/Cert Generation
```
openssl genrsa -out ca.key 4096
openssl req -new -x509 -key ca.key -sha256 -subj "/C=US/ST=NJ/O=CA, Inc." -days 365 -out ca.cert
openssl x509 -in ca.cert -out ca.pem
```
### Client Key/Cert Generation
```
openssl genrsa -out client.key.rsa 4096
openssl pkcs8 -topk8 -in client.key.rsa -out client.key -nocrypt
openssl req -new -key client.key -sha256 -subj "/C=US/ST=NJ/O=CA/CN=localhost" -out client.csr
openssl x509 -req -in client.csr -CA ca.cert -CAkey ca.key -CAcreateserial -out client.pem -days 365 -sha256 -extfile certificate.conf -extensions req_ext
```

### Server Key/Cert Generation
```
openssl genrsa -out service.key.rsa 4096
openssl pkcs8 -topk8 -in service.key.rsa -out service.key -nocrypt
openssl req -new -key service.key -sha256 -subj "/C=US/ST=NJ/O=CA/CN=localhost" -out service.csr
openssl x509 -req -in service.csr -CA ca.cert -CAkey ca.key -CAcreateserial -out service.pem -days 365 -sha256 -extfile certificate.conf -extensions req_ext
```

### Clean up files
```
rm *.rsa
rm *.csr
rm ca.srl
```

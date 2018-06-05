openssl req -nodes -x509 -sha256 -newkey rsa:4096 -keyout "mudsigner.key" -out "mudsigner.crt" -days 3560 -subj "/C=US/ST=Maryland/L=Gaithersburg/O=NIST/OU=ITL/CN=localhost"

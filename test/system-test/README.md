
Put the following in /etc/hosts of the sdn controller host

    127.0.0.1      sensor.nist.local
    127.0.0.1      otherman.nist.local


Run the server as follows:

1. Build the server
2. cd karaf/target/assembly/bin
3. ./karaf clean

Now configure the server from a second terminal:

   python configure.py

Then start the server components for the manufacturer:

   cd mudfile-server
   
READ the README.md there and proceed accordingly.


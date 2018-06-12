This tests whether the mud server fetches and installs a MUD file from the MUD profile server.

Make sure your /etc/hosts has the following entries on the CONTROLLER (OpenDaylight) host:

    203.0.113.13    www.nist.local
    203.0.113.14    www.antd.local
    127.0.0.1       toaster.nist.local

Generate certificate (self signed ) :

    sh gen-cert.sh

Import into the Java kesytore (edit this to point to your keystore):

    sh import-cert.sh

Sign the mudfile:

    sh sign-mudfile.sh

Verify the signature.

    sh verify.sh
    
For this test you need to start the mud profile file server on the Controller host as follows:

    sudo python mudfile-server.py

Test to see if your configuration is working (this should succeed):

    wget --no-check-certificate https://toaster.nist.local/super1/mudfile.json.sha256



Run the following

    sh sign-and-import.sh

Put the following in /etc/hosts of the sdn controller host

    127.0.0.1      sensor.nist.local
    127.0.0.1      otherman.nist.local

Start the server before the test

    sudo -E python mudfile-server.py

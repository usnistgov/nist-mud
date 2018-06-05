This tests whether the mud server fetches and installs a MUD file from the MUD profile server.

Make sure your /etc/hosts has the following entries on the CONTROLLER (OpenDaylight) host:

    203.0.113.13    www.nist.local
    203.0.113.14    www.antd.local
    127.0.0.1       toaster.nist.local

For this test you need to start the mud profile file server on the Controller host:

    sudo python mudfile-server.py
    



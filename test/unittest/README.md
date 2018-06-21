
Make sure you have the setup described in ../../README.md

Edit config/sdnmud-config.json to specify keystore-home and keypass. This is used for mud file verification in the DHCP test. Here is a sample.


    {
        "sdnmud-config" : {
                "keystore-home": "/home/mranga/jdk1.8.0_102/jre/lib/security/cacerts",
                "key-pass" : "changeit"
        }
    }

    

The each test is its own directory and can be run standalone. You have to start the OpenDaylight controller and load the necessary feature to do the test.
For example, for mud tests, you would load the features-sdnmud feature. You can run the test as follows:

    sudo -E UNITTEST=1 python mud-test.py
   
This will exercise the mud implementation and check if it is working as expected. If you would like to exercise the 
implementation from the command line, try the following
  
    sudo -E UNITTEST=0 python mud-test.py

This will take you to the mininet command line from where you can run tests manually.

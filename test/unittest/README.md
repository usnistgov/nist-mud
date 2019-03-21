

Build the sdnmud project using mvn 

Run the following script on the *ODL controller host*:

      sh copy-test-files-to-odl-cache.sh

This will copy the mudfiles to the cache in preparation for testing.
To run mud-dhcp-test make sure you have the [setup described in ../../README.md](../../README.md)

Then 

     cd karaf/target/assembly/
     rm journal/*
     rm snapshots/*

Then
    
    cd bin
    ./karaf clean

At the karaf prompt, install the sdnmud feature 

     karaf> feature:install features-sdnmud

Tests are run from the mininet emulation machine. 
*important -- run the copy-test-files-to-odl-cache.sh script on the controller before you do this, or the tests will fail.*
You can run the test as follows in each mud\*test directory:

    sudo -E UNITTEST=1 python mud-test.py
   
This will exercise the mud implementation and check if it is working as expected. If you would like to exercise the 
implementation from the command line, try the following
  
    sudo -E UNITTEST=0 python mud-test.py

This will take you to the mininet command line from where you can run tests manually.

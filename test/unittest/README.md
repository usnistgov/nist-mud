
Make sure you have the setup described in ../../README.md

Run the following script:

      copy-test-files-to-odl-cache.sh

    
The unittests are only partially automated at this time. Please clean the karaf directories before running each test:

     cd karaf/target/assembly/
     rm journal/*
     rm snapshots/*

Then
    
    cd bin
    ./karaf clean
    feature:install features-sdnmud
   
And then run the tests. Before you run each test, please do the above steps manually.

Help needed in fully automating the tests.

The each test is its own directory and can be run standalone. You have to start the OpenDaylight controller and load the necessary feature to do the test.
For example, for mud tests, you would load the features-sdnmud feature. You can run the test as follows:

    sudo -E UNITTEST=1 python mud-test.py
   
This will exercise the mud implementation and check if it is working as expected. If you would like to exercise the 
implementation from the command line, try the following
  
    sudo -E UNITTEST=0 python mud-test.py

This will take you to the mininet command line from where you can run tests manually.

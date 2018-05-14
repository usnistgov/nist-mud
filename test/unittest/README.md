For running the tests you will need RYU.

On the emulation vm, RYU is used control portions of the test network. It is not
part of the MUD implementation under test. We are using it strictly as
a learning switch controller to set up our topology.

     apt install gcc python-dev libffi-dev libssl-dev libxml2-dev libxslt1-dev zlib1g-dev
     git clone git://github.com/osrg/ryu.git
     cd ryu; pip install .
     pip install -r tools/optional-requires

The each test is its own directory and can be run standalone. You have to start the OpenDaylight controller and load the necessary feature to do the test.
For example, for mud tests, you would load the features-sdnmud feature. You can run the test as follows:

    sudo -E UNITTEST=1 python mud-test.py
   
This will exercise the mud implementation and check if it is working as expected. If you would like to exercise the 
implementation from the command line, try the following
  
    sudo -E UNITTEST=0 python mud-test.py

This will take you to the mininet command line from where you can run tests manually.

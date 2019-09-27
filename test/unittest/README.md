#### Configure the emulation VM ####

You will need an emulation Linux Virtual machine that runs mininet.

Install dnsmasq on your emulation vm.

       sudo apt-get install dnsmasq

Install python requests on your emulation vm

       sudo pip install python-requests


In order for DNS to work on mininet hosts you should not be using local caching. 
Edit /etc/NetworkManager/NetworkManager.conf and comment out. 

        #dns=dnsmasq

For the dhcp test, edit /etc/dnsmasq.conf and set up DHCP addresses for testing:

        no-hosts
        addn-hosts=/etc/dnsmasq.hosts
        dhcp-range=10.0.0.1,10.0.0.10,72h
        dhcp-host=00:00:00:00:00:01,10.0.0.1
        dhcp-host=00:00:00:00:00:02,10.0.0.2
        dhcp-host=00:00:00:00:00:03,10.0.0.3


Add a fake hosts in /etc/dnsmasq.hosts of the *emulation* machine only  by adding the following lines:

      203.0.113.13    www.nist.local
      203.0.113.14    www.antd.local
      203.0.113.15    printer.nist.local

Add the following to /etc/hosts on the *emulation* machine only in order to avoid timeouts:
 
     203.0.113.13	www.nist.local
     203.0.113.14	www.antd.local
     203.0.113.15       myprinter.nist.local

Make the following change in /etc/nswitch.conf 

      #hosts:          files mdns4_minimal [NOTFOUND=return] dns
      hosts: files dns


Kill any existing instance of dnsmasq on the emulation VM. We will
restart it in the test script.

      sudo pkill dnsmasq

If dnsmasq is running as a service, perform the following.
      
      sudo sed -i 's/^dns=dnsmasq/#&/' /etc/NetworkManager/NetworkManager.conf
      sudo service network-manager restart
      sudo service networking restart
      sudo killall dnsmasq


### Configure the SDN Controller (OpenDaylight)  Host ###

Add the following to /etc/hosts on your *controller* host so that the java library can look up our fake host.

      127.0.0.1      dhcptest.nist.local

(We will run the "manufacturer server" on 127.0.0.1 on the controller host.)

Run the following script on the *controller host*:

      sh copy-test-files-to-odl-cache.sh

This will copy the mudfiles to the cache in preparation for testing.
To run mud-dhcp-test make sure you have the [setup described in ../../README.md](../../README.md)

Then clean the state of the controller:

     cd karaf/target/assembly/
     rm journal/*
     rm snapshots/*

Now start the controller:
    
    cd bin
    ./karaf clean

At the karaf prompt, install the sdnmud feature 

     karaf> feature:install features-sdnmud

### Running the UNIT tests ###

Tests are run from the mininet emulation machine. 

*important* BEFORE YOU BEGIN execute the following file from this directory  on the controller :

      sh  copy-test-files-to-odl-cache.sh 

This will copy the MUDfiles directly into the cache.

You can run the test as follows in each mud\*test directory:

    sudo -E UNITTEST=1 python mud-test.py
   
This will exercise the mud implementation and check if it is working as expected. If you would like to exercise the 
implementation from the command line, try the following
  
    sudo -E UNITTEST=0 python mud-test.py

This will take you to the mininet command line from where you can run tests manually.

For the DHCP test check the instructions in the mud-dhcp-test directory.

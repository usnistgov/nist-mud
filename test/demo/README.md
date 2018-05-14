This directory contains a python script that emulates a network on mininet. 
MUD flow rules are installed on an emulated switch. The following diagram
shows the topology of the demo:

![alt tag](../../docs/arch/DEMO-ARCH.png)

Run the demo as follows:

Create a virtual machine running Ubuntu 16. Install mininet on it.
We will call this the emulation machine.  You should run the test
environment on a separate VM. Otherwise, Mininet settings may interfere
with your settings on your host. We assume that OpenDaylight is on
another host different from your emulation VM (it can be co-resident on
the emulation host if you wish).

### Pre-requisites for the emulation VM ###

Allow yourself root privileges for user to save yourself some typing:

     sudo visudo
     <username> ALL=(ALL) NOPASSWD: ALL

Install mininet on the virtual machine.  


    sudo apt-get install openvswitch-switch

Install openvswitch on the mininet vm.

    sudo apt-get install mininet

Install python 2.7x if you don't have it (it should already be there).
Install python prerequisites.
	 
	 sudo pip install requests
	 sudo apt install curl

On the emulation vm, RYU is used control portions of the test network. It is not
part of the MUD implementation under test. We are using it strictly as
a learning switch controller to set up our topology.

     apt install gcc python-dev libffi-dev libssl-dev libxml2-dev libxslt1-dev zlib1g-dev
     git clone git://github.com/osrg/ryu.git
     cd ryu; pip install .
     pip install -r tools/optional-requires


#### Configure the emulation VM ####

Edit /etc/dnsmasq.conf.  Include the following:

      no-dhcp-interface=
      server=8.8.8.8
      no-hosts
      addn-hosts=/etc/dnsmasq.hosts

Add a fake host in /etc/dnsmasq.hosts by adding the following line.

      203.0.113.13    www.nist.local
      203.0.113.14    www.antd.local

Kill any existing instance of dnsmasq on the mininet VM. We will
restart it in the test script.

      sudo pkill dnsmasq

If dnsmasq is running as a service, perform the following.
      
      sudo sed -i 's/^dns=dnsmasq/#&/' /etc/NetworkManager/NetworkManager.conf
      sudo service network-manager restart
      sudo service networking restart
      sudo killall dnsmasq

Add the following line to /etc/resolv.conf on the virtual machine.
 
      nameserver 10.0.0.5

### Configure the Controller Host ###

Add the following to /etc/hosts on your controller so the java library can look up our fake host.

      203.0.113.13   www.nist.local
      203.0.113.14   www.antd.local


### Run the demo ###

Clean the cached flows from any previous runs (if they exist):

        cd $PROJECT_HOME/features-sdnmud/karaf/target/assembly
        rm snapshots/*
        rm journal/*

Start Karaf and load the feature:

        cd $PROJECT_HOME/features-sdnmud/karaf/target/assembly/bin
        ./karaf clean

You should see an OpenDaylight banner appear.

        opendaylight-user@root>feature:install features-sdnmud

Read the messages to ensure that the test network connected.
Check if the L2Switch flows are installed. On the mininet VM :

      ovs-ofctl dump-flows s1 -O openflow13

On the emulation VM:
 
      setenv CONTROLLER_ADDR=ip-address-of-OpenDaylight-controller.
      cd $PROJECT_HOME/test/demo
      sudo -E  python mud-test.py


On the emulation VM dump flows (see above). 
You should see 10 tables created. Now ping some hosts.
The following will work because mud rules have not been installed at yet:

     h1 ping h2 

Check if DNS is working for the fake host name. 

      mininet> h3 nslookup www.nist.local 

should return 203.0.113.13   

Check if we can route packets to our fake web server

      mininet> h1 wget http://www.nist.local/index.html  

Shoud fetch a web page from www.nist.local. Thus far we have tested
that our test network is working and is configured properly.  
Now we can exercise the MUD implementation.

Run the configuration script:

      python configure.py


This installs the following configuration files are installed on the controller.

- cpenodes.json : Tells the controller what switches are of interest
- access-control-list.json : The access control lists (from mudmaker).
- device-association.json  : Associates MAC address with the MUD URI. This information
   would be transmitted to the MUD ACL server using one of the three mechanisms described in the MUD draft.
- controllerclass-mapping.json : Associates ip addresses to controller classes. 
   This would be typically specified by the adminstrator.
- ietfmud.json : Sets up a MUD profile that uses the access-control-lists


The access-control-list allows the following interactions for the device:

- From the device you can send and receive TCP traffic to www.nist.local on port 80
- From the device you can send and receive UDP traffic to other devices on the local network on port 8000
- The device can talk to its controller on port 8002. 
- From the device you can to other devices made by the same manufacturer on port 4000.

These restrictions are defined in the file access-control-list.json. The placeholder labels in that file are
resolved in the file controllerclass-mapping.json.

Dump flows again. You should see table 3 and table 4 is populated to set metadata. Table 5 contains the MUD rules
that are stated in terms of the Metadata. At this point, the system is configured and ready to exercise.

Try pinging from h1 to h2. This should be blocked:

    mininet> h1 ping h2

Next try the following:

     mininet> h1 udpping.py --client --port 4000 --host 10.0.0.2

This will send udp packets from h1 to h2 on port 4000. This is allowed by the access-control rules

Next try the following to exercise the local-networks flow:

     miniet> h1 python udpping.py --client --port 8000 --host 10.0.0.5

This will send udp packets from h1 to h2 on port 8002. This is allowed acording the access-control rules
On mininet dump flow table to see the flow rules that were accessed.


Next verify that you can access the controller:

    mininet> h1 python udpping.py --client --port 8002 --host 10.0.0.7

Note: The flow rules are cached. The first interaction will take a while until the flow rules are installed.
The first couple of packets could time out. For example, for the command above you may see the following in 
the mininet console :


    start ...
    Namespace(client=True, host='10.0.0.7', port=8002, quiet=False, server=False)
    PING 1 21:12:56
    UDPPING FAILED 
    PING 2 21:12:57
    UDPPING FAILED 
    PING 3 21:12:58
    RTT = 0.000797033309937
    PING 4 21:12:58
    RTT = 0.000434160232544
    PING 5 21:12:58
    RTT = 0.000604867935181
    PING 6 21:12:58
    RTT = 0.000573873519897
    PING 7 21:12:58
    RTT = 0.000661134719849
    PING 8 21:12:58
    RTT = 0.000535011291504
    PING 9 21:12:58
    RTT = 0.00066089630127
    PING 10 21:12:58
    RTT = 0.000537872314453
    [rc=8]






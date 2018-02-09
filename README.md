# IOT MUD implementation on Open Daylight with hooks for Elastic IDS #

# NOTE: This project is still in a state of early development. It may not work as advertised just yet. #

## Prerequisites ##

* Install JDK 1.8x and maven. 
* Install python 2.7x and whatever prerequisites Ryu wants.
* Install Ryu (for testing). 
* Install openvswtich 
* Eclipse -- highly recommended.

## How to build and test it it using mininet ##

### Prereqs for easier time ###

Create a virtual machine running Ubuntu 16.
On that virtual machine, allow root privileges for user by performing the following:

     sudo visudo
     <username> ALL=(ALL) NOPASSWD: ALL

Install prerequisites
	 
	 sudo pip install requests
	 sudo apt install curl



### Installing RYU Controller from source ###

RYU is used control a test router. It is not part of the implementation under test. We are using it strictly 
as a learning switch controller.

     apt install gcc python-dev libffi-dev libssl-dev libxml2-dev libxslt1-dev zlib1g-dev
     git clone git://github.com/osrg/ryu.git
     cd ryu; pip install .
     pip install -r tools/optional-requires



### Building ###

Copy maven/settings.xml to ~/.m2

Run maven
    
      cd sdniot-aggregator
      mvn -e clean install -nsu -Dcheckstyle.skip -DskipTests -Dmaven.javadoc.skip=true

### Testing ###

You should run the test environment on a separate VM. Mininet settings may interfere with
your settings on your host otherwise.

#### Test enviornment setup ####

Edit /etc/dnsmasq.conf.  Include the following:

      no-dhcp-interface=
      server=8.8.8.8
      no-hosts
      addn-hosts=/etc/dnsmasq.hosts

Add a fake host in /etc/dnsmasq.hosts by adding the following line.

      203.0.113.13    www.nist.local

Kill any existing instance of dnsmasq on the host.

      sudo pkill dnsmasq

If dnsmasq is running as a service, perform the following.
      
      sudo sed -i 's/^dns=dnsmasq/#&/' /etc/NetworkManager/NetworkManager.conf
      sudo service network-manager restart
      sudo service networking restart
      sudo killall dnsmasq

Add the following line to /etc/resolv.conf
 
      nameserver 10.0.0.5

Add the following to /etc/hosts so the java library can look up our fake host.

      203.0.113.13   www.nist.local

Start Karaf

      cd sdniot-aggregator/karaf/target/assembly/bin
      karaf clean

Install the feature using 

      feature:install features-sdniot

Start the mininet test environment

      setenv CONTROLLER_ADDR=ip address of OpenDaylight controller.
      cd $PROJECT_HOME/test
      sudo -E python topo1.py

Check if the L2Switch flows are installed

      ovs-ofctl dump-flows s1 -O openflow13

Check if DNS is working for the fake host name.

      h3 nslookup www.nist.local 

      should return 203.0.113.13   

Check if we can route packets to our fake web server

      h1 wget http://www.nist.local/index.html  

Shoud fetch a web page from www.nist.local. Now you can exercise the MUD implementation.

#### Test Device to Device Flow and IDS  ####

Install configuration files 

        sh postit.sh

Installs the following:

- topology.json 
- access-control-list.json : The access control lists.
- device-association.json  : Associates MAC address with the MUD URI.
- controllerclass-mapping.json : Associates ip addresses to controller classes.

Dump flows. You should see 6 tables created with the last one containing the Flows for the L2 switch.
The following will work because mud rules have not been installed at yet:

     h1 ping h2 

Install MUD rules and configure the IDS.

      sh postit1.sh

Dump flows again. You should see table 0 and table 1 is populated to set metadata.

Now configure IDS

     sh postit2.sh


You should see the flows corresponding to flow diversion to the IDS installed at this point. Check it:

      ovs-ofctl dump-flows s1 -O openflow13
      ovs-ofctl dump-flows s2 -O openflow13


You should see mpls flows if all went well for example, in s1 look for a flow like this:

      cookie=0xa2d5e, duration=15.918s, table=2, n_packets=0, n_bytes=0, hard_timeout=3000, priority=35,metadata=0xa2d5e actions=push_mpls:0x8847,set_field:666974->mpls_label,goto_table:4

This means the IDS script registered itself successfully.

Now from mininnet you can exercise MUD. The following should time out:

     h1 ping h2 

Now tail the capture file of the IDS:

    tail -f capture.txt

Next try the following:

     h1 same-man-test.py --client

This will send udp packets from h1 to h2 on port 4000.

Verify that the packets were captured in capture.txt (you should see packets from 10.0.0.1 to 10.0.0.2 and back).


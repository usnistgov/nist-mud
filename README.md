# IOT MUD implementation on Open Daylight with hooks for Elastic IDS #

# NOTE: This project is still in a state of early development. It may not work as advertised just yet. #

## Prerequisites ##

* Install JDK 1.8x and maven. 
* Install python 2.7x and whatever prerequisites Ryu wants.
* Install Ryu (for testing). 
* Install openvswtich 
* Eclipse -- highly recommended.

## How to build and test it it using mininet ##

### Prerequisites ###

Create a virtual machine running Ubuntu 16. Install mininet on that virtual machine
   
    sudo apt-get install mininet


Allow root privileges for user by performing the following:

     sudo visudo
     <username> ALL=(ALL) NOPASSWD: ALL

Install python prerequisites
	 
	 sudo pip install requests
	 sudo apt install curl


RYU is used control a test router. It is not part of the MUD implementation under test. We are using it strictly 
as a learning switch controller to set up our topology.

     apt install gcc python-dev libffi-dev libssl-dev libxml2-dev libxslt1-dev zlib1g-dev
     git clone git://github.com/osrg/ryu.git
     cd ryu; pip install .
     pip install -r tools/optional-requires


### Building ###

Copy maven/settings.xml to ~/.m2

Run maven
    
      cd sdniot-aggregator
      mvn -e clean install -nsu -Dcheckstyle.skip -DskipTests -Dmaven.javadoc.skip=true

### Manual Testing ###

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

Install MUD rules 

      sh postit1.sh

Dump flows again. You should see table 0 and table 1 is populated to set metadata.


Next try the following:

     h1 udpping.py --client --port 4000 --host 10.0.0.2

This will send udp packets from h1 to h2 on port 4000. This is allowed by the access-control rules


Next try the following

     h1 udpping.py --client --port 8002 --host 10.0.0.6

This will send udp packets from h1 to h2 on port 8002. This is allowed by the access-control rules


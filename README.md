
### THIS PAGE IS UNDER CONSTRUCTION ###

# IOT MUD implementation on Open Daylight #

This includes two features:
 
- features-sdniot is the scalable MUD implementation. 
- features-ids is IDS support that allows flows to be sampled from the CPE switch.

## Prerequisites ##

On the Controller host:

* Install JDK 1.8x and maven 3.5 or above.
* Eclipse -- highly recommended if you want to look at code.

## How to build and test it it using mininet ##

Create a virtual machine running Ubuntu 16. 


### Pre-requisites for the Test VM ###

Allow root privileges for user to save yourself some typing:

     sudo visudo
     <username> ALL=(ALL) NOPASSWD: ALL

Install mininet on the virtual machine.  Install openvswitch on the mininet vm.

    sudo apt-get install openvswitch-switch
    sudo apt-get install mininet

Install python 2.7x if you don't have it (it should already be there).
Install python prerequisites.
	 
	 sudo pip install requests
	 sudo apt install curl

On the mininet vm. RYU is used control portions of the test network. It is not
part of the MUD implementation under test. We are using it strictly as
a learning switch controller to set up our topology.

     apt install gcc python-dev libffi-dev libssl-dev libxml2-dev libxslt1-dev zlib1g-dev
     git clone git://github.com/osrg/ryu.git
     cd ryu; pip install .
     pip install -r tools/optional-requires

### Architecture ###

### TBD ###

Our system consists of a collection of CPE switches. MUD flow rules
are installed only at CPE switches.  Packets that leave the CPE switch and
are sent to the NPE switch are tagged with a VLAN tag that identifies
the CPE switch from which they originated.  At the NPE switch the VLAN
tag is used to direct packets to a corresponding VNF switch.

The flow monitoring facility allows an IDS to indicate interest in specific classes of packets i.e:
- Packets that have hit a MUD flow rule and successfully been fowarded.
- Packets that have no MUD rule associated with it and that are forwarded to the Network provider.


The flow monitoring facility consists of two parts i.e.
- A CPE flow monitor that places flow rules attaching MPLS labels to packets of interest.
- An VNF switch packet diverter that diverts marked packets from the VNF switch to a registered daemon. These packets 
  can be provided to an Intrusion Detection System to analyze system behavior.




### Software Components ###

This project consists of the following components

- baseapp-aggregator : The base application that lays out the table structure and defines constants and configurations used by the other subprojects.
  This include the opendaylight l2-switch component.
- sdnmud-aggregator : An SDN implementation of the MUD specification on opendaylight. This component can be used independently of the others.
- vlan-aggregator : Stamps outbound packets from the CPE switches bound for the NPE switch. The NPE switch is an aggregator that sends vlan tagged packets to the appropriate VNF switch.
- flowmon-aggreagor : Sets MPLS tags on the outbound flows of the CPE switch for those packets that need to be monitored at the VNF switch.


### Building ###

Copy maven/settings.xml to ~/.m2

Run maven
      mvn -e clean install -nsu -Dcheckstyle.skip -DskipTests -Dmaven.javadoc.skip=true

### Manual Testing ###

You should run the test environment on a separate VM. Mininet settings may interfere with
your settings on your host otherwise. We assume that OpenDaylight is on another host 
(it can be co-resident if you wish).

#### Configure the Mininet VM ####

Edit /etc/dnsmasq.conf.  Include the following:

      no-dhcp-interface=
      server=8.8.8.8
      no-hosts
      addn-hosts=/etc/dnsmasq.hosts

Add a fake host in /etc/dnsmasq.hosts by adding the following line.

      203.0.113.13    www.nist.local

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

Add the following to /etc/hosts so the java library can look up our fake host.

      203.0.113.13   www.nist.local

On the host where you are running the controller, start Karaf

      cd sdnmud-aggregator/karaf/target/assembly/bin
      karaf clean
      
You should see an OpenDaylight banner appear.

Install the feature using in Karaf from the Karaf command line.

      feature:install features-sdnmud

Start the mininet test environment. On the mininet vm:

      setenv CONTROLLER_ADDR=ip-address-of-OpenDaylight-controller.
      cd $PROJECT_HOME/test
      sudo -E python topo1.py

Read the messages to ensure that the test network connected.
Check if the L2Switch flows are installed. On the mininet VM :

      ovs-ofctl dump-flows s1 -O openflow13

Check if DNS is working for the fake host name. 

      mininet> h3 nslookup www.nist.local 

should return 203.0.113.13   

Check if we can route packets to our fake web server

      mininet> h1 wget http://www.nist.local/index.html  

Shoud fetch a web page from www.nist.local. Thus far we have tested
that our test network is working.  Now we can exercise the MUD 
implementation.

#### Test Device to Device Flows and Local network flows  ####

Install configuration files. On the Mininet VM:
 
        cd $PROJECT_HOME/test
        sh postit.sh

This script installs the following:

- topology.json : Tells the controller what switches are of interest
- access-control-list.json : The access control lists (from mudmaker).
- device-association.json  : Associates MAC address with the MUD URI.
- controllerclass-mapping.json : Associates ip addresses to controller classes.

On the Mininet VM dump flows (see above). 
You should see 6 tables created with the last one containing the Flows for the L2 switch.
The following will work because mud rules have not been installed at yet:

     h1 ping h2 

Install MUD rules. On the mininet VM:

      sh postit1.sh

Dump flows again. You should see table 0 and table 1 is populated to set metadata.


Next try the following:

     mininet> h1 udpping.py --client --port 4000 --host 10.0.0.2

This will send udp packets from h1 to h2 on port 4000. This is allowed by the access-control rules

Next try the following to exercise the local-networks flow:

     miniet> h1 python udpping.py --client --port 8002 --host 10.0.0.6

This will send udp packets from h1 to h2 on port 8002. This is allowed acording the access-control rules
On mininet dump flow table to see the flow rules that were accessed.

Next try pinging from h1 to h2. This should be blocked:

    mininet> h1 ping h2

Note: The flow rules are cached. The first interaction will take a while until the flow rules are installed.

### Copyrights and Disclaimers ###

The following disclaimer applies to all code that was written by employees
of the National Institute of Standards and Technology.

This software was developed by employees of the National Institute of
Standards and Technology (NIST), and others. This software has been
contributed to the public domain. Pursuant to title 15 Untied States
Code Section 105, works of NIST employees are not subject to copyright
protection in the United States and are considered to be in the public
domain. As a result, a formal license is not needed to use this software.

This software is provided "AS IS." NIST MAKES NO WARRANTY OF ANY KIND,
EXPRESS, IMPLIED OR STATUTORY, INCLUDING, WITHOUT LIMITATION, THE
IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
NON-INFRINGEMENT AND DATA ACCURACY. NIST does not warrant or make any
representations regarding the use of the software or the results thereof,
including but not limited to the correctness, accuracy, reliability or
usefulness of this software.

Specific copyrights for code that has been re-used from other open 
source projects are noted in the source files as appropriate.

Please acknowledge our work if you re-use this code or design.


## Credits ##

* The MUD Standard was primarily authored by Eliot Lear (Cisco) in the OPSAWG interest group.
* Lead designer / developer for this project : M. Ranganathan <mranga@nist.gov>
* Design Contributors : Charif Mohammed, Doug Montgomery
* Project Manager Doug Montgomery <dougm@nist.gov>
* This is a product of the Advanced Networking Technologies Division.

## LIMITATIONS ##

This code is shared for early review. It is an implementation of an IETF
draft in progress. Much more testing and validation is required.

Please do not re-distribute until this repository is granted public access.
This will happen after:

1. The IETF draft has achieved an RFC status.
2. All issues are satisfactorily resolved.



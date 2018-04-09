from mininet.node import OVSController
from mininet.cli import CLI
from mininet.log import setLogLevel
from mininet.net import Mininet
from mininet.node import RemoteController
from mininet.topo import Topo
import pdb
import subprocess
import argparse
import os
import sys
import signal
from distutils.spawn import find_executable
from subprocess import call
from functools import partial
import time
import json
import requests
from mininet.node import Host
from mininet.log import setLogLevel 

"""
Set up a VLAN topology and ping from h1 to a host on the VLAN to test setting and
stripping of VLAN tags.
"""

class VLANHost( Host ):
    "Host connected to VLAN interface"

    def config( self, vlan=772, **params ):
        """Configure VLANHost according to (optional) parameters:
           vlan: VLAN ID for default interface"""

        r = super( VLANHost, self ).config( **params )

        intf = self.defaultIntf()
        # remove IP from default, "physical" interface
        self.cmd( 'ifconfig %s inet 0' % intf )
        # create VLAN interface
        self.cmd( 'vconfig add %s %d' % ( intf, vlan ) )
        # assign the host's IP to the VLAN interface
        self.cmd( 'ifconfig %s.%d inet %s' % ( intf, vlan, params['ip'] ) )
        # update the intf name and host's intf map
        newName = '%s.%d' % ( intf, vlan )
        # update the (Mininet) interface to refer to VLAN interface name
        intf.name = newName
        # add VLAN interface to host's name to intf map
        self.nameToIntf[ newName ] = intf

        return r


def setupTopology(controller_addr,dns_address, interface):
    "Create and run multiple link network"

    net = Mininet(controller=RemoteController)

    print "mininet created"

    c1 = net.addController('c1', ip=controller_addr,port=6653)
    print "addController ", controller_addr
    net1 = Mininet(controller=RemoteController)
    c2 = net1.addController('c2', ip="127.0.0.1",port=6673)


    # h1: IOT Device.
    # h2 : StatciDHCPD
    # h3# : router / NAT
    # h4 : Non IOT device.

    h1,h2,h3,h4,h5= net.addHost('h1'),net.addHost('h2'),net.addHost('h3'),net.addHost('h4'),net.addHost('h5')

    s1 = net.addSwitch('s1')
    # The host for dhclient
    s1.linkTo(h1)
    # The IOT device
    s1.linkTo(h2)
    # The MUD controller
    s1.linkTo(h3)
    # The MUD server runs here.
    s1.linkTo(h4)
    # The non-iot client runs here
    s1.linkTo(h5)

    h6 = net.addHost('h6')

    # Switch s2 is the "multiplexer".
    s2 = net1.addSwitch('s2')

    s3 = net.addSwitch('s3')


    # h7 and h9 are on a VLAN
    # Make sure we can reach the VLAN'ed hosts from h1.

    host = partial(VLANHost,vlan=772)
    h7 = net.addHost('h7', cls=host)

    h8 = net.addHost('h8')
   
    net.addLink(s3,h7)
    
    host = partial(VLANHost,vlan=772)
    h9 = net.addHost('h9', cls=host)

    net.addLink(s3,h9)
    s2.linkTo(h6)

    s2.linkTo(s3)
    # h8 is the ids.
    s2.linkTo(h8)
    # h9 is our fake server.
    # s2 linked to s3 via our router.
    h7.cmd( 'ip link set h7-eth0 down')
    h9.cmd( 'ip link set h9-eth0 down')
    s3.linkTo(h9)
    s3.linkTo(h7)

    # S2 is the NPE switch.
    # Direct link between S1 and S2
    s1.linkTo(s2)


    net.build()
    c1.start()
    s1.start([c1])
    s2.start([c2])
    s3.start([c1])

    net.start()
    net1.start()
     

    # Clean up any traces of the previous invocation (for safety)


    h1.setMAC("00:00:00:00:00:01","h1-eth0")
    h2.setMAC("00:00:00:00:00:02","h2-eth0")
    h3.setMAC("00:00:00:00:00:03","h3-eth0")
    h4.setMAC("00:00:00:00:00:04","h4-eth0")
    h5.setMAC("00:00:00:00:00:05","h5-eth0")
    h6.setMAC("00:00:00:00:00:06","h6-eth0")
    h8.setMAC("00:00:00:00:00:08","h8-eth0")
    #h9.setMAC("00:00:00:00:00:09","h9-eth0.772")
    #h7.setMAC("00:00:00:00:00:07","h7-eth0.772")

    
    # Set up a routing rule on h2 to route packets via h3
    h1.cmdPrint('ip route del default')
    h1.cmdPrint('ip route add default via 10.0.0.7 dev h1-eth0')

    # Set up a routing rule on h2 to route packets via h3
    h2.cmdPrint('ip route del default')
    h2.cmdPrint('ip route add default via 10.0.0.7 dev h2-eth0')

    # Set up a routing rule on h2 to route packets via h7
    h3.cmdPrint('ip route del default')
    h3.cmdPrint('ip route add default via 10.0.0.7 dev h3-eth0')

    # Set up a routing rule on h2 to route packets via h3
    h4.cmdPrint('ip route del default')
    h4.cmdPrint('ip route add default via 10.0.0.7 dev h4-eth0')

    # Set up a routing rule on h5 to route packets via h3
    h5.cmdPrint('ip route del default')
    h5.cmdPrint('ip route add default via 10.0.0.7 dev h5-eth0')

    # h6 is a localhost.
    h6.cmdPrint('ip route del default')
    h6.cmdPrint('ip route add default via 10.0.0.7 dev h6-eth0')

    # The IDS runs on h8
    h8.cmdPrint('ip route del default')
    h8.cmdPrint('ip route add default via 10.0.0.7 dev h8-eth0')

    # Start dnsmasq (our dns server).
    h5.cmdPrint('/usr/sbin/dnsmasq --server  10.0.4.3 --pid-file=/tmp/dnsmasq.pid'  )

    
    #h1.cmdPrint("ping 10.0.0.7")


    print "*********** System ready *********"

    cli = CLI( net )
    h1.terminate()
    h2.terminate()
    h3.terminate()
    net.stop()

def startTestServer(host):
    """
    Start a test server to add to the allow access rules.
    """
    os.chdir("%s/mininet/testserver" % IOT_MUD_HOME)
    cmd = "/usr/bin/xterm -e \"/usr/bin/python testserver.py -H %s;bash\"" % host
    print cmd
    proc = subprocess.Popen(cmd,shell=True, stdin= subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, close_fds=True)  
    print "test server started"

if __name__ == '__main__':
    setLogLevel( 'info' )
    parser = argparse.ArgumentParser()
    # defaults to the address assigned to my VM
    parser.add_argument("-c",help="Controller host address",default=os.environ.get("CONTROLLER_ADDR"))
    parser.add_argument("-i",help="Host interface to route packets out (the second NATTed interface)",default="eth2")
    parser.add_argument("-r",help="Ryu home (where you have the ryu distro git pulled)", default="/home/odl-developer/host/ryu/")
    #parser.add_argument("-d",help="Public DNS address (check your resolv.conf)",default="192.168.11.1")
    
    parser.add_argument("-d",help="Public DNS address (check your resolv.conf)",default="10.0.4.3")
    parser.add_argument("-t",help="Host only adapter address for test server",default = "192.168.56.102")
    args = parser.parse_args()
    controller_addr = args.c
    dns_address = args.d
    host_addr = args.t
    interface = args.i
    ryu_home = args.r

    cmd = ['sudo','mn','-c']
    proc = subprocess.Popen(cmd,shell=False, stdin= subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    proc.wait()
    
    # Pkill dnsmasq. We will start one up later on h3
    if os.path.exists("/tmp/dnsmasq.pid"):
    	f = open('/tmp/dnsmasq.pid')
    	pid = int(f.readline())
    	try:
    	   os.kill(pid,signal.SIGTERM)
	except:
	   print "Failed to kill dnsmasq check if process is running"


    print("IMPORTANT : append 10.0.0.5 to resolv.conf")

    # start the test server.
    # startTestServer(host_addr)
    # setup our topology

    cmd = ['sudo','pkill','ryu-manager']
    proc = subprocess.Popen(cmd,shell=False, stdin= subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    proc.wait()

    RYU_MANAGER = os.path.abspath(find_executable("ryu-manager"))
    cmd = "/usr/bin/xterm -e \"%s --wsapi-port 9000 --ofp-tcp-listen-port 6673 app/simple_switch_13.py\"" % (RYU_MANAGER)
    #detach the process and shield it from ctrl-c

    proc = subprocess.Popen(cmd,shell=True, cwd=ryu_home + "/ryu", stdin= subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, close_fds=True, preexec_fn=os.setpgrp)

    time.sleep(5)


    headers= {"Content-Type":"application/json"}
    for (configfile,suffix) in {("npenodes.json","nist-network-topology:topology")}:
        print "configfile ",configfile
        data = json.load(open(configfile))
        print "configfile", configfile
        url = "http://" + controller_addr + ":8181/restconf/config/" + suffix
        print "url ", url
        r = requests.put(url, data=json.dumps(data), headers=headers , auth=('admin', 'admin'))
        print "response ", r

    setupTopology(controller_addr,dns_address,interface)

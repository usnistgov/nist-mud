
from mininet.node import OVSController
from mininet.cli import CLI
from mininet.log import setLogLevel
from mininet.net import Mininet
from mininet.node import RemoteController
from mininet.topo import Topo
from mininet.link import Intf
import pdb
import subprocess
import argparse
import os
import sys
import signal
from distutils.spawn import find_executable
from subprocess import call
import time
import requests
import json
from mininet.log import setLogLevel 

global baseMac
global hostCounter

baseMac = 1
hostCounter = 1
nHostsPerCpe = 6

def checkIntf( intf ):
    "Make sure intf exists and is not configured."
    config = quietRun( 'ifconfig %s 2>/dev/null' % intf, shell=True )
    if not config:
        error( 'Error:', intf, 'does not exist!\n' )
        exit( 1 )
    ips = re.findall( r'\d+\.\d+\.\d+\.\d+', config )
    if ips:
        error( 'Error:', intf, 'has an IP address,'
               'and is probably in use!\n' )
    exit( 1 )

def setupNet(controller_addr):
    global net
    global c1
    global npeSwitch

    net = Mininet(controller=RemoteController)

    print "mininet created"

    c1 = net.addController('c1', ip=controller_addr,port=6653)
    npeSwitch = net.addSwitch('s2')
    # Add the interface
    print "Adding hardware interface " + interface + " to switch s1"
    intf = Intf( interface, node=npeSwitch )
    npeSwitch.start([c1])

def assignMacAddresses(hosts):
    global baseMac
    for h in hosts:
       hexStr = format(baseMac,'x').zfill(12)
       macStr = ':'.join([hexStr[i:i+2] for i in range(0, len(hexStr), 2)])
       print "assigning macStr " , macStr
       baseMac = baseMac + 1
   


def setupTopology(controller_addr,interface):
    """ Create and run multiple link network """
    global net
    global c1
    global npeSwitch
    global hostCounter
    
    hosts = []

    for i in range(nHostsPerCpe):
       hosts.append(net.addHost('h'+str(hostCounter)))
       hostCounter = hostCounter + 1

    s1 = net.addSwitch('s1')
    
    for h in hosts:
       s1.linkTo(h)

    assignMacAddresses(hosts)

    s1.linkTo(npeSwitch)
    s1.start([c1])
  
    return hosts


def startNet():
    global c1
    global net
    net.build()
    c1.start()

def assignIpAddresses(hosts, baseip, hn):
    i = 0
    for h in hosts:
        h.cmdPrint('ifconfig h' + str(hn + i ) + '-eth0 10.1.0.' + str(baseip + i) + ' netmask 255.255.255.0')
        i = i + 1
	


def startCli():
    cli = CLI( net )



if __name__ == '__main__':
    setLogLevel( 'info' )
    parser = argparse.ArgumentParser()
    # defaults to the address assigned to my VM
    parser.add_argument("-c",help="Controller host address",default=os.environ.get("CONTROLLER_ADDR"))
    parser.add_argument("-i",help="Host interface to route packets out", default="eth3")
    #parser.add_argument("-i",help="Host interface to route packets out", default="enp1s0f0")
    
    parser.add_argument("-d",help="Public DNS address (check your resolv.conf)",default="10.0.4.3")
    parser.add_argument("-t",help="Host only adapter address for test server",default = "192.168.56.102")
    args = parser.parse_args()
    controller_addr = args.c
    dns_address = args.d
    host_addr = args.t
    interface = args.i
    print "Controller addr ", controller_addr


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

    setupNet(controller_addr)

    cmd = ['sudo','ifconfig', interface, '0']

    hosts  = setupTopology(controller_addr,interface)
    
    startNet()

    assignIpAddresses(hosts,101,1)

    headers= {"Content-Type":"application/json"}
    for (configfile,suffix) in {("cpenodes.json","nist-cpe-nodes:cpe-collections"), 
    				("npenodes.json","nist-network-topology:topology")}:
        data = json.load(open(configfile))
        print "configfile", configfile
        url = "http://" + controller_addr + ":8181/restconf/config/" + suffix
        print "url ", url
        r = requests.put(url, data=json.dumps(data), headers=headers , auth=('admin', 'admin'))
        print "response ", r

    startCli()



from mininet.node import OVSController
from mininet.cli import CLI
from mininet.log import setLogLevel
from mininet.log import info, error
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
import re
from mininet.link import Intf
from mininet.log import setLogLevel 
from mininet.util import quietRun

global baseMac
global hostCounter

baseMac = 1
hostCounter = 1
nHostsPerCpe = 6
switchCounter = 1
cpeSwitches = []
npeSwitches = []
cpeHosts = []
npeSwitchMap = {}



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

def initMininet(controller_addr):
    global net
    global c1

    net = Mininet(controller=RemoteController)
    c1 = net.addController('c1', ip=controller_addr,port=6653)
    print "mininet created"

def setupNet(interface, ofSwitch):
    global net
    global c1
    global npeSwitches
    global npeSwitchMap


    assert ofSwitch.index(":") > 0
   

    npeSwitchName = "s" + ofSwitch[ofSwitch.index(":") + 1:]

    if not npeSwitchName in npeSwitchMap :
        npeSwitch = net.addSwitch(npeSwitchName)
        # Add the interface
        print("Adding hardware interface " + interface + " to switch " + npeSwitchName)
        intf = Intf( interface, node=npeSwitch )
        npeSwitches.append(npeSwitch)
        npeSwitchMap[npeSwitchName] = npeSwitch
        return npeSwitch
    else:
        return npeSwitchMap[npeSwitchName]

def assignMacAddresses(hosts):
    global baseMac
    for h in hosts:
       hexStr = format(baseMac,'x').zfill(12)
       macStr = ':'.join([hexStr[i:i+2] for i in range(0, len(hexStr), 2)])
       print "assigning macStr " , macStr
       baseMac = baseMac + 1

def setupTopology(npeSwitch, ofSwitchName, controller_addr,interface):
    """ Create and run multiple link network """
    global net
    global c1
    global hostCounter
    global cpeSwitches
    
    hosts = []

    for i in range(nHostsPerCpe):
       hosts.append(net.addHost('h'+str(hostCounter)))
       hostCounter = hostCounter + 1

    cpeSwitchName = "s"+ ofSwitchName[ofSwitchName.index(":") + 1:]

    s1 = net.addSwitch(cpeSwitchName)

    cpeSwitches.append(s1)
    
    for h in hosts:
       s1.linkTo(h)

    s1.linkTo(npeSwitch)

    assignMacAddresses(hosts)

    cpeHosts.append(hosts)
  
    return hosts


def startNet():
    global c1
    global net
    net.build()
    c1.start()

    for npeSwitch in npeSwitches:
    	npeSwitch.start([c1])

    for cpeSwitch in cpeSwitches:
        cpeSwitch.start([c1])

    net.start()
    

def assignIpAddresses(prefix, baseip):

    hn = 1
    for hosts in cpeHosts:
        i = 0
        for h in hosts:
            h.cmdPrint('ifconfig h' + str(hn + i ) + '-eth0 ' + prefix  + str(baseip + i) + ' netmask 255.255.255.0')
            i = i + 1
	

def startCli():
    cli = CLI( net )



if __name__ == '__main__':
    setLogLevel( 'info' )
    parser = argparse.ArgumentParser()
    # defaults to the address assigned to my VM
    parser.add_argument("-c",help="SDN Controller host address",default=os.environ.get("CONTROLLER_ADDR"))
    parser.add_argument("-i",help="Host interface to route packets out", default="eth3")
    #parser.add_argument("-i",help="Host interface to route packets out", default="enp1s0f0")
    parser.add_argument("-p",help="prefix", default="10.1.0.")
    args = parser.parse_args()
    controller_addr = args.c
    interface = args.i
    prefix = args.p
    print "Controller addr ", controller_addr
    print "interface ", interface

    checkIntf(interface)
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

    npenodes = json.load(open("npenodes.json"))

    initMininet(controller_addr)
     
    for link in npenodes['topology']['link'] :
        print("link = " + str(link))
        npeSwitch = setupNet(interface, link['npe-switch'])
        setupTopology(npeSwitch, link['cpe-switch'], controller_addr,interface)

    startNet()

    assignIpAddresses("10.1.0.", 101)

    headers= {"Content-Type":"application/json"}

    for (configfile,suffix) in { ("npenodes.json","nist-network-topology:topology")}:
        data = json.load(open(configfile))
        print "configfile", configfile
        url = "http://" + controller_addr + ":8181/restconf/config/" + suffix
        print "url ", url
        r = requests.put(url, data=json.dumps(data), headers=headers , auth=('admin', 'admin'))
        print "response ", r

    startCli()


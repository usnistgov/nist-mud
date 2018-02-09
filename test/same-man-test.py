import requests
import json
import time
import unittest
import sys
import argparse
from socket import *


def udp_client() :
    # Create a UDP socket
    # Notice the use of SOCK_DGRAM for UDP packets
    clientSocket = socket(AF_INET, SOCK_DGRAM)

    # To set waiting time of one second for reponse from server
    clientSocket.settimeout(1)
    # Declare server's socket address
    remoteAddr = ("10.0.0.2", 4000)
    # Ping ten times
    clientSocket.bind(('',4000))
    for i in range(10):
        sendTime = time.time()
        message = 'PING ' + str(i + 1) + " " + str(time.strftime("%H:%M:%S"))
        clientSocket.sendto(message, remoteAddr)
    
        try:
            data, server = clientSocket.recvfrom(1024)
            recdTime = time.time()
            rtt = recdTime - sendTime
            print "Message Received", data
            print "Round Trip Time", rtt
            print
        except timeout:
            print 'REQUEST TIMED OUT'
            print


def udp_server() :

    # Create a UDP socket
    # Notice the use of SOCK_DGRAM for UDP packets
    serverSocket = socket(AF_INET, SOCK_DGRAM)
    # Assign IP address and port number to socket
    serverSocket.bind(('', 4000))

    while True:
        # Receive the client packet along with the address it is coming from
        message, address = serverSocket.recvfrom(1024)
        # Capitalize the message from the client
        message = message.upper()
        serverSocket.sendto(message, address)

def put_request(url,payloadFile):
    f = open(payloadFile)
    payload = json.load(f)
    requests.put(url,auth=('admin','admin'),data=json.dumps(payload), headers={"Content-Type":"application/json"})

def setUp():
    url = "http://localhost:8181/restconf/config/ietf-mud:mud"
    payloadFile = "ietfmud.json"
    put_request(url,payloadFile)

    url = "http://localhost:8181/restconf/config/ietf-access-control-list:access-lists"
    payloadFile = "access-control-list.json"
    put_request(url,payloadFile)

    url = "http://localhost:8181/restconf/config/nist-mud-device-association:mapping"
    payloadFile =  "device-association.json"
    put_request(url,payloadFile)

    time.sleep(10)

    url = "http://localhost:8181/restconf/config/nist-mud-controllerclass-mapping:controllerclass-mapping"
    payloadFile = "controllerclass-mapping.json"
    put_request(url,payloadFile)


if __name__ == "__main__":

    print("start ...")
    parser = argparse.ArgumentParser('argument parser')

    parser.add_argument("--setup", 
			action="store_true",dest='setup', 
                    help='setup flow rules')

    parser.add_argument("--client",  
			action="store_true",dest='client', 
                    help='client send ping')

    parser.add_argument("--server",  
			action="store_true",dest='server', 
                    help='server respond to ping')

    parser.set_defaults(setup=False)
    parser.set_defaults(client=False)
    parser.set_defaults(server=False)

    args  = parser.parse_args()

    print args
    
    count = 0
    
    if args.setup:
        count = count+1

    if args.client:
        count = count+1

    if args.server:
        count = count+1

    if count != 1 :
        print "Need one argument. Specify --client or --server"
        sys.exit()

    if args.setup:
        setUp()
    elif args.client:
        udp_client()
    elif args.server:
        udp_server()

    
       



 
    


import requests
import json
import time
import unittest
import sys
import argparse
from socket import *


def udp_client(host, port) :
    # Create a UDP socket
    # Notice the use of SOCK_DGRAM for UDP packets
    clientSocket = socket(AF_INET, SOCK_DGRAM)

    # To set waiting time of one second for reponse from server
    clientSocket.settimeout(1)
    # Declare server's socket address
    remoteAddr = (host, port)
    # Ping ten times
    clientSocket.bind(('',port))
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
            print 'ERROR : REQUEST TIMED OUT'
            print


def udp_server(port) :

    # Create a UDP socket
    # Notice the use of SOCK_DGRAM for UDP packets
    serverSocket = socket(AF_INET, SOCK_DGRAM)
    # Assign IP address and port number to socket
    serverSocket.bind(('', port))

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



if __name__ == "__main__":

    print("start ...")
    parser = argparse.ArgumentParser('argument parser')

    parser.add_argument("--port", 
			type=int, default=None,
            required=True,
                    help='listening port')

    parser.add_argument("--host", default=None,
                    help='server host (required if Client flag is True)')
        

    parser.add_argument("--client",  
			action="store_true",dest='client', 
                    help='client send ping')

    parser.add_argument("--server",  
			action="store_true",dest='server', 
                    help='server respond to ping')

    parser.set_defaults(client=False)
    parser.set_defaults(server=False)

    args  = parser.parse_args()

    print args

    port = args.port
    host = args.host
    
    
    count = 0
    
    if args.client:
        count = count+1

    if args.server:
        count = count+1

    if count != 1 :
        print "Need one argument. Specify --client or --server"
        sys.exit()

    if args.client:
        if host is None:
           print("Missing a required argument --host")
           sys.exit()
        udp_client(host,port)
    elif args.server:
        udp_server(port)

    

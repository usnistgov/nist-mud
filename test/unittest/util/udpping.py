import requests
import json
import time
import unittest
import sys
import argparse
import os
import socket
import struct
import random

global timeoutCount

global quiet

global timeout


def udp_client(host, port, npings) :
    # Create a UDP socket
    # Notice the use of SOCK_DGRAM for UDP packets
    global timeoutCount
    global quiet
    timeoutCount = 0
    clientSocket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

    # To set waiting time of one second for reponse from server
    clientSocket.settimeout(1)
    clientSocket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
    clientSocket.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, struct.pack('ii', 1, 0))
    # Declare server's socket address
    remoteAddr = (host, port)
    # Ping ten times
    #clientSocket.bind(('',port))

    lambd = (1.0 / 0.5)
    for i in range(npings):
        sendTime = time.time()
        message = 'PING ' + str(i + 1) + " " + str(time.strftime("%H:%M:%S"))
        if not quiet:
           print(message)           
        clientSocket.sendto(message, remoteAddr)
    
        try:
            data, server = clientSocket.recvfrom(1024)
            time.sleep(random.expovariate(lambd))
            recdTime = time.time()
            rtt = recdTime - sendTime
            if not quiet:
                print ( "RTT = " + str(rtt) )
        except:
            if not quiet:
                print ( "UDPPING FAILED " )
            timeoutCount = timeoutCount + 1

    clientSocket.close()


def udp_server(port,npings) :

    global timeout

    # Create a UDP socket
    # Notice the use of SOCK_DGRAM for UDP packets
    serverSocket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    serverSocket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
    serverSocket.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, struct.pack('ii', 1, 0))
    # Assign IP address and port number to socket
    serverSocket.bind(('', port))

    if timeout:
        print "Setting timeout "
        serverSocket.settimeout(2)

    i = 0

    try:
        while True:
            # Receive the client packet along with the address it is coming from
            message, address = serverSocket.recvfrom(1024)
            serverSocket.sendto(message, address)
            if i >= npings-1 and timeout:
                break
            else:
                i = 1 + 1
    finally:
        serverSocket.close()
            
       

def put_request(url,payloadFile):
    f = open(payloadFile)
    payload = json.load(f)
    requests.put(url,auth=('admin','admin'),data=json.dumps(payload), headers={"Content-Type":"application/json"})



if __name__ == "__main__":

    print("start ...")
    global timeoutCount
    global quiet
    global timeout

    parser = argparse.ArgumentParser('argument parser')
    parser.add_argument("--quiet", 
            action="store_true",dest='quiet', 
                    help='client send ping')

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

    parser.add_argument("--timeout",
            action="store_true",dest='timeout', 
                    help='enable server timeout')

    parser.add_argument("--npings",
                type=int, default=10, required=False, 
                help="number of pings")

    parser.set_defaults(client=False)
    parser.set_defaults(server=False)
    parser.set_defaults(quiet=False)
    parser.set_defaults(timeout=False)

    args  = parser.parse_args()

    print args

    port = args.port
    host = args.host
    quiet = args.quiet
    timeout = args.timeout
    npings = args.npings
    
    
    count = 0
    
    if args.client:
        count = count+1

    if args.server:
        count = count+1

    if count != 1 :
        print "Need one argument. Specify --client or --server"
        sys.exit()

    pid = os.getpid()
    if args.client:

        if host is None:
           print("Missing a required argument --host")
           sys.exit()

        with open("/tmp/udpclient.pid",'w') as f:
           f.write(str(pid))
        udp_client(host,port,npings)

        print "[rc=" + str(npings  - timeoutCount ) + "]"
    elif args.server:
        with open("/tmp/udpserver.pid",'w') as f:
           f.write(str(pid))
        udp_server(port,npings)
        sys.exit( 0 )



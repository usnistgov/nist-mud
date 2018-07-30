import socket
import argparse
import os
import time

BUFFER_SIZE = 1024
MESSAGE = "Hello, World!" 

for i in range(1,1000):
   MESSAGE=MESSAGE+"Hello world"

BUFFER_SIZE = len(MESSAGE) + 1

if __name__=="__main__" :
    
    with open("/tmp/tcpclient.pid","w") as f:
        f.write(str(os.getpid()))

    parser = argparse.ArgumentParser('argument parser')
    parser.add_argument("-H", help="Host address", default="127.0.0.1")
    parser.add_argument("-P", type=int, help="Port", default=4000)
    parser.add_argument("-B", action="store_true",dest='bind', help="bind client sock")
    parser.set_defaults(bind=False)
    args = parser.parse_args()
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

    if args.bind:
      s.bind(("",args.P))

    try:
        s.settimeout(5)
        try:
    	    s.connect((args.H, args.P))
        except:
	    time.sleep(5)
            print("Failed to connect -- retrying")
            # Try connect again to make sure no race conditions.
    	    s.connect((args.H, args.P))
        s.send(MESSAGE)
        data = s.recv(BUFFER_SIZE)
        print "OK"
    finally:
        s.close()

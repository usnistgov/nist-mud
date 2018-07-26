import socket
import argparse
import sys
import os
import time

BUFFER_SIZE = 10*1024  # Normally 1024, but we set to 20 --  we want fast response
MESSAGE = "Hello, World!" 

for i in range(1,100000):
   MESSAGE=MESSAGE+"Hello world"

BUFFER_SIZE = len(MESSAGE) + 1

if __name__=="__main__":

    with open("/tmp/tcpserver.py","w") as f:
        f.write(str(os.getpid()))

    parser = argparse.ArgumentParser()
    parser.add_argument("-H", help="Host address", default="127.0.0.1")
    parser.add_argument("-P", help="Port", default="4000")
    parser.add_argument("-T", help="Timeout ", default = "10")
    parser.add_argument("-C", dest="c_flag", help="Continue after one shot", default=False, action='store_true')
    args = parser.parse_args()
    host = args.H
    port = int(args.P)
    timeout = int(args.T)
    c_flag = args.c_flag

    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.bind((host, port))
    s.listen(1)
    if not c_flag: 
        s.settimeout(timeout)

    try:
        while True:
            conn, addr = s.accept()
            data = conn.recv(BUFFER_SIZE)
            if not data: 
                conn.send(data)  # echo
            conn.close()
            if not c_flag:
                break
    finally:
        s.close()
        sys.exit()

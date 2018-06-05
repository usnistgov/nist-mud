import socket
import argparse
import os

BUFFER_SIZE = 1024
MESSAGE = "Hello, World!"
if __name__=="__main__" :

    with open("/tmp/tcpclient.pid","w") as f:
        f.write(str(os.getpid()))

    parser = argparse.ArgumentParser('argument parser')
    parser.add_argument("-H", help="Host address", default="127.0.0.1")
    parser.add_argument("-P", help="Port", default="4000")
    args = parser.parse_args()
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(5)
    s.connect((args.H, int(args.P)))
    s.send(MESSAGE)
    data = s.recv(BUFFER_SIZE)
    s.close()
    print "OK"

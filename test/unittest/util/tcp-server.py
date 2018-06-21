import socket
import argparse
import sys
import os

BUFFER_SIZE = 20  # Normally 1024, but we want fast response

if __name__=="__main__":

    with open("/tmp/tcpserver.py","w") as f:
        f.write(str(os.getpid()))

    parser = argparse.ArgumentParser()
    parser.add_argument("-H", help="Host address", default="127.0.0.1")
    parser.add_argument("-P", help="Port", default="4000")
    parser.add_argument("-T", help="Timeout ", default = "10")
    parser.add_argument("-C", dest="c_flag", help="Continue after one shot", default=False, action='store_true')
    args = parser.parse_args()
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    host = args.H
    port = int(args.P)
    timeout = int(args.T)
    c_flag = args.c_flag
    s.bind((host, port))
    s.listen(1)

    if not c_flag: 
        s.settimeout(timeout)

    while True:
        conn, addr = s.accept()
        data = conn.recv(BUFFER_SIZE)
        if not data: 
            conn.send(data)  # echo
        conn.close()
        if not c_flag:
	    s.close()
            sys.exit()

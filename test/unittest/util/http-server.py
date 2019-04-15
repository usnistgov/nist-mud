import SimpleHTTPServer
import SocketServer
import argparse


if __name__ == "__main__":

    parser = argparse.ArgumentParser()
    parser.add_argument("-H", help="Host address", default="")
    parser.add_argument("-P", help="Port ", default="80")
    args = parser.parse_args()
    hostAddr = args.H
    PORT = int(args.P)
    Handler = SimpleHTTPServer.SimpleHTTPRequestHandler
    httpd = SocketServer.TCPServer((hostAddr, PORT), Handler)
    print "serving at port", PORT
    httpd.serve_forever()

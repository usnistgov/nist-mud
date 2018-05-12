import SimpleHTTPServer
import SocketServer
import argparse

PORT = 80

if __name__ == "__main__":

    parser = argparse.ArgumentParser()
    parser.add_argument("-H", help="Host address", default="")
    args = parser.parse_args()
    hostAddr = args.H
    Handler = SimpleHTTPServer.SimpleHTTPRequestHandler
    httpd = SocketServer.TCPServer((hostAddr, PORT), Handler)
    print "serving at port", PORT
    httpd.serve_forever()

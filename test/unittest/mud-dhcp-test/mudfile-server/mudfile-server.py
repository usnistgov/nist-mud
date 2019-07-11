import BaseHTTPServer, SimpleHTTPServer
import ssl
import urlparse

class MyHTTPRequestHandler(SimpleHTTPServer.SimpleHTTPRequestHandler):

    def do_GET(self):
        print ("DoGET " + self.path)
        self.send_response(200)
        self.end_headers()
        if self.path == "/super1" :
           with  open("mudfile-dhcptest.json") as f:
                data = f.read()
                self.wfile.write(data)
        elif self.path == "/super1/mudfile-dhcptest.p7s":
           with open("mudfile-dhcptest.p7s") as f:
                data = f.read()
                self.wfile.write(data)
        else:
           self.wfile.write(b'Hello, world!')

print "Starting mudfile server on 127.0.0.1"
httpd = BaseHTTPServer.HTTPServer(('127.0.0.1', 443), MyHTTPRequestHandler)
httpd.socket = ssl.wrap_socket (httpd.socket, keyfile='./mudsigner.key',  certfile='./mudsigner.crt', server_side=True)
httpd.serve_forever()
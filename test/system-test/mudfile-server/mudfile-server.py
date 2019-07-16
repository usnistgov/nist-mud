import BaseHTTPServer, SimpleHTTPServer
import ssl
import urlparse
# Dummy manufacturer server for testing

class MyHTTPRequestHandler(SimpleHTTPServer.SimpleHTTPRequestHandler):

    def do_GET(self):
        print ("DoGET " + self.path)
        self.send_response(200)
        if self.path == "/nistmud1" :
           with  open("mudfile-sensor.json", mode="r") as f:
                data = f.read()
		print("Read " + str(len(data)) + " chars ")
                self.send_header("Content-Length", len(data))
                self.end_headers()
                self.wfile.write(data)
        elif self.path == "/nistmud2" :
           with  open("mudfile-otherman.json", mode="r") as f:
                data = f.read()
		print("Read " + str(len(data)) + " chars ")
                self.send_header("Content-Length", len(data))
                self.end_headers()
                self.wfile.write(data)
        elif self.path == "/nistmud1/mudfile-sensor.p7s":
           with open("mudfile-sensor.p7s",mode="r") as f:
                data = f.read()
		print("Read " + str(len(data)) + " chars ")
                self.send_header("Content-Length", len(data))
                self.end_headers()
                self.wfile.write(data)
        elif self.path == "/nistmud2/mudfile-otherman.p7s":
           with open("mudfile-otherman.p7s",mode="r") as f:
                data = f.read()
		print("Read " + str(len(data)) + " chars ")
                self.send_header("Content-Length", len(data))
                self.end_headers()
                self.wfile.write(data)
        else:
           print("UNKNOWN URL!!")
           self.wfile.write(b'Hello, world!')

httpd = BaseHTTPServer.HTTPServer(('0.0.0.0', 443), MyHTTPRequestHandler)
httpd.socket = ssl.wrap_socket (httpd.socket, keyfile='./mudsigner.key',  certfile='./mudsigner.crt', server_side=True)
httpd.serve_forever()

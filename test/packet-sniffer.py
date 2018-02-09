import socket
import struct
import sys
import time
from struct import *
from socket import *

time.sleep(30)
sock = socket(AF_INET, SOCK_DGRAM)
sock.setsockopt(SOL_SOCKET, SO_REUSEADDR, 1)
sock.setsockopt(SOL_SOCKET, SO_BROADCAST, 1)
sock.bind(('10.0.0.8',1000))
sock.sendto('IDS encrypted secret data', ('255.255.255.255', 1000))
print "Sent broadcast"

try:
    s = socket(PF_PACKET, SOCK_RAW, htons(0x800))
    s.settimeout(10)
except:
    print "ERROR CREATING SOCKET"
    sys.exit()
 
# receive a packet
f = open('packets.txt','w')
while True:
    try:
        pkt = s.recv(65565)
        if(len(pkt)) <= 34:
            print "Too short " + str(len(pkt))
            continue
    except timeout:
        print "timeout"
        sock.sendto('IDS encrypted secret data', ('255.255.255.255', 1000))
        print "Sent broadcast"
        continue

    ethHeader = pkt[0:14]
    ip_header = pkt[14:34]
    tcpHeader = pkt[34:38]
     
    #now unpack them :)
    iph = unpack('!BBHHHBBH4s4s' , ip_header)
     
    version_ihl = iph[0]
    version = version_ihl >> 4
    ihl = version_ihl & 0xF
     
    iph_length = ihl * 4
     
    ttl = iph[5]
    protocol = iph[6]
    s_addr = inet_ntoa(iph[8]);
    d_addr = inet_ntoa(iph[9]);
    output = 'Version : ' + str(version) + ' IP Header Length : ' + str(ihl) + ' TTL : ' + str(ttl) + ' Protocol : ' + str(protocol) + ' Source Address : ' + str(s_addr) + ' Destination Address : ' + str(d_addr)
    print output
    f.write(output + "\n")
    f.flush()

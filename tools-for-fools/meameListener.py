import socket
import struct

print "yo"

s = socket.socket()
host = "129.241.110.224"
port = 12350

print "connecting"
s.connect((host, port))
onez = '\x01'
s.send(onez)

msg = s.recv(256)
while (msg):
    print(msg)
    msg = s.recv(256)

print "done, closing stuff"
s.close()
print "OK"

import socket
import struct

s = socket.socket()
host = "129.241.201.110"
port = 8899

print "connecting"
s.connect((host, port))

msg = s.recv(256)
while (msg):
    b = bytearray()
    b.extend(msg)
    print (int)(b[0])
    print (int)(b[1])
    print (int)(b[2])
    print (int)(b[3])

    # fug = struct.unpack("I", [b[0], b[1], b[2], b[3]])
    # print fug

    # print msg
    # for i in range(0, 64):
    #     dude = 0
    #     for j in range(0, 4):
    #         dude += msg(i*4 + j) << j*8
    #     print "[%(dude)]"

    print reduce(lambda s, x: s*256 + x, bytearray(msg))
    msg = s.recv(256)

print "done, closing stuff"
s.close()
print "OK"

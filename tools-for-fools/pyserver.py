import socket
import pickle

s = socket.socket()
host = "129.241.111.251"
port = 1256

print "hosting on"
print host
print port

s.bind((host, port))
s.listen(5)

while True:
    try:
        c, addr = s.accept()

        ones   = ['\x01' for x in range(0, 64)]
        onez = ''
        for b in ones:
            onez += b
            onez += '\x00\x00\x00'

        twos   = ['\x02' for x in range(0, 64)]
        twoz = ''
        for b in twos:
            twoz += b
            twoz += '\x00\x00\x00'

        threes   = ['\x03' for x in range(0, 64)]
        threez = ''
        for b in threes:
            threez += b
            threez += '\x00\x00\x00'

        fours   = ['\x04' for x in range(0, 64)]
        fourz = ''
        for b in fours:
            fourz += b
            fourz += '\x00\x00\x00'

        # for ii in range(0, 128*5*60):
        # for ii in range(0, 64):
        for jj in range(0, 256):
            for ii in range(0, 64*64*64*128):
                c.send(onez)
                c.send(onez)
                c.send(threez)
                c.send(fourz)


                c.send(threez)
                c.send(threez)
                c.send(threez)
                c.send(fourz)


                c.send(onez)
                c.send(onez)
                c.send(onez)
                c.send(onez)
                c.send(onez)
                c.send(onez)
                c.send(onez)
                c.send(onez)

        print "freedom delivered"
        meme = c.recv(256)
        print "we retrieved"
        print meme
        meme = c.recv(256)
        print "we retrieved"
        print meme
        meme = c.recv(256)
        print "we retrieved"
        print meme
        meme = c.recv(256)
        print "we retrieved"
        print meme
        c.close()
    finally:
        c.close()
        s.close()
    c.close()
    s.close()

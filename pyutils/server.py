from websocket_server import WebsocketServer
import thread
import time
import array
import socket
import struct

direction = [0.0, 0.0, 0.0]

def memeRelay():
  global direction
  s = socket.socket()
  host = "129.241.111.251"
  port = 9898

  s.bind((host, port))
  s.listen(5)

  while True:
    try:
      c, addr = s.accept()
      while True:
        msg1 = c.recv(256)
        msg2 = c.recv(256)
        msg3 = c.recv(256)

        # value = struct.unpack('f', msg)[0]

        first = array.array('d', msg1)[0]
        second = array.array('d', msg2)[0]
        third = array.array('d', msg3)[0]

        vals = [first, second, third]

        smallest = vals.index(min(vals))

        if(smallest == 0):
          direction[0] = second
          direction[1] = third
          direction[2] = first
        elif(smallest == 1):
          direction[0] = third
          direction[1] = first
          direction[2] = second
        else:
          direction[0] = first
          direction[1] = second
          direction[2] = third

        # print("\n\n----")
        # print(first)
        # print(second)
        # print(third)
        # print("----")

        # direction[0] = first
        # direction[1] = second
        # direction[2] = third

    finally:
      s.close()


thread.start_new_thread(memeRelay, ())


def new_client(client, server):
  print("New client with id %d" % client['id'])
  global direction
  server.send_message_to_all(str(direction))

def client_left(client, server):
  print("client(%d) disconnected" % client['id'])

def message_received(client, server, message):
  # print("gotted messeg, sended:")
  # print(str(direction))
  server.send_message_to_all(str(direction))

PORT=9897
server = WebsocketServer(PORT)
server.set_fn_new_client(new_client)
server.set_fn_client_left(client_left)
server.set_fn_message_received(message_received)
server.run_forever()

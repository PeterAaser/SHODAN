from websocket_server import WebsocketServer
import thread
import time
import array
import socket

direction = [0.0, 0.0]

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
        msg = c.recv(256)
        doubles_seq = array.array('d', msg)
        print doubles_seq
        direction = doubles_seq

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
  server.send_message_to_all(str(direction))

PORT=9897
server = WebsocketServer(PORT)
server.set_fn_new_client(new_client)
server.set_fn_client_left(client_left)
server.set_fn_message_received(message_received)
server.run_forever()

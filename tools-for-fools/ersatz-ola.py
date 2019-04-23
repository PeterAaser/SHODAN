import serial
import time

rawdata=[]
count=0

arduino = serial.Serial("/dev/ttyUSB3", timeout=1, baudrate=115200)

message = "Hey paul!"

# buffer = [chr(i) for i in [1, 2, 3]]

while 1:
    print("writing 1")
    arduino.write(chr(1).encode())
    print("sleeping")
    time.sleep(10)
    print("reading")
    print(str(arduino.read()))

    print("writing 2")
    arduino.write(chr(2).encode())
    print("sleeping")
    time.sleep(10)
    print("reading")
    print(str(arduino.read()))

    print("writing 3")
    arduino.write(chr(3).encode())
    print("sleeping")
    time.sleep(10)
    print("reading")
    print(str(arduino.read()))

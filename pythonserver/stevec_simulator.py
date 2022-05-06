import random
import time

def simulate(func):
    print("started Test MQTT")
    while True:
        #logika za simuliranje števca
        x = random.gauss(800,15)
        time.sleep(10)
        func(x)
        



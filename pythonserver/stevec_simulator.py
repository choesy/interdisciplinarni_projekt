import random
import time

def simulate(func):
    print("started Test MQTT")
    while True:
        #logika za simuliranje števca
        x = random.gauss(40,10)
        time.sleep(1)
        func(x)
        



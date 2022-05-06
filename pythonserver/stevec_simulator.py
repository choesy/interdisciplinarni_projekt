import random
import time

def simulate(func):
    print("started Test MQTT")
    while True:
        #logika za simuliranje števca
        x = random.gauss(1700,20)
        time.sleep(10)
        func(x)
        



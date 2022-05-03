# python3.6
from datetime import datetime
from re import sub
import struct
from threading import Event, Thread
from flask import Flask, request
from paho.mqtt import client as mqtt_client
from stevec_simulator import simulate
import json
from dataclasses import dataclass
import sys
app = Flask(__name__)

broker = 'rlab.lucami.org'
port = 1883
topic = "eCheck/powerMeterP1"
# generate client ID with pub prefix randomly
client_id = "test_4"
username = 'lucmqtt'
password = 'lucami2021'
test=1

MAX_VALUES_LENGTH=60
@dataclass
class User:
    calculating:bool
    starttime:int
    endtime:int
    device: str
    totalConsumption:float=0
    loudness:int=0

users={}
stevec_values={}
def connect_mqtt() -> mqtt_client:
    def on_connect(client, userdata, flags, rc):
        if rc == 0:
            print("Connected to MQTT Broker!")
        else:
            print("Failed to connect, return code %d\n", rc)

    client = mqtt_client.Client(client_id)
    client.username_pw_set(username, password)
    client.on_connect = on_connect
    client.connect(broker, port)
    return client


def on_test_message(msg):
        #print(f"Received `{msg.payload.decode()}` from `{msg.topic}` topic")
        global stevec_values
        print(msg)
        time=int(datetime.now().timestamp())
        mintime=int(datetime.now().timestamp())
        if len(stevec_values)>=MAX_VALUES_LENGTH:
            for key,value in stevec_values.items():
                if int(float(key))<mintime:
                    mintime=int(key)
            del stevec_values[str(mintime)]
        stevec_values[str(time)]=int(float(msg))

def subscribe(client: mqtt_client):
    def on_message(client, userdata, msg):
        #print(f"Received `{msg.payload.decode()}` from `{msg.topic}` topic")
        global stevec_values
        msg_value=json.loads(msg.payload.decode())
        print(msg_value["actual_consumption"])
        time=int(datetime.now().timestamp())
        mintime=int(datetime.now().timestamp())
        if len(stevec_values)>=MAX_VALUES_LENGTH:
            for key,value in stevec_values.items():
                if int(float(key))<mintime:
                    mintime=int(key)
            del stevec_values[str(mintime)]
        stevec_values[str(time)]=int(float(msg_value["actual_consumption"]))




    client.subscribe(topic)
    client.on_message = on_message

def runmqtt():
    client = connect_mqtt()
    subscribe(client)
    client.loop_start()
    

def runserver():
    app.run(host='0.0.0.0', port=8080
    )

start_time = 0
end_time = 0


def calculateConsumption(user_id):
    global stevec_values,users
    start_time=users[user_id].starttime
    end_time=users[user_id].endtime
    loudness=users[user_id].loudness
    usage=0
    for key,val in stevec_values.items():
        if int(float(key))<=end_time and int(float(key))>=start_time:
            usage+=val
    usage=usage/360
    return  usage



@app.route('/',methods=['POST'])
def measurement():
    global users
    print("hello")
    request_json = request.get_json()
    # look for how to get data
    user_id = request_json.get('userID')
    start = request_json.get('start')
    device = request_json.get('device')
    time = request_json.get('time')
    print(user_id, start, device, time)
    loudness=0
    datatosend=""
    if start:
        if user_id in users:
            if users[user_id].calculating:
                datatosend = json.dumps({"status": "already calculating" })
                users[user_id].loudness=loudness
                users[user_id].device=device
            else:
                users[user_id].calculating=True
                users[user_id].loudness=loudness
                users[user_id].device=device
                users[user_id].starttime=int(datetime.now().timestamp())
                users[user_id].endtime=None
                datatosend = json.dumps({"measurment": 1 })
        else:
            datatosend = json.dumps({"measurment": 1 })
            users[user_id]=User(calculating=True,starttime=int(datetime.now().timestamp()),endtime=None,loudness=loudness,device=device)
    else:
        if user_id in users:
                if users[user_id].calculating:
                    users[user_id].calculating=False
                    users[user_id].device=device
                    users[user_id].currentLoudness=0
                    users[user_id].endtime=int(datetime.now().timestamp())
                    consumed=calculateConsumption(user_id)
                    users[user_id].totalConsumption+=consumed
                    datatosend=json.dumps({"calculated": consumed })
                else:
                    datatosend = json.dumps({"calculated": "you need to start initialise calculating first" })
        else:
            datatosend = json.dumps({"calculated": "you need to start initialise calculating first" })
    print(users)
    return datatosend


if __name__ == '__main__':
    if test:
        thread = Thread(target=simulate,args=(on_test_message,))
        thread.daemon = True
        thread.start()
    else:
        runmqtt()
    runserver()

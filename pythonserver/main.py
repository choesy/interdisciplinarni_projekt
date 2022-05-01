# python3.6
from datetime import datetime
from re import sub
from threading import Event, Thread
from flask import Flask, request
from paho.mqtt import client as mqtt_client
from stevec_simulator import simulate
import json
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

l = [] #list of values
start_measure = Event()
values = []

def on_test_message(msg):
        #print(f"Received `{msg.payload.decode()}` from `{msg.topic}` topic")
        global l, values
        x = {
            "value":int(msg) ,
            "time": datetime.now().timestamp()
        }
        #save samples to array of length of 10 min ?
        print(x["value"])
        l.append(x)   #add measurement to list
        values.append(x)
        if len(values) > 3:
            values = values[-3:]
        if start_measure.is_set():
            values.append(l[-1])
            print("adding to values")
        l = l[-30:]    #redefine list as last 5 min


def subscribe(client: mqtt_client):
    def on_message(client, userdata, msg):
        #print(f"Received `{msg.payload.decode()}` from `{msg.topic}` topic")
        global l, values
        value=json.loads(msg.payload.decode())
        x = {
            "value":int(value["actual_consumption"]) ,
            "time": datetime.now().timestamp()
        }
        #save samples to array of length of 10 min ?
        print(x["value"])
        l.append(x)   #add measurement to list
        values.append(x)
        if len(values) > 3:
            values = values[-3:]
        if start_measure.is_set():
            values.append(l[-1])
            print("adding to values")
        l = l[-30:]    #redefine list as last 5 min



    client.subscribe(topic)
    client.on_message = on_message

def runmqtt():
    client = connect_mqtt()
    subscribe(client)
    client.loop_start()
    

def runserver():
    app.run(host='0.0.0.0', port=8080)

start_time = 0
end_time = 0

def collectingSamples(userID, time, device, start):
    #start collecting samples, gets called with api call, last minute and counting untill another api call, alghoritm for recognizing
    # max value and min
    print("collecting samples")
    global start_time, end_time
    userConsumption = 0
    if start:
        start_time = time
        if not start_measure.is_set(): #start =1, end = 0
            start_measure.set()
        return json.dumps({"measuring": 1 })
    else:
        end_time=time
        start_measure.clear()
        print(values)
        print(len(values))
        sum = calculateConsumption(values,start_time, end_time)
        return json.dumps({"calculated": sum })

def calculateConsumption(values, start_time, end_time):
    sum_consumption = 0
    measurements_list = [measure["value"] for measure in values]

    min_measure = min(measurements_list)
    print(min_measure)
    for measurement1 in values:
        if measurement1["time"] > start_time:
            if measurement1["time"] < end_time:
                print("is bigger than " + str(start_time))
                sum_consumption += 1/360 * (measurement1["value"] - min_measure)
    return sum_consumption



@app.route('/',methods=['POST'])
def measurement():
    print("hello")
    request_json = request.get_json()
    # look for how to get data
    user_id = request_json.get('userID')
    start = request_json.get('start')
    device = request_json.get('device')
    time = request_json.get('time')
    print(user_id, start, device, time)

    return collectingSamples(user_id,time,device,start)


if __name__ == '__main__':
    if test:
        thread = Thread(target=simulate,args=(on_test_message,))
        thread.daemon = True
        thread.start()
    else:
        runmqtt()
    runserver()

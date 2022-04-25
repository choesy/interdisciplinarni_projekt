# python3.6
from datetime import datetime

from flask import Flask
from paho.mqtt import client as mqtt_client

app = Flask(__name__)

broker = 'rlab.lucami.org'
port = 1883
topic = "sensors/power/p1meter/actual_consumption"
# generate client ID with pub prefix randomly
client_id = "test_4"
username = 'lucmqtt'
password = 'lucami2021'


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
def subscribe(client: mqtt_client):
    def on_message(client, userdata, msg):
        print(f"Received `{msg.payload.decode()}` from `{msg.topic}` topic")
        #x = {
        #    'value': msg,
        #   'time': datetime.now().timestamp()
        #}
        x = msg
        #save samples to array of length of 10 min ? 
        l.append(x)   #add measurement to list                     
        l = l[-6:]    #redefine list as last 5 items 



    client.subscribe(topic)
    client.on_message = on_message


def run():
    client = connect_mqtt()
    subscribe(client)
    client.loop_start()
    app.run(host='0.0.0.0', port=8080)

 measure = 0
 values = []
def collectingSamples(userID, time, device, startEnd):
    #start collecting samples, gets called with api call, last minute and counting untill another api call, alghoritm for recognizing 
    # max value and min 
    console.log("measuring")
    userConsumption = 0
    if startEnd: #start =1, end = 0
        measure = 1
        values.extend(l)
    else:
        measure = 0
    while measure:
        values.append(l[-1])
    min = min(values)
    
    userConsumption = 
    return userConsumption

@app.route('/')
def measurement(data):
    cons = collectingSamples(data['userID'], data['time'], data['device'], data['startEnd'])
    return cons


if __name__ == '__main__':
    run()

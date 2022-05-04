
from datetime import datetime
from re import sub
from threading import Event, Thread
from flask import Flask, request
from paho.mqtt import client as mqtt_client
from stevec_simulator import simulate
import json
import sys
from flask_sqlalchemy import SQLAlchemy

app = Flask(__name__)
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///site.db'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
db = SQLAlchemy(app)
broker = 'rlab.lucami.org'
port = 1883
topic = "eCheck/powerMeterP1"
# generate client ID with pub prefix randomly
client_id = "test_4"
username = 'lucmqtt'
password = 'lucami2021'
test=0

MAX_VALUES_LENGTH=60
class users(db.Model):
    id = db.Column(db.String(10), primary_key = True)
    calculating=db.Column(db.Boolean)
    starttime=db.Column(db.Integer)
    endtime=db.Column(db.Integer)
    device=db.Column(db.String(10))
    totalConsumption=db.Column(db.Integer)
    loudness=db.Column(db.Integer)
    def __repr__(self):
        return f"users('{self.id}','{self.calculating}','{self.starttime}','{self.endtime}','{self.loudness}','{self.device}','{self.totalConsumption}')"

    # def __init__(self, id,calculating,starttime,endtime,device,totalConsumption,loudness):
    #     self.id = id
    #     self.calculating = calculating
    #     self.starttime = starttime
    #     self.endtime = endtime
    #     self.device = device
    #     self.totalConsumption = totalConsumption
    #     self.loudness = loudness

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
    db.create_all()
    app.run(host='0.0.0.0', port=8080)

start_time = 0
end_time = 0


def calculateConsumption(user_id):
    global stevec_values
    user= users.query.filter_by(id=user_id).first()
    start_time=user.starttime
    end_time=user.endtime
    otheruser=users.query.filter(users.id!=user_id,users.calculating==True,users.device==user.device,users.loudness>user.loudness).first()
    print(otheruser)
    if otheruser !=None:
        return 0
    usage=0
    for key,val in stevec_values.items():
        if int(float(key))<=end_time and int(float(key))>=start_time:
            usage+=val
    usage=usage/360
    return usage



@app.route('/',methods=['POST'])
def measurement():
    print("hello")
    request_json = request.get_json()
    # look for how to get data
    user_id = request_json.get('userID')#string
    start = request_json.get('start')#boool
    device = request_json.get('device')#string
    loudness=request_json.get('loudness')#int
    print(user_id, start, device,loudness)
    datatosend=""

    user= users.query.get(user_id)
    print("USER: ", user)
    if start:
        if user:
            if user.calculating:
                datatosend = json.dumps({"measurment": 0,"calculation":0 })
                user.loudness=loudness
                user.device=device
            else:
                user.calculating=True
                user.loudness=loudness
                user.device=device
                user.starttime=int(datetime.now().timestamp())
                user.endtime=0
                datatosend = json.dumps({"measurment": 1,"calculation":0})
        else:
            datatosend = json.dumps({"measurment": 1 ,"calculation":0})
            user=users(calculating=True,id=user_id,starttime=int(datetime.now().timestamp()),endtime=0,loudness=loudness,device=device,totalConsumption=0)
            db.session.add(user)   
    else:
        if user:
            if user.calculating:
                user.calculating=False
                user.device=device
                user.currentLoudness=0
                user.endtime=int(datetime.now().timestamp())
                consumed=calculateConsumption(user_id)
                user.totalConsumption+=consumed
                datatosend=json.dumps({"measurment":0,"calculation": consumed })
            else:
                datatosend = json.dumps({"measurment": 2 ,"calculation":0})
        else:
            datatosend = json.dumps({"measurment":2,"calculation": 0 })
    db.session.commit()
    return datatosend


if __name__ == '__main__':
    if test:
        thread = Thread(target=simulate,args=(on_test_message,))
        thread.daemon = True
        thread.start()
    else:
        runmqtt()
    runserver()

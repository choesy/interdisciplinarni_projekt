
from datetime import datetime
from re import sub
from threading import Event, Thread
from flask import Flask, request, send_from_directory
from paho.mqtt import client as mqtt_client
from stevec_simulator import simulate
import json
import sys
from flask_sqlalchemy import SQLAlchemy
import random

app = Flask(__name__)
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///site.db'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
db = SQLAlchemy(app)
broker = 'rlab.lucami.org'
port = 1883
topic = "eCheck/powerMeterP1"
# generate client ID with pub prefix randomly
client_id = hex(random.getrandbits(128))
username = 'lucmqtt'
password = 'lucami2021'
test=0
print(sys.argv)
if len(sys.argv)>1:
    test=1

MAX_VALUES_LENGTH=60
class users(db.Model):
    id = db.Column(db.String(10), primary_key = True)
    calculating=db.Column(db.Boolean)
    starttime=db.Column(db.Integer)
    endtime=db.Column(db.Integer)
    device=db.Column(db.String(10))
    totalConsumption=db.Column(db.Float)
    loudness=db.Column(db.Integer)
    def __repr__(self):
        return f"users('{self.id}','{self.calculating}','{self.starttime}','{self.endtime}','{self.loudness}','{self.device}','{self.totalConsumption}')"

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
        calculateConsumption()

def subscribe(client: mqtt_client):
    def on_message(client, userdata, msg):
        global stevec_values
        msg_value=json.loads(msg.payload.decode())
        #print(msg_value["actual_consumption"])
        time=int(datetime.now().timestamp())
        mintime=int(datetime.now().timestamp())
        if len(stevec_values)>=MAX_VALUES_LENGTH:
            for key,value in stevec_values.items():
                if int(float(key))<mintime:
                    mintime=int(key)
            del stevec_values[str(mintime)]
        stevec_values[str(time)]=int(float(msg_value["actual_consumption"]))
        calculateConsumption()




    client.subscribe(topic)
    client.on_message = on_message

def runmqtt():
    client = connect_mqtt()
    subscribe(client)
    client.loop_start()
    

def runserver():
    db.create_all()
    db.session.query(users).delete()
    db.session.commit()
    app.run(host='0.0.0.0', port=8080)

start_time = 0
end_time = 0


def calculateConsumption():
    global stevec_values
    lastkey=sorted(stevec_values.keys())[-1]
    lastval=stevec_values[lastkey]
    if lastval>0.5:
        allusers= users.query.all()
        if allusers == None:
            return 0
        for user in allusers:
            if user.calculating==True:
                otheruser=users.query.filter(users.id!=user.id,users.calculating==True,users.device==user.device,users.loudness>user.loudness).first()
                if otheruser==None:
                    user.totalConsumption+=lastval/360
        db.session.commit()


@app.route('/data',methods=['GET'])
def getdata():
    allusers= users.query.all()
    out={u.id:"{:.2f}".format(u.totalConsumption) for u in allusers}
    return json.dumps(out)



@app.route('/',methods=['POST','GET'])
def measurement():
    if request.method == 'POST':
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
                    datatosend = json.dumps({"measurment": 1,"calculation":user.totalConsumption })
                    user.loudness=loudness
                    user.device=device
                else:
                    user.calculating=True
                    user.loudness=loudness
                    user.device=device
                    user.starttime=int(datetime.now().timestamp())
                    user.endtime=0
                    datatosend = json.dumps({"measurment": 2,"calculation":user.totalConsumption})
            else:
                datatosend = json.dumps({"measurment": 3 ,"calculation":0})
                user=users(calculating=True,id=user_id,starttime=int(datetime.now().timestamp()),endtime=0,loudness=loudness,device=device,totalConsumption=0)
                db.session.add(user)   
        else:
            if user:
                if user.calculating:
                    user.calculating=False
                    user.device=device
                    user.currentLoudness=0
                    user.endtime=int(datetime.now().timestamp())
                    datatosend=json.dumps({"measurment":4,"calculation": user.totalConsumption})
                else:
                    datatosend = json.dumps({"measurment": 5 ,"calculation":user.totalConsumption})
            else:
                datatosend = json.dumps({"measurment":3,"calculation": 0 })
        db.session.commit()
        return datatosend
    else:
        return send_from_directory('static', "index.html")


if __name__ == '__main__':
    if test:
        thread = Thread(target=simulate,args=(on_test_message,))
        thread.daemon = True
        thread.start()
    else:
        runmqtt()
    runserver()

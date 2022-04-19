# python3.6


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


def subscribe(client: mqtt_client):
    def on_message(client, userdata, msg):
        print(f"Received `{msg.payload.decode()}` from `{msg.topic}` topic")

    client.subscribe(topic)
    client.on_message = on_message


def run():
    client = connect_mqtt()
    subscribe(client)
    client.loop_start()
    app.run(host='0.0.0.0', port=8080)




@app.route('/')
def hello_world():
    return 'Hello World! I am running on port ' + str(port)

if __name__ == '__main__':
    run()

import requests
from datetime import datetime
import json
# api-endpoint
URL = "http://localhost:8080/"
  
# location given here
user_id ="3"
start = False
loudness=10000
device = "aaa"
time = int(datetime.timestamp(datetime.now()))
# defining a params dict for the parameters to be sent to the API
PARAMS ={'userID':user_id,'start':start,'device':device,'time':time,'loudness':loudness}
  
# sending get request and saving the response as response object
r = requests.post(url = URL, data = json.dumps(PARAMS),headers={'Content-Type':'application/json'})
  
# extracting data in json format
data = r.content

print(data)
  
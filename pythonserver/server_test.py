
import requests
from datetime import datetime
import json
# api-endpoint
URL = "http://localhost:8080"
  
# location given here
user_id ="1"
start = False
device = "aaa"
time = datetime.timestamp(datetime.now())
# defining a params dict for the parameters to be sent to the API
PARAMS ={'userID':user_id,'start':start,'device':device,'time':time}
  
# sending get request and saving the response as response object
r = requests.post(url = URL, data = json.dumps(PARAMS),headers={'Content-Type':'application/json'})
  
# extracting data in json format
data = r.content

print(data)
  
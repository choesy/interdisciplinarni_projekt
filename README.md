# interdisciplinarni_projekt

Server accepts POST api requests on "/" with JSON body:   
{. 
    "userID": string,  
    "time": timestamp,  
    "device": string,  
    "start": boolean. 
}. 
For example:   
{. 
    "userID": "USER1",  
    "time": 1650966940,  
    "device": "blender",  
    "start": 1. 
}. 
Start indicates whether to start saving measurements from powermeter (1) or to stop saving measurements (0).  
When api request with start = 0 is sent, the response will be a consumption value in kWh. 

# interdisciplinarni_projekt

Server accepts POST api requests on "/" with JSON body:   
{  
 &nbsp;&nbsp;&nbsp;&nbsp;   "userID": string,  
 &nbsp;&nbsp;&nbsp;&nbsp;  "time": timestamp,  
 &nbsp;&nbsp;&nbsp;&nbsp;   "device": string,  
 &nbsp;&nbsp;&nbsp;&nbsp;   "start": boolean. 
}. 
For example:   
{  
&nbsp;&nbsp;&nbsp;&nbsp;    "userID": "USER1",  
&nbsp;&nbsp;&nbsp;&nbsp;    "time": 1650966940,  
&nbsp;&nbsp;&nbsp;&nbsp;    "device": "blender",  
&nbsp;&nbsp;&nbsp;&nbsp;    "start": 1. 
}  
Start indicates whether to start saving measurements from powermeter (1) or to stop saving measurements (0).  
When api request with start = 0 is sent, the response will be a consumption value in Wh. 

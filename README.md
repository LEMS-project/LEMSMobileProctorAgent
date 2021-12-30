#LEMS Mobile Proctor Agent
This android mobile application is the LEMS learner mobile agent.
It aims at taking pictures back and front and sending them to the proctoring server.

This simple application should be started automatically from the detection of a LEMS QR Code, provided on the learner web interface. it connects then to the LEMS server and starts taking and send pictures

## Features
- automatic startup at lems://proctoring url pattern access 
- back and front camera used at regular interval to take pictures
- users feedbacks provided about the current application state
- users interactions proposed to recover on errors

## Usage
1. Deploy the application on a smartphone etheir compiling it from source-code or using the apk. 
1. When accessing the LEMS web plateform, scan the provided QR code with your smartphone.
1. The application launches, connects and runs by itself
1. A feedback on the lems plateform is provided to indicate the mobile application runs correctly.
1. Once you ends working on the LEMS plateform, you can simply terminate the application.


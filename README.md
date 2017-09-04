[Blockchain Monitoring](http://blockchain-monitoring.org)
================

[Team Dashboard](https://trello.com/invite/blockchainmonitoring/a789c404cf577dc31f7a1b56e82ccc9b)


**Continuous integration:** [![Build Status](https://travis-ci.org/blockchain-monitoring/blockchain-monitoring.svg?branch=master)](https://travis-ci.org/blockchain-monitoring/blockchain-monitoring)

"Blockchain Monitoring" is an open source project designed for **Hyperledger Fabric v1.0**. 

It provides convenient and demonstrative way to represent information
about blockchain fabric network activities.

![demo](http://blockchain-monitoring.org/images/demo.png)
![demo](http://blockchain-monitoring.org/images/dashboard-performance.png)

# About
Project consists of Grafana, Influx DB and "Blockchain Monitoring" as own, which collects and aggregates telemetry from Fabric.
## Requirements
You need Docker and maybe Docker-compose to run "Blockchain Monitoring" and open 3000 and 8086 ports. That's all.
## Installation
You can download docker image with command: `docker pull blockchainmonitoring/blockchain-monitoring:latest`

With docker-compose create docker-compose.yaml file:
```yaml
version: '2'

services:
  monitoring:
    container_name: blockchain-monitoring
    image: blockchainmonitoring/blockchain-monitoring:latest
    volumes:
      - $CERTS_ADMIN:/opt/blockchain-monitoring/certs/admin/:rw
      - $FABRIC_NET_CONFIG:/etc/conf/net-config.yaml
#      if you want customize configuration grafana or influxdb
      - ./influxdb.conf:/etc/influxdb/influxdb.conf
      - ./config/grafana/grafana.ini:/etc/grafana/grafana.ini
    environment:
#     SCHEDULED_TASKS_DELAY - defaule is 1000 milliseconds
      SCHEDULED_TASKS_DELAY: 10000
#     TIME_EVENT_LIFETIME - defaule is 1 hour
      TIME_EVENT_LIFETIME: "00:01:00"
    ports:
      - "3000:3000"
      - "8086:8086"
      - "5006:5005" #debug port
```
and net-config.yaml file:
```yaml
organisations:
- name: 'foo'
  msp: 'foo'
  member:
    login: 'foomember'
    password: 'member'
  ca:
    name: 'cafoo'
    address: 'http://172.25.0.177:7054'
  peers:
    - name: 'peer-foo'
      address: 'grpc://172.25.0.104:7051'
      admin:
        login: 'fooadmin'
        privkey: /opt/blockchain-monitoring/certs/admin/foo/foo-admin-key.pem
        cert:    /opt/blockchain-monitoring/certs/admin/foo/foo-admin-signed.pem

    - name: 'peer-foo-02'
      address: 'grpc://172.25.0.105:7051'
      admin:
        login: 'fooadmin2'
        privkey: /opt/blockchain-monitoring/certs/admin/foo-02/foo-02-admin-key.pem
        cert:    /opt/blockchain-monitoring/certs/admin/foo-02/foo-02-admin-signed.pem

    - name: 'peer-foo-03'
      address: 'grpc://172.25.0.106:7051'
      admin:
        login: 'fooadmin3'
        privkey: /opt/blockchain-monitoring/certs/admin/foo-03/foo-03-admin-key.pem
        cert:    /opt/blockchain-monitoring/certs/admin/foo-03/foo-03-admin-signed.pem

- name: 'bar'
  msp: 'bar'
  member:
    login: 'barmember'
    password: 'member'
  ca:
    name: 'cabar'
    address: 'http://172.25.0.177:7054'
  peers:
    - name: 'peer-bar'
      address: 'grpc://172.25.0.107:7051'
      admin:
        login: 'baradmin'
        privkey: /opt/blockchain-monitoring/certs/admin/bar/bar-admin-key.pem
        cert:    /opt/blockchain-monitoring/certs/admin/bar/bar-admin-signed.pem
```

This file describes fabric network configuration and contains two main sections: organization and channels.
Orgranization section provides information about fabric-CA, fabric-peers name and address, MSP-ID. 
Next section channels show us which peers are connected to channel, their addresses, names and msp-id.

Also you need to set environment variable $FABRIC_NET_CONFIG for net-config.yaml file (**it must be absolute path**) and after that just write:
```bash
docker-compose up
```
If monitoring seccessully started you can access to it by visiting http://localhost:3000 admin:admin

## Use monitoring in your code
"Blockchain Monitoring" provides you simple API, written in Java. 
Visit [link](https://github.com/blockchain-monitoring/blockchain-monitoring-api) for more information.

## Email notification
For example, I set if we get invoke or query send me email notification and attach graph of metrics

### Invoke
![demo](http://blockchain-monitoring.org/images/invoke-alert.png)
### Query
![demo](http://blockchain-monitoring.org/images/query-alert.png)

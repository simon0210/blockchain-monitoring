#!/usr/bin/env bash

function countdown(){
   date1=$((`date +%s` + $1)); 
   while [ "$date1" -ge `date +%s` ]; do 
     echo -ne "$(date -u --date @$(($date1 - `date +%s`)) +%H:%M:%S)\r";
     sleep 0.1
   done
}
echo "
[admin]
enabled = true
bind-address = \":8083\"
https-enabled = false" >> /etc/influxdb/influxdb.conf
cat /etc/influxdb/influxdb.conf
cd /
echo "countdown For 1 min"
countdown 60

./init_grafana.sh &
./init_influx.sh &
java -Xdebug -Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=n -jar blockchain-monitoring.jar

#!/bin/bash
set -euo pipefail
source /opt/network/client-properties.sh
server=${KAFKA_BOOTSTRAP:-kafka:19092}
for topic in network.events.v1 network.events.v1.DLT; do
 /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$server" --command-config /tmp/admin.properties --create --if-not-exists --topic "$topic" --partitions 3 --replication-factor 1
 done
for user in member content messaging hiring; do
 /opt/kafka/bin/kafka-acls.sh --bootstrap-server "$server" --command-config /tmp/admin.properties --add --allow-principal "User:$user" --operation Write --operation Describe --topic network.events.v1
 done
/opt/kafka/bin/kafka-acls.sh --bootstrap-server "$server" --command-config /tmp/admin.properties --add --allow-principal User:notification --operation Read --operation Describe --topic network.events.v1
/opt/kafka/bin/kafka-acls.sh --bootstrap-server "$server" --command-config /tmp/admin.properties --add --allow-principal User:notification --operation Read --group notifications-v1
/opt/kafka/bin/kafka-acls.sh --bootstrap-server "$server" --command-config /tmp/admin.properties --add --allow-principal User:notification --operation Write --operation Describe --topic network.events.v1.DLT
for user in member content notification messaging hiring; do
 /opt/kafka/bin/kafka-acls.sh --bootstrap-server "$server" --command-config /tmp/admin.properties --add --allow-principal "User:$user" --operation IdempotentWrite --cluster
 done

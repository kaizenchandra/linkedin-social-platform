#!/bin/bash
set -euo pipefail
source /opt/network/client-properties.sh
jaas="org.apache.kafka.common.security.plain.PlainLoginModule required username=\"admin\" password=\"${KAFKA_ADMIN_PASSWORD}\" user_admin=\"${KAFKA_ADMIN_PASSWORD}\" user_member=\"${KAFKA_MEMBER_PASSWORD}\" user_content=\"${KAFKA_CONTENT_PASSWORD}\" user_notification=\"${KAFKA_NOTIFICATION_PASSWORD}\" user_messaging=\"${KAFKA_MESSAGING_PASSWORD}\";"
export KAFKA_LISTENER_NAME_INTERNAL_PLAIN_SASL_JAAS_CONFIG="$jaas"
export KAFKA_LISTENER_NAME_EXTERNAL_PLAIN_SASL_JAAS_CONFIG="$jaas"
export KAFKA_LISTENER_NAME_CONTROLLER_PLAIN_SASL_JAAS_CONFIG="$jaas"
exec /etc/kafka/docker/run

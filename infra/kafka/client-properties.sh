#!/bin/bash
set -euo pipefail
umask 077
printf 'security.protocol=SASL_PLAINTEXT\nsasl.mechanism=PLAIN\nsasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="admin" password="%s";\n' "$KAFKA_ADMIN_PASSWORD" > /tmp/admin.properties

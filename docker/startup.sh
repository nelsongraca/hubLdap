#!/bin/ash

echo "" > /opt/hubLdap/hubLdap.properties
echo "hubUrl=${HUB_URL}" >> /opt/hubLdap/hubLdap.properties
echo "rootDomain=${ROOT_DOMAIN}" >> /opt/hubLdap/hubLdap.properties
echo "adminPassword=${ADMIN_PASSWORD}" >> /opt/hubLdap/hubLdap.properties
echo "serviceId=${SERVICE_ID}" >> /opt/hubLdap/hubLdap.properties
echo "serviceSecret=${SERVICE_SECRET}" >> /opt/hubLdap/hubLdap.properties
echo "certificatePassword=${CERTIFICATE_PASSWORD:-secret}" >> /opt/hubLdap/hubLdap.properties

FINAL_JAVA_OPTS="${JAVA_OPTS} -Djava.net.preferIPv4Stack=true"

echo java ${FINAL_JAVA_OPTS} -jar /opt/hubLdap/hubLdap.jar

java ${FINAL_JAVA_OPTS} -jar /opt/hubLdap/hubLdap.jar

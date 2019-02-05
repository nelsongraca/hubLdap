#!/bin/ash

echo "" > /opt/hubLdap/hubLdap.properties
echo "hubUrl=${HUB_URL}" >> /opt/hubLdap/hubLdap.properties
echo "rootDomain=${ROOT_DOMAIN}" >> /opt/hubLdap/hubLdap.properties
echo "adminPassword=${ADMIN_PASSWORD}" >> /opt/hubLdap/hubLdap.properties
echo "serviceId=${SERVICE_ID}" >> /opt/hubLdap/hubLdap.properties
echo "serviceSecret=${SERVICE_SECRET}" >> /opt/hubLdap/hubLdap.properties

java -jar /opt/hubLdap/hubLdap.jar

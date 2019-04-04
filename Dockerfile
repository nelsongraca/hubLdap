FROM openjdk:8u181-alpine3.8

LABEL maintainer="graca.nelson@gmail.com"

# Install startup script for container
ADD /docker/startup.sh /usr/sbin/startup.sh
#run missing commands
RUN apk add --no-cache tini && \
chmod +x /usr/sbin/startup.sh

#install jar
ARG JAR_FILE
ADD /target/${JAR_FILE} /opt/hubLdap/hubLdap.jar

EXPOSE 10389
EXPOSE 10636

ENTRYPOINT ["/sbin/tini", "--"]
CMD /usr/sbin/startup.sh

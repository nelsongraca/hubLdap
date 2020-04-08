FROM openjdk:8u242-slim

# Install startup script for container
COPY /docker/startup.sh /usr/sbin/startup.sh

#add missing stuff
RUN apt-get update && \
    apt-get install -y tini fontconfig &&\
    apt-get clean && \
    chmod +x /usr/sbin/startup.sh

#install jar
ARG JAR_FILE
ADD /target/${JAR_FILE} /opt/hubLdap/hubLdap.jar

EXPOSE 10389
EXPOSE 10636

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD /usr/sbin/startup.sh

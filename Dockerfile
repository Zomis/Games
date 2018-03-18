FROM openjdk:8u151-jre

EXPOSE 8081

# RUN apk add --no-cache git
# RUN git clone https://github.com/Zomis/Server2.git

WORKDIR /server2/
ADD games-server/build/libs/*-all.jar /server2/

VOLUME /data/logs/

CMD java -jar games-server-1.0-SNAPSHOT-all.jar


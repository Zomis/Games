FROM openjdk:8u151-jre

EXPOSE 42638

WORKDIR /server2/
ADD games-server/build/libs/*-all.jar /server2/

VOLUME /data/logs/

CMD java -jar /server2/games-server-1.0-SNAPSHOT-all.jar -httpPort 42638

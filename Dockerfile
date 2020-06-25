#
# Build stage
#
FROM maven:3.6.0-jdk-8-slim AS build
COPY src /home/app/src
COPY pom.xml /home/app
RUN mvn -f /home/app/pom.xml dependency:go-offline
RUN mvn -f /home/app/pom.xml clean package

#
# Package stage
#
FROM java:8-jdk-alpine
COPY --from=build /home/app/target /home/app/target
RUN chmod 755 ./home/app/target/appassembler/bin/chariotMonitoringAgent
CMD ["/home/app/target/appassembler/bin/chariotMonitoringAgent"]


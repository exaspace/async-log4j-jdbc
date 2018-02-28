FROM openjdk:9-jdk-slim

WORKDIR /demo
ADD . /demo
RUN ./gradlew clean build

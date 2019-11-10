FROM openjdk:13

WORKDIR /demo

ADD . /demo

RUN ./gradlew clean build

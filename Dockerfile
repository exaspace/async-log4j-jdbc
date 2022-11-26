FROM eclipse-temurin:18

WORKDIR /demo

ADD . /demo

RUN ./gradlew clean build

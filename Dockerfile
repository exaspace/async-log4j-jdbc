FROM java:8

WORKDIR /demo
ADD . /demo
RUN ./gradlew clean build

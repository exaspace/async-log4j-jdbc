FROM java:8

# can be overridden at runtime with -e flag to docker run
ENV DATABASE postgres

WORKDIR /demo
ADD . /demo
RUN ./gradlew clean build
ENTRYPOINT ./gradlew --offline -Pdatabase=${DATABASE} demo

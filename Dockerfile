FROM ubuntu:bionic
RUN apt-get update && apt-get --yes install default-jre
COPY gradlew build.gradle config.yaml gradle.properties settings.gradle ./
COPY reportingserver ./reportingserver
COPY gradle ./gradle
RUN ./gradlew :reportingserver:build --no-daemon
ENTRYPOINT ["./gradlew", ":reportingserver:run", "--no-daemon"]

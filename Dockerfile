FROM gradle:8.12.0-jdk21 AS BUILD

COPY . /src
WORKDIR /src
RUN gradle --no-daemon shadowJar

FROM amazoncorretto:21-alpine3.18

COPY --from=BUILD /src/build/libs/application.jar /bin/runner/run.jar
WORKDIR /bin/runner

CMD ["java","-jar","run.jar"]

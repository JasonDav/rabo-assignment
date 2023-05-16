FROM maven:3.8.6-amazoncorretto-18 as build
ENV HOME=/usr/app
RUN mkdir -p $HOME
WORKDIR $HOME
ADD . $HOME
RUN --mount=type=cache,target=/root/.m2 mvn -f $HOME/pom.xml clean package -B -DskipTests

FROM amazoncorretto:18.0.2
COPY --from=build /usr/app/target/*.jar /app/runner.jar
ENTRYPOINT ["java", "-jar", "/app/runner.jar"]
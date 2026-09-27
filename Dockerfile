FROM eclipse-temurin:21.0.12.1_1-jre@sha256:d7051a45dd955e4d5d1db4d3f4269fe13d1c6dff8cc6b7ef89fc8577b96c1982
ARG MODULE
WORKDIR /app
COPY ${MODULE}/target/${MODULE}-0.4.0.jar /app/app.jar
USER 10001
EXPOSE 8080 9090
ENTRYPOINT ["java","-jar","/app/app.jar"]

FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY Addon.java .
RUN javac Addon.java
CMD ["java", "Addon"]

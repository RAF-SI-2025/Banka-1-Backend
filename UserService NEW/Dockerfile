# Koristimo zvaničnu, laganu verziju Jave 21
FROM eclipse-temurin:21-jre-alpine

# Postavljamo radni direktorijum unutar kontejnera
WORKDIR /app

# Kopiramo kompajlirani JAR fajl iz tvog build/libs foldera u kontejner
# (Zvezdica osigurava da uhvati fajl bez obzira na tacnu verziju u imenu)
COPY build/libs/*SNAPSHOT.jar app.jar

# Izlažemo port na kom aplikacija radi
EXPOSE 8081

# Komanda koja se izvršava kada se kontejner pokrene
ENTRYPOINT ["java", "-jar", "app.jar"]
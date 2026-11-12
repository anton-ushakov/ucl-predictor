# ============================================================
# Сборка приложения и запуск. Два этапа: сначала собираем jar,
# потом кладём его в маленький образ только с Java.
# ============================================================

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Ограничиваем аппетит Maven: на сервере с 2 ГБ он иначе съедает всё
ENV MAVEN_OPTS="-Xmx512m"

# Сначала только pom.xml: пока он не меняется, зависимости берутся из кэша
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ------------------------------------------------------------

FROM eclipse-temurin:21-jre
WORKDIR /app

# Приложение работает не от администратора: так безопаснее
RUN useradd --system --create-home --shell /usr/sbin/nologin app
USER app

COPY --from=build --chown=app:app /build/target/*.jar app.jar

# Настройки под маленький сервер (1 ядро, 2 ГБ): нам хватает 320 МБ кучи с запасом.
# Если переедете на машину побольше, это можно поднять через JAVA_OPTS в docker-compose.yml.
ENV JAVA_OPTS="-Xmx320m -XX:MaxMetaspaceSize=192m -Xss512k -XX:+UseSerialGC"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

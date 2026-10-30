# Imagen de cualquier servicio Spring Boot; el contexto es la carpeta del servicio.
# Dos etapas: la imagen final no lleva Maven ni el código fuente.

FROM eclipse-temurin:21-jdk AS compilacion
WORKDIR /fuente
# Las dependencias quedan en una capa propia que solo se rehace si cambia el pom
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q -B dependency:go-offline
COPY src src
# Las pruebas corren en desarrollo y en el CI, no al armar la imagen
RUN ./mvnw -q -B -DskipTests package && cp target/*.jar /fuente/servicio.jar

FROM eclipse-temurin:21-jre
ENV TZ=America/La_Paz
# /datos: el volumen de un servicio nace con este dueño (las imágenes de Eventos y Venta)
RUN groupadd --system sarao && useradd --system --gid sarao --no-create-home sarao \
	&& mkdir -p /datos/imagenes && chown -R sarao:sarao /datos
WORKDIR /app
COPY --from=compilacion /fuente/servicio.jar servicio.jar
USER sarao
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "/app/servicio.jar"]

#FROM jboss/keycloak:7.0.1
# FROM jboss/keycloak:8.0.2
FROM hkong2/keycloak
COPY ./config/ClientFhirServerRealm.json ./
#RUN mkdir -p /opt/jboss/keycloak/standalone/data/content
USER 1000
EXPOSE 8080
ENTRYPOINT [ "/opt/jboss/tools/docker-entrypoint.sh", \
        "-Dkeycloak.migration.action=import", \
        "-Dkeycloak.migration.provider=singleFile", \
        "-Dkeycloak.migration.file=./ClientFhirServerRealm.json", \
        "-Dkeycloak.migration.strategy=OVERWRITE_EXISTING" ]
CMD ["-b", "0.0.0.0"]
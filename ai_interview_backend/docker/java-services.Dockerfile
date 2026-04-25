FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /workspace
COPY . .

RUN mvn -B -DskipTests clean package

RUN set -eux; \
    mkdir -p /dist; \
    modules="ai-interviewer-gateway ai-interviewer-user ai-interviewer-resume ai-interviewer-interview ai-interviewer-job ai-interviewer-evaluation ai-interviewer-notification"; \
    for module in $modules; do \
      exec_jar="$(find "${module}/target" -maxdepth 1 -type f -name '*-exec.jar' | head -n 1 || true)"; \
      if [ -n "$exec_jar" ]; then \
        jar="$exec_jar"; \
      else \
        jar="$(find "${module}/target" -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name '*.jar.original' | head -n 1 || true)"; \
      fi; \
      if [ -z "$jar" ]; then \
        echo "No runnable jar found for module ${module}" >&2; \
        exit 1; \
      fi; \
      cp "$jar" "/dist/${module}.jar"; \
    done

FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/apps
COPY --from=builder /dist /opt/apps
COPY docker/run-java-service.sh /usr/local/bin/run-java-service.sh

ENV JAVA_OPTS="-XX:InitialRAMPercentage=25 -XX:MaxRAMPercentage=75"

ENTRYPOINT ["/usr/local/bin/run-java-service.sh"]

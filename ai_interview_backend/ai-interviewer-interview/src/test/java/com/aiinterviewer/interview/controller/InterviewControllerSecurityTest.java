package com.aiinterviewer.interview.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestHeader;

class InterviewControllerSecurityTest {

    @Test
    void everyInterviewBusinessEndpointRequiresGatewayAuthenticatedUserHeader() {
        List<String> endpoints = List.of(
                "chat",
                "resumeInterview",
                "listInterviews",
                "listIncompleteSessions",
                "listLineages",
                "getLineageTree",
                "getSession",
                "getBranchTranscript",
                "cancelInterview");

        for (Method method : InterviewController.class.getDeclaredMethods()) {
            if (!endpoints.contains(method.getName())) {
                continue;
            }
            assertThat(Arrays.stream(method.getParameters())
                    .map(parameter -> parameter.getAnnotation(RequestHeader.class))
                    .filter(annotation -> annotation != null
                            && "X-User-Id".equals(annotation.value())
                            && annotation.required()))
                    .as(method.getName() + " must require the Gateway user identity")
                    .hasSize(1);
        }
    }

    @Test
    void composePublishesInterviewAndEvaluationOnlyInsideTheGatewayNetwork() throws Exception {
        String compose = Files.readString(resolveCompose());
        for (String service : List.of("interview", "evaluation")) {
            Matcher matcher = Pattern.compile(
                            "(?ms)^  " + service
                                    + ":\\R(?<body>.*?)(?=^  [a-zA-Z0-9_-]+:\\R|^networks:\\R)")
                    .matcher(compose);

            assertThat(matcher.find()).as(service + " service exists").isTrue();
            assertThat(matcher.group("body"))
                    .as(service + " must only be reachable through the Gateway network")
                    .contains("ai-interviewer-net")
                    .doesNotContain("\n    ports:");
        }
    }

    private static Path resolveCompose() {
        String reactorRoot = System.getProperty("maven.multiModuleProjectDirectory", "");
        return Stream.of(
                        reactorRoot.isBlank() ? null : Path.of(reactorRoot, "docker-compose.yml"),
                        Path.of("docker-compose.yml"),
                        Path.of("..", "docker-compose.yml"))
                .filter(path -> path != null && Files.isRegularFile(path))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot locate ai_interview_backend/docker-compose.yml"));
    }
}

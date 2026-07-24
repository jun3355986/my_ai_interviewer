package com.aiinterviewer.interview.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record ForkStateSnapshot(
        int schemaVersion,
        String currentStage,
        int branchStatus,
        int projectQuestionsCount,
        int targetProjectQuestions,
        int currentFollowupCount,
        List<Object> projectQuestionsPool,
        List<Object> technicalQuestionsPool) {

    public static final String METADATA_KEY = "_postTurnStateV1";
    public static final int SCHEMA_VERSION = 1;

    public static ForkStateSnapshot from(AuthoritativeTurnState state) {
        return new ForkStateSnapshot(
                SCHEMA_VERSION,
                state.currentStage(),
                state.branchStatus(),
                state.projectQuestionsCount(),
                state.targetProjectQuestions(),
                state.currentFollowupCount(),
                copy(state.projectQuestionsPool()),
                copy(state.technicalQuestionsPool()));
    }

    public static Optional<ForkStateSnapshot> fromMetadata(Map<String, Object> metadata) {
        if (metadata == null || !(metadata.get(METADATA_KEY) instanceof Map<?, ?> raw)) {
            return Optional.empty();
        }
        try {
            int version = number(raw.get("schemaVersion"));
            String stage = text(raw.get("currentStage"));
            int status = number(raw.get("branchStatus"));
            int projectCount = number(raw.get("projectQuestionsCount"));
            int projectTarget = number(raw.get("targetProjectQuestions"));
            int followupCount = number(raw.get("currentFollowupCount"));
            List<Object> projectPool = list(raw.get("projectQuestionsPool"));
            List<Object> technicalPool = list(raw.get("technicalQuestionsPool"));
            if (version != SCHEMA_VERSION
                    || stage == null
                    || stage.isBlank()
                    || status != 1
                    || projectCount < 0
                    || projectTarget < 0
                    || followupCount < 0) {
                return Optional.empty();
            }
            return Optional.of(new ForkStateSnapshot(
                    version,
                    stage,
                    status,
                    projectCount,
                    projectTarget,
                    followupCount,
                    projectPool,
                    technicalPool));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public Map<String, Object> toMetadataValue() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", schemaVersion);
        value.put("currentStage", currentStage);
        value.put("branchStatus", branchStatus);
        value.put("projectQuestionsCount", projectQuestionsCount);
        value.put("targetProjectQuestions", targetProjectQuestions);
        value.put("currentFollowupCount", currentFollowupCount);
        value.put("projectQuestionsPool", projectQuestionsPool);
        value.put("technicalQuestionsPool", technicalQuestionsPool);
        return value;
    }

    private static int number(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Fork state number is missing");
        }
        return number.intValue();
    }

    private static String text(Object value) {
        return value instanceof String text ? text : null;
    }

    private static List<Object> list(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Fork state pool is missing");
        }
        return new ArrayList<>(list);
    }

    private static List<Object> copy(List<?> value) {
        return value == null ? List.of() : new ArrayList<>(value);
    }
}

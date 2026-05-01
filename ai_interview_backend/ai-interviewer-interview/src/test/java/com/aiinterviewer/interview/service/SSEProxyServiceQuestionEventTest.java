package com.aiinterviewer.interview.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiinterviewer.interview.sse.SSEEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SSEProxyServiceQuestionEventTest {

    @Test
    void questionEventIsKnownAndTextCanBeExtractedForPersistence() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String data = """
                {"question":{"id":"123","text":"请结合下图说明两个 Lua 脚本关系。","media":[{"type":"image","url":"https://example.com/figure.png","caption":"图 10-17"}]},"next_stage":"technical_qna"}
                """;

        String text = objectMapper.readTree(data).get("question").get("text").asText();

        assertThat(SSEEventType.fromValue("question")).isEqualTo(SSEEventType.QUESTION);
        assertThat(text).isEqualTo("请结合下图说明两个 Lua 脚本关系。");
    }
}

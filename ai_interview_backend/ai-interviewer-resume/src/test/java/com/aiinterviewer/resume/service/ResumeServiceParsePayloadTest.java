package com.aiinterviewer.resume.service;

import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.resume.entity.ResumeContent;
import com.aiinterviewer.resume.mapper.ResumeMapper;
import com.aiinterviewer.resume.mapper.ResumeVersionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ResumeServiceParsePayloadTest {

    private final ResumeService service = new ResumeService(
            mock(ResumeMapper.class),
            mock(ResumeVersionMapper.class),
            mock(FileStorageService.class),
            mock(org.springframework.web.reactive.function.client.WebClient.Builder.class),
            new ObjectMapper());

    @Test
    void extractsTopLevelRawTextFromNewParseResponse() {
        String payload = """
                {
                  "name": "张三",
                  "skills": ["Java", "Redis"],
                  "otherInfo": "姓名：张三\\n工作经验：5年",
                  "rawText": "姓名：张三\\n工作经验：5年\\n项目：电商订单系统"
                }
                """;

        ResumeService.ParsedResume parsed = service.parseResumePayload(payload);

        assertThat(parsed.content().getName()).isEqualTo("张三");
        assertThat(parsed.rawText()).contains("电商订单系统");
        assertThat(parsed.rawText()).startsWith("姓名：张三");
    }

    @Test
    void fallsBackToOtherInfoWhenRawTextMissing() {
        String payload = """
                {
                  "name": "李四",
                  "otherInfo": "Name: Li Si\\nSkills: Docker"
                }
                """;

        ResumeService.ParsedResume parsed = service.parseResumePayload(payload);

        assertThat(parsed.content().getName()).isEqualTo("李四");
        assertThat(parsed.rawText()).isEqualTo("Name: Li Si\nSkills: Docker");
    }

    @Test
    void unwrapsContentWrapperAndReadsRawSnakeCaseField() {
        String payload = """
                {
                  "content": {
                    "name": "王五",
                    "otherInfo": "legacy body"
                  },
                  "raw_text": "完整原文"
                }
                """;

        ResumeService.ParsedResume parsed = service.parseResumePayload(payload);

        assertThat(parsed.content().getName()).isEqualTo("王五");
        assertThat(parsed.rawText()).isEqualTo("完整原文");
    }

    @Test
    void rejectsMalformedPayloadWithControlledBusinessException() {
        assertThatThrownBy(() -> service.parseResumePayload("not-json"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("响应格式不受支持");
    }
}

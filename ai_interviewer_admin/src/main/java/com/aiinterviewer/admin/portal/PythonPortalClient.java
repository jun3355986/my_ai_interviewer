package com.aiinterviewer.admin.portal;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

/**
 * Python AI 服务的门户客户端：简历结构化解析 + 模型/检索运行时配置透传。
 */
@Component
public class PythonPortalClient {

    public record ExchangeResponse(HttpStatusCode status, String body) {
    }

    private final RestClient restClient;
    private final String resumeParseUrl;
    private final String runtimeConfigUrl;

    public PythonPortalClient(
            RestClient.Builder restClientBuilder,
            @Value("${python-ai.resume-parse-url:http://localhost:8000/resume/parse}") String resumeParseUrl,
            @Value("${python-ai.runtime-config-url:http://localhost:8000/admin/runtime-config}") String runtimeConfigUrl) {
        this.restClient = restClientBuilder.build();
        this.resumeParseUrl = resumeParseUrl;
        this.runtimeConfigUrl = runtimeConfigUrl;
    }

    /** 调用 Python /resume/parse，返回结构化字段 Map；解析失败抛 IllegalStateException。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseResume(MultipartFile file) {
        try {
            String filename = file.getOriginalFilename() == null ? "resume" : file.getOriginalFilename();
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("file", fileResource);
            Map<String, Object> parsed = restClient.post()
                    .uri(resumeParseUrl)
                    .headers(headers -> headers.setContentType(MediaType.MULTIPART_FORM_DATA))
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            if (parsed == null) {
                throw new IllegalStateException("简历解析返回为空");
            }
            return parsed;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("简历解析失败: " + ex.getMessage(), ex);
        }
    }

    public ExchangeResponse getRuntimeConfig() {
        return restClient.get()
                .uri(runtimeConfigUrl)
                .accept(MediaType.APPLICATION_JSON)
                .exchange((request, response) -> new ExchangeResponse(
                        response.getStatusCode(),
                        response.bodyTo(String.class)));
    }

    public ExchangeResponse putRuntimeConfig(String jsonBody) {
        return restClient.put()
                .uri(runtimeConfigUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(jsonBody == null ? "{}" : jsonBody)
                .exchange((request, response) -> new ExchangeResponse(
                        response.getStatusCode(),
                        response.bodyTo(String.class)));
    }

    public ExchangeResponse testRuntimeConfig() {
        return restClient.post()
                .uri(runtimeConfigUrl + "/test")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body("{}")
                .exchange((request, response) -> new ExchangeResponse(
                        response.getStatusCode(),
                        response.bodyTo(String.class)));
    }
}

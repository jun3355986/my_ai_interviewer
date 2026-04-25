package com.aiinterviewer.admin;

import com.aiinterviewer.admin.evaluation.mapper.AdminEvaluationMapper;
import com.aiinterviewer.admin.interview.mapper.AdminInterviewMapper;
import com.aiinterviewer.admin.job.mapper.AdminJobMapper;
import com.aiinterviewer.admin.questionbank.mapper.QuestionMapper;
import com.aiinterviewer.admin.resume.mapper.AdminResumeMapper;
import com.aiinterviewer.admin.user.mapper.AdminUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration,"
                + "com.alibaba.cloud.nacos.NacosDiscoveryAutoConfiguration,"
                + "com.alibaba.cloud.nacos.registry.NacosServiceRegistryAutoConfiguration,"
                + "com.alibaba.cloud.nacos.discovery.NacosDiscoveryClientConfiguration,"
                + "com.alibaba.cloud.nacos.NacosConfigAutoConfiguration",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class AdminApplicationTests {

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AdminUserMapper adminUserMapper;

    @MockBean
    private AdminResumeMapper adminResumeMapper;

    @MockBean
    private AdminJobMapper adminJobMapper;

    @MockBean
    private AdminInterviewMapper adminInterviewMapper;

    @MockBean
    private AdminEvaluationMapper adminEvaluationMapper;

    @MockBean
    private QuestionMapper questionMapper;

    @Test
    void contextLoads() {
    }
}

package com.aiinterviewer.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.interview.dto.CreateStartAttemptRequest;
import com.aiinterviewer.interview.dto.CreateTurnAttemptRequest;
import com.aiinterviewer.interview.dto.RetryTurnAttemptRequest;
import com.aiinterviewer.interview.dto.StartAttemptDTO;
import com.aiinterviewer.interview.dto.TurnAttemptDTO;
import com.aiinterviewer.interview.dto.TurnAttemptEventDTO;
import com.aiinterviewer.interview.entity.InterviewTurnAttempt;
import com.aiinterviewer.interview.model.TurnModelClient;
import com.aiinterviewer.interview.model.TurnModelCommand;
import com.aiinterviewer.interview.model.TurnModelResult;
import com.aiinterviewer.interview.model.BranchSnapshot;
import com.aiinterviewer.interview.model.BranchSnapshotMessage;
import com.aiinterviewer.interview.model.AuthoritativeTurnState;
import com.aiinterviewer.interview.model.WebClientTurnModelClient;
import com.aiinterviewer.interview.repository.TurnAttemptRepository;
import com.aiinterviewer.interview.repository.StartAttemptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.Disposable;

@Testcontainers
class TurnAttemptLifecycleIntegrationTest {

    private static final String BRANCH_ID = "ef3d58eb84c74358a4b55dd09ff635b2";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private AnnotationConfigApplicationContext context;
    private TurnAttemptService service;
    private StartAttemptService startService;
    private TurnAttemptRepository repository;
    private JdbcTemplate jdbc;
    private ControlledTurnModelClient modelClient;
    private TurnAttemptEventPublisher eventPublisher;
    private DataSource testDataSource;

    @BeforeEach
    void setUp() throws SQLException {
        DataSource dataSource = dataSource();
        testDataSource = dataSource;
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("db/legacy/V0__legacy_interview_schema.sql"));
        }
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .table("flyway_interview_schema_history")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("0"))
                .load()
                .migrate();

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS t_resume (
                        id BIGINT PRIMARY KEY,
                        user_id BIGINT NOT NULL REFERENCES t_user(id),
                        raw_text TEXT,
                        parsed_content JSONB
                    )
                    """);
            statement.execute("ALTER TABLE t_job ADD COLUMN IF NOT EXISTS requirements TEXT");
            statement.execute("ALTER TABLE t_job ADD COLUMN IF NOT EXISTS status SMALLINT DEFAULT 1");
            statement.execute("UPDATE t_job SET requirements = 'Java 21 and Spring', status = 1 WHERE id = 10");
        }

        context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerSingleton("dataSource", dataSource);
        context.register(TestConfiguration.class);
        context.refresh();
        service = context.getBean(TurnAttemptService.class);
        startService = context.getBean(StartAttemptService.class);
        repository = context.getBean(TurnAttemptRepository.class);
        jdbc = context.getBean(JdbcTemplate.class);
        modelClient = context.getBean(ControlledTurnModelClient.class);
        eventPublisher = context.getBean(TurnAttemptEventPublisher.class);
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void durableStartCreatesOwnedRootAndCommitsOpeningAsSystemTrigger() {
        jdbc.update("INSERT INTO t_resume(id, user_id, raw_text, parsed_content) VALUES (?, ?, ?, ?::jsonb)",
                20L, 1L, "十年 Java 后端经验", "{\"name\":\"张三\"}");
        modelClient.enqueueSuccess(openingSuccess("欢迎参加面试，请先介绍一下自己。"));

        StartAttemptDTO started = startService.create(
                1L,
                "alice",
                startRequest("start-opening-1", 20L, 10L));

        awaitStatus("start-opening-1", "COMPLETED");
        assertThat(started.getLineageId()).isEqualTo(started.getBranchId());
        assertThat(started.getAttempt().getTurnId()).isEqualTo("start-opening-1");
        assertThat(started.getAttempt().getExpectedBranchVersion()).isEqualTo(1L);
        assertThat(started.getAttempt().getExpectedTailMessageId()).isNull();
        assertThat(query("""
                SELECT user_id || '|' || resume_id || '|' || job_id || '|' || candidate_name
                    || '|' || stage || '|' || status || '|' || resume_content
                    || '|' || job_requirements
                FROM t_interview_session WHERE id = '%s'
                """.formatted(started.getBranchId())))
                .isEqualTo("1|20|10|张三|self_introduction|1|十年 Java 后端经验|Java 21 and Spring");
        assertThat(jdbc.queryForList("""
                        SELECT role || '|' || message_type || '|' || content AS value
                        FROM t_interview_message
                        WHERE session_id = ?
                        ORDER BY sequence
                        """, String.class, started.getBranchId()))
                .containsExactly(
                        "human|system_trigger|我准备好了",
                        "ai|ai_question|欢迎参加面试，请先介绍一下自己。");
    }

    @Test
    void durableStartReplaysConcurrentExactTurnAndRejectsChangedPayload() throws Exception {
        jdbc.update("INSERT INTO t_resume(id, user_id, raw_text, parsed_content) VALUES (?, ?, ?, ?::jsonb)",
                21L, 1L, "resume", "{\"name\":\"李四\"}");
        modelClient.enqueueSuccess(openingSuccess("opening"));
        CreateStartAttemptRequest request = startRequest("start-concurrent", 21L, 10L);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<StartAttemptDTO> first = executor.submit(() -> startService.create(1L, "alice", request));
            Future<StartAttemptDTO> second = executor.submit(() -> startService.create(1L, "alice", request));

            StartAttemptDTO left = first.get(10, TimeUnit.SECONDS);
            StartAttemptDTO right = second.get(10, TimeUnit.SECONDS);

            assertThat(right.getBranchId()).isEqualTo(left.getBranchId());
            assertThat(right.getAttempt().getTurnId()).isEqualTo(left.getAttempt().getTurnId());
            assertThat(count("SELECT count(*) FROM t_interview_lineage WHERE id = '" + left.getLineageId() + "'"))
                    .isEqualTo(1);
            assertThat(count("SELECT count(*) FROM t_interview_session WHERE id = '" + left.getBranchId() + "'"))
                    .isEqualTo(1);
            assertThat(count("SELECT count(*) FROM t_interview_turn_attempt WHERE id = 'start-concurrent'"))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        CreateStartAttemptRequest changed = startRequest("start-concurrent", null, 10L);
        assertThatThrownBy(() -> startService.create(1L, "alice", changed))
                .isInstanceOf(TurnAttemptConflictException.class)
                .hasMessage("IDEMPOTENCY_PAYLOAD_MISMATCH");
    }

    @Test
    void durableStartReplayRequiresImmutableRootAndCurrentLineageOwnership() {
        jdbc.update("INSERT INTO t_user(id) VALUES (2)");
        modelClient.enqueueFailure(new IllegalStateException("opening failed"));
        CreateStartAttemptRequest request = startRequest("start-owner-drift", null, 10L);
        StartAttemptDTO started = startService.create(1L, "alice", request);
        awaitStatus("start-owner-drift", "FAILED");

        jdbc.update(
                "UPDATE t_interview_lineage SET user_id = 2 WHERE id = ?",
                started.getLineageId());

        for (Long userId : List.of(1L, 2L)) {
            assertDenied(() -> startService.create(userId, "user-" + userId, request));
        }
    }

    @Test
    void durableStartRejectsForeignResumeWithoutCreatingRoot() {
        jdbc.update("INSERT INTO t_user(id) VALUES (2)");
        jdbc.update("INSERT INTO t_resume(id, user_id, raw_text, parsed_content) VALUES (?, ?, ?, ?::jsonb)",
                22L, 2L, "secret", "{\"name\":\"Other\"}");
        long beforeLineages = count("SELECT count(*) FROM t_interview_lineage");
        long beforeBranches = count("SELECT count(*) FROM t_interview_session");

        assertThatThrownBy(() -> startService.create(
                        1L,
                        "alice",
                        startRequest("start-foreign-resume", 22L, 10L)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(3000);

        assertThat(count("SELECT count(*) FROM t_interview_lineage")).isEqualTo(beforeLineages);
        assertThat(count("SELECT count(*) FROM t_interview_session")).isEqualTo(beforeBranches);
        assertThat(count("SELECT count(*) FROM t_interview_turn_attempt WHERE id = 'start-foreign-resume'"))
                .isZero();
    }

    @Test
    void durableStartRollsBackRootWhenOpeningAttemptInsertFails() {
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION reject_start_attempt() RETURNS trigger AS $$
                BEGIN
                    IF NEW.id = 'start-db-failure' THEN
                        RAISE EXCEPTION 'forced start failure';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER reject_start_attempt_trigger
                BEFORE INSERT ON t_interview_turn_attempt
                FOR EACH ROW EXECUTE FUNCTION reject_start_attempt()
                """);

        assertThatThrownBy(() -> startService.create(
                        1L,
                        "alice",
                        startRequest("start-db-failure", null, 10L)))
                .isInstanceOf(RuntimeException.class);

        assertThat(count("SELECT count(*) FROM t_interview_turn_attempt WHERE id = 'start-db-failure'"))
                .isZero();
        assertThat(count("SELECT count(*) FROM t_interview_lineage WHERE id LIKE 'start-%'"))
                .isZero();
        assertThat(count("SELECT count(*) FROM t_interview_session WHERE id LIKE 'start-%'"))
                .isZero();
    }

    @Test
    void storesCandidateBeforeModelCompletionWithoutCanonicalMessage() throws Exception {
        BlockingBehavior behavior = modelClient.enqueueBlocking(success("下一题", 86));

        TurnAttemptDTO attempt = service.create(BRANCH_ID, 1L, request("turn-durable", "候选回答", 1L, 6L));

        assertThat(behavior.started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(service.get("turn-durable", 1L).getCandidateAnswer()).isEqualTo("候选回答");
        assertThat(service.get("turn-durable", 1L).getStatus()).isEqualTo("PROCESSING");
        assertThat(count("SELECT count(*) FROM t_interview_message WHERE turn_id = 'turn-durable'"))
                .isZero();
        assertThat(attempt.getTurnId()).isEqualTo("turn-durable");

        behavior.release.countDown();
        awaitStatus("turn-durable", "COMPLETED");
    }

    @Test
    void modelFailurePreservesInputAndCreatesNoCanonicalRowsOrRawError() {
        modelClient.enqueueFailure(new IllegalStateException("secret transport stack detail"));

        service.create(BRANCH_ID, 1L, request("turn-failed", "可恢复回答", 1L, 6L));

        TurnAttemptDTO failed = awaitStatus("turn-failed", "FAILED");
        assertThat(failed.getCandidateAnswer()).isEqualTo("可恢复回答");
        assertThat(failed.getErrorCode()).isEqualTo("MODEL_PROCESSING_FAILED");
        assertThat(failed.toString()).doesNotContain("secret transport stack detail");
        assertThat(count("SELECT count(*) FROM t_interview_message WHERE turn_id = 'turn-failed'"))
                .isZero();
        assertThat(count("SELECT count(*) FROM t_score_record WHERE turn_id = 'turn-failed'"))
                .isZero();
    }

    @Test
    void productionModelBoundaryRejectsTruncatedSseWithoutCanonicalRows() throws Exception {
        HttpServer python = startPythonStub("""
                event: status
                data: {"session_id":"py-truncated","stage":"project_qna"}

                event: chunk
                data: {"content":"不完整输出"}

                event: result
                data: {"next_stage":"project_qna","next_question":"不完整输出"}

                """);
        try {
            WebClientTurnModelClient productionClient = new WebClientTurnModelClient(
                    WebClient.builder().build(),
                    new ObjectMapper(),
                    "http://127.0.0.1:" + python.getAddress().getPort());
            modelClient.enqueueBehavior(productionClient::process);

            service.create(BRANCH_ID, 1L, request("turn-truncated", "回答", 1L, 6L));

            TurnAttemptDTO failed = awaitStatus("turn-truncated", "FAILED");
            assertThat(failed.getErrorCode()).isEqualTo("MODEL_PROCESSING_FAILED");
            assertThat(count("SELECT count(*) FROM t_interview_message WHERE turn_id = 'turn-truncated'"))
                    .isZero();
            assertThat(count("SELECT count(*) FROM t_score_record WHERE turn_id = 'turn-truncated'"))
                    .isZero();
        } finally {
            python.stop(0);
        }
    }

    @Test
    void productionModelBoundaryRejectsResultDoneStageMismatch() throws Exception {
        HttpServer python = startPythonStub("""
                event: status
                data: {"session_id":"py-mismatch","stage":"project_qna"}

                event: chunk
                data: {"content":"完整文本但终态矛盾"}

                event: result
                data: {"next_stage":"technical_qna","next_question":"完整文本但终态矛盾"}

                event: done
                data: {"stage":"project_qna","is_interview_complete":false}

                """);
        try {
            WebClientTurnModelClient productionClient = new WebClientTurnModelClient(
                    WebClient.builder().build(),
                    new ObjectMapper(),
                    "http://127.0.0.1:" + python.getAddress().getPort());
            modelClient.enqueueBehavior(productionClient::process);

            service.create(BRANCH_ID, 1L, request("turn-mismatch", "回答", 1L, 6L));

            awaitStatus("turn-mismatch", "FAILED");
            assertThat(count("SELECT count(*) FROM t_interview_message WHERE turn_id = 'turn-mismatch'"))
                    .isZero();
        } finally {
            python.stop(0);
        }
    }

    @Test
    void successCommitsMessagesScoreBranchActivityAndAttemptTogether() {
        modelClient.enqueueSuccess(success("请说明 JVM 内存模型。", 91, true));

        service.create(BRANCH_ID, 1L, request("turn-success", "一致性回答", 1L, 6L));

        awaitStatus("turn-success", "COMPLETED");
        assertThat(count("SELECT count(*) FROM t_interview_message WHERE turn_id = 'turn-success'"))
                .isEqualTo(2);
        assertThat(query("""
                SELECT role || '|' || message_type || '|' || sequence
                FROM t_interview_message
                WHERE turn_id = 'turn-success'
                ORDER BY sequence
                LIMIT 1
                """))
                .isEqualTo("human|candidate_answer|7");
        assertThat(query("""
                SELECT role || '|' || message_type || '|' || expects_response
                FROM t_interview_message
                WHERE turn_id = 'turn-success'
                ORDER BY sequence DESC
                LIMIT 1
                """))
                .isEqualTo("ai|ai_question|true");
        assertThat(query("""
                SELECT metadata ->> 'id'
                FROM t_interview_message
                WHERE turn_id = 'turn-success' AND role = 'ai'
                """))
                .isEqualTo("q-next");
        assertThat(query("""
                SELECT turn_id || '|' || question_message_id || '|' || answer_message_id || '|' || score || '|' || is_followup
                FROM t_score_record
                WHERE turn_id = 'turn-success'
                """))
                .startsWith("turn-success|6|")
                .endsWith("|91|true");
        assertThat(count("SELECT branch_version FROM t_interview_session WHERE id = '" + BRANCH_ID + "'"))
                .isEqualTo(2);
        assertThat(query("""
                SELECT (lineage.last_business_activity_at = session.last_business_activity_at)::text
                FROM t_interview_lineage lineage
                JOIN t_interview_session session ON session.lineage_id = lineage.id
                WHERE session.id = '%s'
                """.formatted(BRANCH_ID))).isEqualTo("true");
    }

    @Test
    void authoritativePostTurnStateFeedsTheNextSnapshotAndUsernameSurvivesAsyncBoundary() {
        modelClient.enqueueSuccess(new TurnModelResult(
                "第二道项目题",
                "project_qna",
                false,
                91,
                "feedback-1",
                Map.of("id", "project-second", "text", "第二道项目题"),
                BRANCH_ID,
                new AuthoritativeTurnState(
                        "project_qna",
                        1,
                        1,
                        3,
                        2,
                        List.of("第三道项目题"),
                        List.of(Map.of("id", "tech-1", "text", "技术题一")))));

        service.create(
                BRANCH_ID,
                1L,
                "alice",
                request("turn-state-1", "第一轮回答", 1L, 6L));
        awaitStatus("turn-state-1", "COMPLETED");

        assertThat(repository.findById("turn-state-1").orElseThrow().getUsername())
                .isEqualTo("alice");
        assertThat(repository.findById("turn-state-1").orElseThrow().getOwnerUserId())
                .isEqualTo(1L);
        assertThat(query("""
                SELECT stage || '|' || status || '|' || project_questions_count || '|'
                    || target_project_questions || '|' || current_followup_count || '|'
                    || project_questions_pool::text || '|' || technical_questions_pool::text
                FROM t_interview_session WHERE id = '%s'
                """.formatted(BRANCH_ID)))
                .isEqualTo("project_qna|1|1|3|2|[\"第三道项目题\"]|[{\"id\": \"tech-1\", \"text\": \"技术题一\"}]");
        assertThat(query("""
                SELECT (metadata ->> 'id') || '|'
                    || (metadata -> '_postTurnStateV1' ->> 'schemaVersion') || '|'
                    || (metadata -> '_postTurnStateV1' ->> 'currentStage') || '|'
                    || (metadata -> '_postTurnStateV1' ->> 'branchStatus') || '|'
                    || (metadata -> '_postTurnStateV1' ->> 'projectQuestionsCount') || '|'
                    || (metadata -> '_postTurnStateV1' ->> 'targetProjectQuestions') || '|'
                    || (metadata -> '_postTurnStateV1' ->> 'currentFollowupCount')
                FROM t_interview_message
                WHERE turn_id = 'turn-state-1' AND role = 'ai'
                """))
                .isEqualTo("project-second|1|project_qna|1|1|3|2");
        assertThat(query("""
                SELECT (metadata -> '_postTurnStateV1' -> 'projectQuestionsPool')::text || '|'
                    || (metadata -> '_postTurnStateV1' -> 'technicalQuestionsPool')::text
                FROM t_interview_message
                WHERE turn_id = 'turn-state-1' AND role = 'ai'
                """))
                .isEqualTo("[\"第三道项目题\"]|[{\"id\": \"tech-1\", \"text\": \"技术题一\"}]");

        Long secondTail = repository.findTailMessageId(BRANCH_ID);
        modelClient.enqueueBehavior(command -> {
            assertThat(command.username()).isEqualTo("alice");
            assertThat(command.branchSnapshot().projectQuestionsCount()).isEqualTo(1);
            assertThat(command.branchSnapshot().targetProjectQuestions()).isEqualTo(3);
            assertThat(command.branchSnapshot().currentFollowupCount()).isEqualTo(2);
            assertThat(command.branchSnapshot().projectQuestionsPool())
                    .containsExactly("第三道项目题");
            assertThat(command.branchSnapshot().technicalQuestionsPool())
                    .containsExactly(Map.of("id", "tech-1", "text", "技术题一"));
            return new TurnModelResult(
                    "技术题一",
                    "technical_qna",
                    false,
                    92,
                    "feedback-2",
                    Map.of("id", "tech-1", "text", "技术题一"),
                    BRANCH_ID,
                    new AuthoritativeTurnState(
                            "technical_qna",
                            1,
                            2,
                            3,
                            0,
                            List.of(),
                            List.of()));
        });

        service.create(
                BRANCH_ID,
                1L,
                "alice",
                request("turn-state-2", "第二轮回答", 2L, secondTail));
        awaitStatus("turn-state-2", "COMPLETED");

        assertThat(count("SELECT branch_version FROM t_interview_session WHERE id = '" + BRANCH_ID + "'"))
                .isEqualTo(3);
    }

    @Test
    void databaseFailureInsideCompletionRollsBackCanonicalChangesAndLeavesRecovery() {
        jdbc.execute("""
                CREATE FUNCTION reject_forced_turn_score() RETURNS trigger AS $$
                BEGIN
                    IF NEW.turn_id = 'turn-rollback' THEN
                        RAISE EXCEPTION 'forced commit failure';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER reject_forced_turn_score
                BEFORE INSERT ON t_score_record
                FOR EACH ROW EXECUTE FUNCTION reject_forced_turn_score()
                """);
        modelClient.enqueueSuccess(success("不会提交的问题", 75));

        service.create(BRANCH_ID, 1L, request("turn-rollback", "事务回答", 1L, 6L));

        awaitStatus("turn-rollback", "FAILED");
        assertThat(count("SELECT count(*) FROM t_interview_message WHERE turn_id = 'turn-rollback'"))
                .isZero();
        assertThat(count("SELECT count(*) FROM t_score_record WHERE turn_id = 'turn-rollback'"))
                .isZero();
        assertThat(count("SELECT branch_version FROM t_interview_session WHERE id = '" + BRANCH_ID + "'"))
                .isEqualTo(1);
    }

    @Test
    void rejectsStaleVersionOrTailWithoutSchedulingWork() {
        assertThatThrownBy(() -> service.create(
                        BRANCH_ID, 1L, request("turn-stale-version", "回答", 0L, 6L)))
                .isInstanceOf(TurnAttemptConflictException.class)
                .hasMessageContaining("BRANCH_VERSION_CONFLICT");
        assertThatThrownBy(() -> service.create(
                        BRANCH_ID, 1L, request("turn-stale-tail", "回答", 1L, 5L)))
                .isInstanceOf(TurnAttemptConflictException.class)
                .hasMessageContaining("BRANCH_TAIL_CONFLICT");
        assertThat(modelClient.invocations()).isZero();
    }

    @Test
    void exactDuplicateReturnsSameAttemptAndSchedulesOnceButPayloadMismatchConflicts() throws Exception {
        BlockingBehavior behavior = modelClient.enqueueBlocking(success("下一题", 80));
        CreateTurnAttemptRequest request = request("turn-duplicate", "同一回答", 1L, 6L);

        TurnAttemptDTO first = service.create(BRANCH_ID, 1L, request);
        TurnAttemptDTO duplicate = service.create(BRANCH_ID, 1L, request);

        assertThat(behavior.started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(duplicate.getTurnId()).isEqualTo(first.getTurnId());
        assertThat(modelClient.invocations()).isEqualTo(1);
        assertThatThrownBy(() -> service.create(
                        BRANCH_ID, 1L, request("turn-duplicate", "篡改回答", 1L, 6L)))
                .isInstanceOf(TurnAttemptConflictException.class)
                .hasMessageContaining("IDEMPOTENCY_PAYLOAD_MISMATCH");

        behavior.release.countDown();
        awaitStatus("turn-duplicate", "COMPLETED");
    }

    @Test
    void serviceAndDatabaseRejectSecondProcessingAttemptInLineage() throws Exception {
        BlockingBehavior behavior = modelClient.enqueueBlocking(success("下一题", 80));
        service.create(BRANCH_ID, 1L, request("turn-active", "回答一", 1L, 6L));
        assertThat(behavior.started.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> service.create(
                        BRANCH_ID, 1L, request("turn-second", "回答二", 1L, 6L)))
                .isInstanceOf(TurnAttemptConflictException.class)
                .hasMessageContaining("LINEAGE_PROCESSING_CONFLICT");
        assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO t_interview_turn_attempt(
                            id, lineage_id, session_id, owner_user_id, expected_branch_version,
                            expected_tail_message_id, candidate_answer, status
                        ) VALUES (?, ?, ?, 1, 1, 6, 'db race', 'PROCESSING')
                        """, "turn-db-race", BRANCH_ID, BRANCH_ID))
                .isInstanceOf(RuntimeException.class);

        behavior.release.countDown();
        awaitStatus("turn-active", "COMPLETED");
    }

    @Test
    void disposingEventSubscriberDoesNotCancelServerOwnedWorker() throws Exception {
        BlockingBehavior behavior = modelClient.enqueueBlocking(success("订阅关闭后仍完成", 88));
        service.create(BRANCH_ID, 1L, request("turn-detached", "回答", 1L, 6L));
        assertThat(behavior.started.await(5, TimeUnit.SECONDS)).isTrue();

        Disposable subscription = service.events("turn-detached", 1L).subscribe();
        subscription.dispose();
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(eventPublisher.activeStreamCount()).isZero());
        behavior.release.countDown();

        awaitStatus("turn-detached", "COMPLETED");
        assertThat(count("SELECT count(*) FROM t_interview_message WHERE turn_id = 'turn-detached'"))
                .isEqualTo(2);
    }

    @Test
    void cancellationPreventsLateCommitEvenWhenModelIgnoresInterrupt() throws Exception {
        BlockingBehavior behavior = modelClient.enqueueBlockingIgnoringInterrupt(success("晚到结果", 99));
        service.create(BRANCH_ID, 1L, request("turn-cancel", "待取消回答", 1L, 6L));
        assertThat(behavior.started.await(5, TimeUnit.SECONDS)).isTrue();

        TurnAttemptDTO cancelled = service.cancel("turn-cancel", 1L);

        assertThat(cancelled.getStatus()).isEqualTo("CANCEL_REQUESTED");
        assertThatThrownBy(() -> service.create(
                        BRANCH_ID, 1L, request("turn-after-cancel", "新回答", 1L, 6L)))
                .isInstanceOf(TurnAttemptConflictException.class)
                .hasMessageContaining("LINEAGE_PROCESSING_CONFLICT");
        assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO t_interview_turn_attempt(
                            id, lineage_id, session_id, owner_user_id, expected_branch_version,
                            expected_tail_message_id, candidate_answer, status
                        ) VALUES (?, ?, ?, 1, 1, 6, 'db race after cancel', 'PROCESSING')
                        """, "turn-db-after-cancel", BRANCH_ID, BRANCH_ID))
                .isInstanceOf(RuntimeException.class);

        behavior.release.countDown();
        awaitStatus("turn-cancel", "CANCELLED");
        await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(count("SELECT count(*) FROM t_interview_message WHERE turn_id = 'turn-cancel'"))
                        .isZero());
    }

    @Test
    void cancellationFinalizesAfterCommitWhenModelHonorsInterrupt() throws Exception {
        BlockingBehavior behavior = modelClient.enqueueBlocking(success("不应提交", 99));
        service.create(BRANCH_ID, 1L, request("turn-cancel-interruptible", "待取消回答", 1L, 6L));
        assertThat(behavior.started.await(5, TimeUnit.SECONDS)).isTrue();

        TurnAttemptDTO cancelRequested = service.cancel("turn-cancel-interruptible", 1L);

        assertThat(cancelRequested.getStatus()).isEqualTo("CANCEL_REQUESTED");
        awaitStatus("turn-cancel-interruptible", "CANCELLED");
        assertThat(count("SELECT count(*) FROM t_interview_message WHERE turn_id = 'turn-cancel-interruptible'"))
                .isZero();
    }

    @Test
    void sessionCancellationDuringModelWorkPreventsLateCanonicalCommit() throws Exception {
        BlockingBehavior behavior = modelClient.enqueueBlockingIgnoringInterrupt(success("晚到问题", 92));
        service.create(BRANCH_ID, 1L, request("turn-session-cancel", "回答", 1L, 6L));
        assertThat(behavior.started.await(5, TimeUnit.SECONDS)).isTrue();

        jdbc.update("UPDATE t_interview_session SET status = 3 WHERE id = ?", BRANCH_ID);
        behavior.release.countDown();

        TurnAttemptDTO interrupted = awaitStatus("turn-session-cancel", "INTERRUPTED");
        assertThat(interrupted.getErrorCode()).isEqualTo("BRANCH_NOT_ACTIVE");
        assertThat(count("SELECT count(*) FROM t_interview_message WHERE turn_id = 'turn-session-cancel'"))
                .isZero();
        assertThat(count("SELECT branch_version FROM t_interview_session WHERE id = '" + BRANCH_ID + "'"))
                .isEqualTo(1);
    }

    @Test
    void ownershipReassignmentDuringModelWorkPreventsLateCanonicalCommit() throws Exception {
        jdbc.update("INSERT INTO t_user(id) VALUES (2)");
        BlockingBehavior behavior = modelClient.enqueueBlockingIgnoringInterrupt(
                success("不应提交的换主结果", 92));
        service.create(BRANCH_ID, 1L, request("turn-owner-reassigned", "回答", 1L, 6L));
        assertThat(behavior.started.await(5, TimeUnit.SECONDS)).isTrue();

        jdbc.update("UPDATE t_interview_lineage SET user_id = 2 WHERE id = ?", BRANCH_ID);
        jdbc.update("UPDATE t_interview_session SET user_id = 2 WHERE id = ?", BRANCH_ID);
        behavior.release.countDown();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.findById("turn-owner-reassigned").orElseThrow().getStatus())
                        .isEqualTo("INTERRUPTED"));
        assertThat(repository.findById("turn-owner-reassigned").orElseThrow().getErrorCode())
                .isEqualTo("OWNERSHIP_CHANGED");
        assertThat(count("SELECT count(*) FROM t_interview_message WHERE turn_id = 'turn-owner-reassigned'"))
                .isZero();
        assertThat(count("SELECT count(*) FROM t_score_record WHERE turn_id = 'turn-owner-reassigned'"))
                .isZero();
        assertThat(count("SELECT branch_version FROM t_interview_session WHERE id = '" + BRANCH_ID + "'"))
                .isEqualTo(1);
    }

    @Test
    void terminalReconnectReturnsOnlyAuthoritativeSnapshotAndCompletes() {
        modelClient.enqueueSuccess(success("终态问题", 89));
        service.create(BRANCH_ID, 1L, request("turn-terminal-events", "回答", 1L, 6L));
        awaitStatus("turn-terminal-events", "COMPLETED");

        List<TurnAttemptEventDTO> events = service.events("turn-terminal-events", 1L)
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().type()).isEqualTo("snapshot");
        assertThat(events.getFirst().status()).isEqualTo("COMPLETED");
        assertThat(eventPublisher.activeStreamCount()).isZero();
    }

    @Test
    void activeEventStreamIsOrderedCompletesAtTerminalAndCleansUpSink() throws Exception {
        BlockingBehavior behavior = modelClient.enqueueBlocking(success("事件终态问题", 90));
        service.create(BRANCH_ID, 1L, request("turn-live-events", "回答", 1L, 6L));
        assertThat(behavior.started.await(5, TimeUnit.SECONDS)).isTrue();

        List<TurnAttemptEventDTO> observed = new CopyOnWriteArrayList<>();
        CountDownLatch completed = new CountDownLatch(1);
        service.events("turn-live-events", 1L).subscribe(
                observed::add,
                failure -> { },
                completed::countDown);
        behavior.release.countDown();

        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(observed).isNotEmpty();
        assertThat(observed.getFirst().type()).isEqualTo("snapshot");
        assertThat(observed.getFirst().status()).isEqualTo("PROCESSING");
        assertThat(observed.getLast().status()).isEqualTo("COMPLETED");
        assertThat(observed).extracting(TurnAttemptEventDTO::sequence)
                .doesNotHaveDuplicates()
                .isSorted();
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(eventPublisher.activeStreamCount()).isZero());
    }

    @Test
    void retryCanEditCandidateAndDiscardHidesFailedAttemptWithoutDuplicatingCanonicalData() {
        modelClient.enqueueFailure(new IllegalStateException("temporary"));
        service.create(BRANCH_ID, 1L, "alice", request("turn-original", "旧回答", 1L, 6L));
        awaitStatus("turn-original", "FAILED");

        modelClient.enqueueBehavior(command -> {
            assertThat(command.username()).isEqualTo("alice");
            assertThat(command.branchSnapshot().username()).isEqualTo("alice");
            return success("重试后的下一题", 87);
        });
        RetryTurnAttemptRequest retry = new RetryTurnAttemptRequest();
        retry.setTurnId("turn-retry");
        retry.setCandidateAnswer("编辑后的回答");
        retry.setExpectedBranchVersion(1L);
        retry.setExpectedTailMessageId(6L);
        service.retry("turn-original", 1L, retry);
        awaitStatus("turn-retry", "COMPLETED");

        assertThat(repository.findById("turn-retry").orElseThrow().getRetryOfId())
                .isEqualTo("turn-original");
        assertThat(repository.findById("turn-retry").orElseThrow().getUsername())
                .isEqualTo("alice");
        assertThat(count("SELECT count(*) FROM t_interview_message WHERE turn_id = 'turn-retry'"))
                .isEqualTo(2);
        assertThat(count("SELECT count(*) FROM t_interview_message WHERE turn_id = 'turn-original'"))
                .isZero();

        TurnAttemptDTO discarded = service.discard("turn-original", 1L);
        assertThat(discarded.getStatus()).isEqualTo("DISCARDED");
        assertThat(service.listRecoverable(BRANCH_ID, 1L))
                .extracting(TurnAttemptDTO::getTurnId)
                .doesNotContain("turn-original");
    }

    @Test
    void deniesCrossUserCreateReadEventsRetryCancelAndDiscard() {
        jdbc.update("INSERT INTO t_user(id) VALUES (2)");
        modelClient.enqueueFailure(new IllegalStateException("failure"));
        service.create(BRANCH_ID, 1L, request("turn-owned", "回答", 1L, 6L));
        awaitStatus("turn-owned", "FAILED");

        assertDenied(() -> service.create(BRANCH_ID, 2L, request("turn-foreign", "回答", 1L, 6L)));
        assertDenied(() -> service.get("turn-owned", 2L));
        assertDenied(() -> service.events("turn-owned", 2L));
        RetryTurnAttemptRequest retry = new RetryTurnAttemptRequest();
        retry.setTurnId("turn-foreign-retry");
        retry.setCandidateAnswer("回答");
        retry.setExpectedBranchVersion(1L);
        retry.setExpectedTailMessageId(6L);
        assertDenied(() -> service.retry("turn-owned", 2L, retry));
        assertDenied(() -> service.cancel("turn-owned", 2L));
        assertDenied(() -> service.discard("turn-owned", 2L));
    }

    @Test
    void attemptAccessRequiresImmutableAttemptAndCurrentBranchAndLineageOwnership() {
        jdbc.update("INSERT INTO t_user(id) VALUES (2)");
        modelClient.enqueueFailure(new IllegalStateException("failure"));
        service.create(BRANCH_ID, 1L, request("turn-reassigned-access", "回答", 1L, 6L));
        awaitStatus("turn-reassigned-access", "FAILED");

        jdbc.update("UPDATE t_interview_session SET user_id = 2 WHERE id = ?", BRANCH_ID);
        assertAttemptDeniedForBothOwners("turn-reassigned-access");
        assertBranchAttemptCreationDeniedForBothOwners("session-only-reassignment");

        jdbc.update("UPDATE t_interview_session SET user_id = 1 WHERE id = ?", BRANCH_ID);
        jdbc.update("UPDATE t_interview_lineage SET user_id = 2 WHERE id = ?", BRANCH_ID);
        assertAttemptDeniedForBothOwners("turn-reassigned-access");
        assertBranchAttemptCreationDeniedForBothOwners("lineage-only-reassignment");

        jdbc.update("UPDATE t_interview_session SET user_id = 2 WHERE id = ?", BRANCH_ID);
        assertAttemptDeniedForBothOwners("turn-reassigned-access");
        modelClient.enqueueFailure(new IllegalStateException("new owner failure"));
        service.create(BRANCH_ID, 2L, request("turn-new-owner", "新所有者回答", 1L, 6L));
        awaitStatus("turn-new-owner", "FAILED", 2L);
    }

    @Test
    void startupRecoveryInterruptsOnlyStaleProcessingAttemptsAndClosesLiveEvents()
            throws Exception {
        LocalDateTime now = LocalDateTime.now();
        insertProcessing("turn-stale", now.minusMinutes(30));
        insertProcessingInSeparateLineage("turn-recent", now.minusSeconds(10));
        List<TurnAttemptEventDTO> observed = new CopyOnWriteArrayList<>();
        CountDownLatch completed = new CountDownLatch(1);
        service.events("turn-stale", 1L).subscribe(
                observed::add,
                failure -> { },
                completed::countDown);

        int recovered = service.recoverStaleProcessing(Duration.ofMinutes(5));

        assertThat(recovered).isEqualTo(1);
        assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(observed).extracting(TurnAttemptEventDTO::status)
                .containsExactly("PROCESSING", "INTERRUPTED");
        assertThat(repository.findById("turn-stale").orElseThrow().getStatus())
                .isEqualTo("INTERRUPTED");
        assertThat(repository.findById("turn-recent").orElseThrow().getStatus())
                .isEqualTo("PROCESSING");
        assertThat(eventPublisher.activeStreamCount()).isZero();
    }

    private void insertProcessing(String turnId, LocalDateTime updatedAt) {
        jdbc.update("""
                INSERT INTO t_interview_turn_attempt(
                    id, lineage_id, session_id, owner_user_id, expected_branch_version,
                    expected_tail_message_id, candidate_answer, status,
                    created_at, processing_started_at, updated_at
                ) VALUES (?, ?, ?, 1, 1, 6, 'stale', 'PROCESSING', ?, ?, ?)
                """, turnId, BRANCH_ID, BRANCH_ID, updatedAt, updatedAt, updatedAt);
    }

    private void assertAttemptDeniedForBothOwners(String turnId) {
        for (Long userId : List.of(1L, 2L)) {
            assertDenied(() -> service.get(turnId, userId));
            assertDenied(() -> service.events(turnId, userId));
            RetryTurnAttemptRequest retry = new RetryTurnAttemptRequest();
            retry.setTurnId(turnId + "-retry-" + userId);
            retry.setCandidateAnswer("回答");
            retry.setExpectedBranchVersion(1L);
            retry.setExpectedTailMessageId(6L);
            assertDenied(() -> service.retry(turnId, userId, retry));
            assertDenied(() -> service.cancel(turnId, userId));
            assertDenied(() -> service.discard(turnId, userId));
        }
    }

    private void assertBranchAttemptCreationDeniedForBothOwners(String idPrefix) {
        for (Long userId : List.of(1L, 2L)) {
            assertDenied(() -> service.create(
                    BRANCH_ID,
                    userId,
                    request(idPrefix + "-" + userId, "回答", 1L, 6L)));
            assertDenied(() -> service.listRecoverable(BRANCH_ID, userId));
        }
    }

    private void insertProcessingInSeparateLineage(String turnId, LocalDateTime updatedAt) {
        try (Connection connection = testDataSource.getConnection();
                Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("""
                    INSERT INTO t_interview_lineage(
                        id, user_id, root_session_id, last_business_activity_at,
                        archived, created_at, updated_at
                    ) VALUES (
                        'recent-lineage', 1, 'recent-branch', TIMESTAMP '%1$s',
                        false, TIMESTAMP '%1$s', TIMESTAMP '%1$s'
                    )
                    """.formatted(updatedAt));
            statement.execute("""
                    INSERT INTO t_interview_session(
                        id, user_id, stage, status, lineage_id, branch_label, branch_version,
                        last_business_activity_at, legacy_migrated, created_at, updated_at
                    ) VALUES (
                        'recent-branch', 1, 'opening', 1, 'recent-lineage', 'Recent', 1,
                        TIMESTAMP '%1$s', false, TIMESTAMP '%1$s', TIMESTAMP '%1$s'
                    )
                    """.formatted(updatedAt));
            statement.execute("""
                    INSERT INTO t_interview_turn_attempt(
                        id, lineage_id, session_id, owner_user_id, expected_branch_version,
                        candidate_answer, status, created_at, processing_started_at, updated_at
                    ) VALUES (
                        '%1$s', 'recent-lineage', 'recent-branch', 1, 1,
                        'recent', 'PROCESSING', TIMESTAMP '%2$s', TIMESTAMP '%2$s', TIMESTAMP '%2$s'
                    )
                    """.formatted(turnId, updatedAt));
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void assertDenied(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(2003);
    }

    private CreateTurnAttemptRequest request(
            String turnId, String answer, Long version, Long tailMessageId) {
        CreateTurnAttemptRequest request = new CreateTurnAttemptRequest();
        request.setTurnId(turnId);
        request.setCandidateAnswer(answer);
        request.setExpectedBranchVersion(version);
        request.setExpectedTailMessageId(tailMessageId);
        return request;
    }

    private CreateStartAttemptRequest startRequest(String turnId, Long resumeId, Long jobId) {
        CreateStartAttemptRequest request = new CreateStartAttemptRequest();
        request.setTurnId(turnId);
        request.setResumeId(resumeId);
        request.setJobId(jobId);
        return request;
    }

    private TurnModelResult openingSuccess(String aiMessage) {
        return new TurnModelResult(
                aiMessage,
                "self_introduction",
                false,
                null,
                null,
                Map.of("id", "opening-question", "text", aiMessage),
                "python-opening",
                new AuthoritativeTurnState(
                        "self_introduction",
                        1,
                        0,
                        5,
                        0,
                        List.of(),
                        List.of()));
    }

    private TurnModelResult success(String aiMessage, int score) {
        return success(aiMessage, score, false);
    }

    private TurnModelResult success(String aiMessage, int score, boolean isFollowup) {
        return new TurnModelResult(
                aiMessage,
                "project_qna",
                false,
                score,
                "反馈仅用于评分记录",
                isFollowup,
                Map.of("id", "q-next", "text", aiMessage),
                null,
                null);
    }

    private TurnAttemptDTO awaitStatus(String turnId, String expectedStatus) {
        return awaitStatus(turnId, expectedStatus, 1L);
    }

    private TurnAttemptDTO awaitStatus(String turnId, String expectedStatus, Long userId) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(service.get(turnId, userId).getStatus()).isEqualTo(expectedStatus));
        return service.get(turnId, userId);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private String query(String sql) {
        return jdbc.queryForObject(sql, String.class);
    }

    private static HttpServer startPythonStub(String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/interview/chat", exchange -> {
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static DataSource dataSource() {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        return source;
    }

    @Configuration
    @EnableTransactionManagement
    @Import({
        StartAttemptRepository.class,
        TurnAttemptRepository.class,
        TurnAttemptEventPublisher.class,
        StartAttemptService.class,
        TurnCommitService.class,
        TurnAttemptWorker.class,
        TurnAttemptService.class
    })
    static class TestConfiguration {

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean(destroyMethod = "shutdown")
        ThreadPoolTaskExecutor turnAttemptExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(2);
            executor.setMaxPoolSize(2);
            executor.setThreadNamePrefix("turn-test-");
            executor.initialize();
            return executor;
        }

        @Bean
        ControlledTurnModelClient turnModelClient() {
            return new ControlledTurnModelClient();
        }

        @Bean
        ComposedAssessmentService composedAssessmentService() {
            return org.mockito.Mockito.mock(ComposedAssessmentService.class);
        }

        @Bean
        BranchSnapshotComposer branchSnapshotComposer(
                JdbcTemplate jdbcTemplate,
                ObjectMapper objectMapper) {
            return new DatabaseSnapshotComposer(jdbcTemplate, objectMapper);
        }
    }

    static final class DatabaseSnapshotComposer extends BranchSnapshotComposer {

        private final JdbcTemplate jdbcTemplate;
        private final ObjectMapper objectMapper;

        DatabaseSnapshotComposer(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
            super(null, null, null, null);
            this.jdbcTemplate = jdbcTemplate;
            this.objectMapper = objectMapper;
        }

        @Override
        public BranchSnapshot compose(
                InterviewTurnAttempt attempt,
                Long authenticatedUserId,
                String username) {
            Map<String, Object> branch = jdbcTemplate.queryForMap("""
                    SELECT stage, status, branch_version, candidate_name, resume_content,
                           job_requirements, project_questions_count,
                           target_project_questions, current_followup_count,
                           project_questions_pool::text AS project_pool,
                           technical_questions_pool::text AS technical_pool
                    FROM t_interview_session WHERE id = ?
                    """, attempt.getSessionId());
            List<BranchSnapshotMessage> messages;
            if (attempt.getExpectedTailMessageId() == null) {
                messages = List.of();
            } else {
                BranchSnapshotMessage tailMessage = jdbcTemplate.queryForObject("""
                        SELECT id, session_id, role, content, stage, message_type,
                               expects_response, metadata::text, sequence
                        FROM t_interview_message WHERE id = ?
                        """, (rs, rowNumber) -> new BranchSnapshotMessage(
                        rs.getLong("id"),
                        rs.getString("session_id"),
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getString("stage"),
                        rs.getString("message_type"),
                        rs.getBoolean("expects_response"),
                        readMap(rs.getString("metadata")),
                        rs.getInt("sequence"),
                        1), attempt.getExpectedTailMessageId());
                messages = List.of(tailMessage);
            }
            return new BranchSnapshot(
                    1,
                    attempt.getId(),
                    attempt.getSessionId(),
                    attempt.getLineageId(),
                    ((Number) branch.get("branch_version")).longValue(),
                    attempt.getExpectedTailMessageId(),
                    authenticatedUserId,
                    username,
                    (String) branch.get("candidate_name"),
                    (String) branch.get("resume_content"),
                    (String) branch.get("job_requirements"),
                    (String) branch.get("stage"),
                    ((Number) branch.get("status")).intValue(),
                    intValue(branch.get("project_questions_count"), 0),
                    intValue(branch.get("target_project_questions"), 5),
                    intValue(branch.get("current_followup_count"), 0),
                    readList((String) branch.get("project_pool")),
                    readList((String) branch.get("technical_pool")),
                    messages,
                    List.of());
        }

        private List<Object> readList(String json) {
            if (json == null) {
                return List.of();
            }
            try {
                return objectMapper.readValue(
                        json,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Object.class));
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> readMap(String json) {
            if (json == null) {
                return Map.of();
            }
            try {
                return objectMapper.readValue(json, Map.class);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private static int intValue(Object value, int fallback) {
            return value == null ? fallback : ((Number) value).intValue();
        }
    }

    static final class ControlledTurnModelClient implements TurnModelClient {

        private final Queue<Behavior> behaviors = new ArrayDeque<>();
        private final AtomicInteger invocationCount = new AtomicInteger();

        synchronized void enqueueSuccess(TurnModelResult result) {
            behaviors.add(command -> result);
        }

        synchronized void enqueueFailure(RuntimeException failure) {
            behaviors.add(command -> { throw failure; });
        }

        synchronized void enqueueBehavior(Behavior behavior) {
            behaviors.add(behavior);
        }

        synchronized BlockingBehavior enqueueBlocking(TurnModelResult result) {
            BlockingBehavior behavior = new BlockingBehavior(result, false);
            behaviors.add(behavior);
            return behavior;
        }

        synchronized BlockingBehavior enqueueBlockingIgnoringInterrupt(TurnModelResult result) {
            BlockingBehavior behavior = new BlockingBehavior(result, true);
            behaviors.add(behavior);
            return behavior;
        }

        int invocations() {
            return invocationCount.get();
        }

        @Override
        public TurnModelResult process(TurnModelCommand command) throws Exception {
            invocationCount.incrementAndGet();
            Behavior behavior;
            synchronized (this) {
                behavior = behaviors.remove();
            }
            return behavior.process(command);
        }
    }

    @FunctionalInterface
    interface Behavior {
        TurnModelResult process(TurnModelCommand command) throws Exception;
    }

    static final class BlockingBehavior implements Behavior {

        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        private final TurnModelResult result;
        private final boolean ignoreInterrupt;

        BlockingBehavior(TurnModelResult result, boolean ignoreInterrupt) {
            this.result = result;
            this.ignoreInterrupt = ignoreInterrupt;
        }

        @Override
        public TurnModelResult process(TurnModelCommand command) throws Exception {
            started.countDown();
            if (!ignoreInterrupt) {
                release.await();
                return result;
            }
            boolean released = false;
            while (!released) {
                try {
                    released = release.await(50, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignored) {
                    // Simulates a provider that cannot cancel an in-flight request.
                }
            }
            return result;
        }
    }
}

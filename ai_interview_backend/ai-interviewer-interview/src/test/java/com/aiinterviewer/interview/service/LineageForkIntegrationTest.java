package com.aiinterviewer.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.interview.dto.BranchMessageDTO;
import com.aiinterviewer.interview.dto.BranchTranscriptDTO;
import com.aiinterviewer.interview.dto.CreateForkAttemptRequest;
import com.aiinterviewer.interview.dto.CreateTurnAttemptRequest;
import com.aiinterviewer.interview.dto.ForkAttemptDTO;
import com.aiinterviewer.interview.entity.InterviewSession;
import com.aiinterviewer.interview.mapper.InterviewSessionMapper;
import com.aiinterviewer.interview.model.AuthoritativeTurnState;
import com.aiinterviewer.interview.model.TurnModelResult;
import com.aiinterviewer.interview.repository.ForkBranchRepository;
import com.aiinterviewer.interview.repository.TurnAttemptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class LineageForkIntegrationTest {

    private static final String ROOT = "ef3d58eb84c74358a4b55dd09ff635b2";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private AnnotationConfigApplicationContext context;
    private ForkAttemptService forkService;
    private TurnAttemptService turnAttemptService;
    private TurnCommitService commitService;
    private TurnAttemptRepository attemptRepository;
    private JdbcTemplate jdbc;
    private InterviewHistoryService historyService;
    private InterviewSessionMapper sessionMapper;
    private TurnAttemptWorker worker;
    private ComposedAssessmentService composedAssessmentService;

    @BeforeEach
    void setUp() throws SQLException {
        DataSource dataSource = dataSource();
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

        historyService = mock(InterviewHistoryService.class);
        sessionMapper = mock(InterviewSessionMapper.class);
        worker = mock(TurnAttemptWorker.class);
        composedAssessmentService = mock(ComposedAssessmentService.class);
        context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerSingleton("dataSource", dataSource);
        context.getBeanFactory().registerSingleton("historyService", historyService);
        context.getBeanFactory().registerSingleton("sessionMapper", sessionMapper);
        context.getBeanFactory().registerSingleton("worker", worker);
        context.getBeanFactory().registerSingleton(
                "composedAssessmentService",
                composedAssessmentService);
        context.register(TestConfiguration.class);
        context.refresh();

        forkService = context.getBean(ForkAttemptService.class);
        turnAttemptService = context.getBean(TurnAttemptService.class);
        commitService = context.getBean(TurnCommitService.class);
        attemptRepository = context.getBean(TurnAttemptRepository.class);
        jdbc = context.getBean(JdbcTemplate.class);

        jdbc.update("""
                UPDATE t_interview_session
                SET legacy_migrated = FALSE,
                    resume_content = 'resume',
                    job_requirements = 'job',
                    project_questions_pool = '["p-next"]'::jsonb,
                    technical_questions_pool = '[{"id":"t1"}]'::jsonb,
                    current_followup_count = 0
                WHERE id = ?
                """, ROOT);
        jdbc.update("""
                UPDATE t_score_record
                SET question_message_id = 4,
                    answer_message_id = 5
                WHERE id = 1
                """);

        InterviewSession source = new InterviewSession();
        source.setId(ROOT);
        source.setUserId(1L);
        source.setJobId(10L);
        source.setCandidateName("Legacy Candidate");
        source.setResumeContent("resume");
        source.setJobRequirements("job");
        source.setLineageId(ROOT);
        when(sessionMapper.selectById(ROOT)).thenReturn(source);
        when(historyService.getBranchTranscript(ROOT, 1L)).thenReturn(rootTranscript());
        com.aiinterviewer.interview.dto.ComposedAssessmentDTO inherited =
                new com.aiinterviewer.interview.dto.ComposedAssessmentDTO();
        inherited.setId(1L);
        inherited.setOwningBranchId(ROOT);
        inherited.setInherited(true);
        when(composedAssessmentService.compose(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(1L)))
                .thenReturn(List.of(inherited));
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void atomicallyCreatesRecoverableChildAndAttemptThenCommitsOnlyChildDelta() {
        String sourceFingerprint = sourceFingerprint();
        CreateForkAttemptRequest request = request("fork-atomic", "edited answer", 1L, 6L);

        ForkAttemptDTO first = forkService.create(ROOT, 1L, "alice", request);
        ForkAttemptDTO duplicate = forkService.create(ROOT, 1L, "alice", request);

        assertThat(duplicate.getBranchId()).isEqualTo(first.getBranchId());
        assertThat(duplicate.getAttempt().getTurnId()).isEqualTo("fork-atomic");
        assertThat(count("SELECT count(*) FROM t_interview_session WHERE parent_session_id = '" + ROOT + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM t_interview_turn_attempt WHERE id = 'fork-atomic'"))
                .isEqualTo(1);
        assertThat(attemptRepository.findTailMessageId(first.getBranchId())).isEqualTo(6L);
        assertThat(query("""
                SELECT parent_session_id || '|' || fork_point_message_id || '|'
                    || fork_trigger_message_id || '|' || branch_version || '|'
                    || stage || '|' || status || '|' || project_questions_count || '|'
                    || target_project_questions || '|' || current_followup_count
                FROM t_interview_session WHERE id = '%s'
                """.formatted(first.getBranchId())))
                .isEqualTo(ROOT + "|6|6|1|technical_qna|1|2|4|1");
        assertThat(query("SELECT branch_label FROM t_interview_session WHERE id = '%s'"
                .formatted(first.getBranchId())))
                .isEqualTo("分支 1");
        assertThat(query("""
                SELECT project_questions_pool -> 0 ->> 'id'
                FROM t_interview_session
                WHERE id = '%s'
                """.formatted(first.getBranchId())))
                .isEqualTo("p-next");
        verify(worker).schedule("fork-atomic");

        assertThatThrownBy(() -> forkService.create(
                        ROOT,
                        1L,
                        "alice",
                        request("fork-atomic", "different answer", 1L, 6L)))
                .isInstanceOf(TurnAttemptConflictException.class)
                .hasMessageContaining("IDEMPOTENCY_PAYLOAD_MISMATCH");

        commitService.commit("fork-atomic", result("child next question", 91));

        assertThat(turnAttemptService.get("fork-atomic", 1L).getStatus()).isEqualTo("COMPLETED");
        assertThat(count("SELECT count(*) FROM t_interview_message WHERE session_id = '"
                + first.getBranchId() + "'"))
                .isEqualTo(2);
        assertThat(query("""
                SELECT string_agg(role || ':' || sequence, ',' ORDER BY sequence)
                FROM t_interview_message WHERE session_id = '%s'
                """.formatted(first.getBranchId())))
                .isEqualTo("human:1,ai:2");
        assertThat(count("SELECT question_index FROM t_score_record WHERE turn_id = 'fork-atomic'"))
                .isEqualTo(2);
        assertThat(sourceFingerprint()).isEqualTo(sourceFingerprint);
        assertThat(count("SELECT branch_version FROM t_interview_session WHERE id = '"
                + first.getBranchId() + "'"))
                .isEqualTo(2);
    }

    @Test
    void invalidStaleProcessingAndAttemptInsertFailureLeaveNoEmptyChild() {
        long baseline = count("SELECT count(*) FROM t_interview_session");

        assertThatThrownBy(() -> forkService.create(
                        ROOT, 1L, null, request("fork-stale", "answer", 0L, 6L)))
                .isInstanceOf(TurnAttemptConflictException.class)
                .hasMessageContaining("BRANCH_VERSION_CONFLICT");
        assertThat(count("SELECT count(*) FROM t_interview_session")).isEqualTo(baseline);

        BranchTranscriptDTO invalid = rootTranscript();
        invalid.getMessages().getFirst().setForkable(false);
        when(historyService.getBranchTranscript(ROOT, 1L)).thenReturn(invalid);
        assertThatThrownBy(() -> forkService.create(
                        ROOT, 1L, null, request("fork-invalid", "answer", 1L, 6L)))
                .isInstanceOf(TurnAttemptConflictException.class)
                .hasMessageContaining("FORK_TRIGGER_NOT_FORKABLE");
        assertThat(count("SELECT count(*) FROM t_interview_session")).isEqualTo(baseline);
        when(historyService.getBranchTranscript(ROOT, 1L)).thenReturn(rootTranscript());

        jdbc.update("""
                INSERT INTO t_interview_turn_attempt(
                    id, lineage_id, session_id, owner_user_id, expected_branch_version,
                    expected_tail_message_id, candidate_answer, status
                ) VALUES ('active-turn', ?, ?, 1, 1, 6, 'active', 'PROCESSING')
                """, ROOT, ROOT);
        assertThatThrownBy(() -> forkService.create(
                        ROOT, 1L, null, request("fork-busy", "answer", 1L, 6L)))
                .isInstanceOf(TurnAttemptConflictException.class)
                .hasMessageContaining("LINEAGE_PROCESSING_CONFLICT");
        assertThat(count("SELECT count(*) FROM t_interview_session")).isEqualTo(baseline);
        jdbc.update("UPDATE t_interview_turn_attempt SET status = 'FAILED' WHERE id = 'active-turn'");

        jdbc.execute("""
                CREATE FUNCTION reject_fork_attempt() RETURNS trigger AS $$
                BEGIN
                    IF NEW.id = 'fork-db-fail' THEN
                        RAISE EXCEPTION 'forced attempt failure';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER reject_fork_attempt
                BEFORE INSERT ON t_interview_turn_attempt
                FOR EACH ROW EXECUTE FUNCTION reject_fork_attempt()
                """);
        assertThatThrownBy(() -> forkService.create(
                        ROOT, 1L, null, request("fork-db-fail", "answer", 1L, 6L)))
                .isInstanceOf(TurnAttemptConflictException.class);
        assertThat(count("SELECT count(*) FROM t_interview_session")).isEqualTo(baseline);
        assertThat(count("SELECT count(*) FROM t_interview_turn_attempt WHERE id = 'fork-db-fail'"))
                .isZero();
    }

    @Test
    void concurrentExactDuplicatesReturnOneChildAndOneAttempt() throws Exception {
        clearInvocations(worker);
        CreateForkAttemptRequest request = request("fork-concurrent", "same answer", 1L, 6L);
        CountDownLatch start = new CountDownLatch(1);
        Callable<ForkAttemptDTO> action = () -> {
            start.await();
            return forkService.create(ROOT, 1L, "alice", request);
        };
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ForkAttemptDTO> first = executor.submit(action);
            Future<ForkAttemptDTO> second = executor.submit(action);
            start.countDown();

            ForkAttemptDTO firstResult = first.get();
            ForkAttemptDTO secondResult = second.get();

            assertThat(secondResult.getBranchId()).isEqualTo(firstResult.getBranchId());
            assertThat(count("SELECT count(*) FROM t_interview_session WHERE parent_session_id = '"
                    + ROOT + "'"))
                    .isEqualTo(1);
            assertThat(count("SELECT count(*) FROM t_interview_turn_attempt WHERE id = 'fork-concurrent'"))
                    .isEqualTo(1);
            verify(worker).schedule("fork-concurrent");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void ancestorAndNestedFocusedForksCannotDeadlockOnOppositeBranchLineageLocks()
            throws Exception {
        insertNestedFocusedBranch();
        CountDownLatch rootHistoryEntered = new CountDownLatch(1);
        CountDownLatch releaseRootHistory = new CountDownLatch(1);
        when(historyService.getBranchTranscript(ROOT, 1L)).thenAnswer(invocation -> {
            rootHistoryEntered.countDown();
            if (!releaseRootHistory.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("root history test latch timed out");
            }
            return rootTranscript();
        });
        BranchTranscriptDTO nestedTranscript = rootTranscript();
        nestedTranscript.setBranchId("nested-focused");
        when(historyService.getBranchTranscript("nested-focused", 1L))
                .thenReturn(nestedTranscript);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ForkAttemptDTO> rootFork = executor.submit(() -> forkService.create(
                    ROOT,
                    1L,
                    "alice",
                    request("fork-root-first", "root answer", 1L, 6L)));
            assertThat(rootHistoryEntered.await(5, TimeUnit.SECONDS)).isTrue();

            Future<ForkAttemptDTO> nestedFork = executor.submit(() -> forkService.create(
                    "nested-focused",
                    1L,
                    "alice",
                    request("fork-nested-second", "nested answer", 1L, 6L)));
            awaitDatabaseLockWait();
            releaseRootHistory.countDown();

            assertThat(rootFork.get(5, TimeUnit.SECONDS).getAttempt().getStatus())
                    .isEqualTo("PROCESSING");
            assertThatThrownBy(() -> nestedFork.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(TurnAttemptConflictException.class)
                    .hasRootCauseMessage("LINEAGE_PROCESSING_CONFLICT:fork-root-first");
        } finally {
            releaseRootHistory.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void forkAndInFlightCommitUseOneLineageFirstLockOrderWithoutDeadlock() throws Exception {
        insertNestedFocusedBranch();
        BranchTranscriptDTO nestedTranscript = rootTranscript();
        nestedTranscript.setBranchId("nested-focused");
        when(historyService.getBranchTranscript("nested-focused", 1L))
                .thenReturn(nestedTranscript);

        CreateTurnAttemptRequest commitRequest = new CreateTurnAttemptRequest();
        commitRequest.setTurnId("commit-in-flight");
        commitRequest.setCandidateAnswer("commit answer");
        commitRequest.setExpectedBranchVersion(1L);
        commitRequest.setExpectedTailMessageId(6L);
        turnAttemptService.create(ROOT, 1L, "alice", commitRequest);

        CountDownLatch commitReachedComposition = new CountDownLatch(1);
        CountDownLatch releaseCommit = new CountDownLatch(1);
        when(composedAssessmentService.compose(ROOT, 1L)).thenAnswer(invocation -> {
            commitReachedComposition.countDown();
            if (!releaseCommit.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("commit test latch timed out");
            }
            return List.of();
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> commit = executor.submit(() ->
                    commitService.commit("commit-in-flight", result("commit next", 91)));
            assertThat(commitReachedComposition.await(5, TimeUnit.SECONDS)).isTrue();

            Future<ForkAttemptDTO> fork = executor.submit(() -> forkService.create(
                    "nested-focused",
                    1L,
                    "alice",
                    request("fork-during-commit", "fork answer", 1L, 6L)));
            awaitDatabaseLockWait();

            releaseCommit.countDown();
            commit.get(5, TimeUnit.SECONDS);
            ForkAttemptDTO forkResult = fork.get(5, TimeUnit.SECONDS);

            assertThat(forkResult.getAttempt().getStatus()).isEqualTo("PROCESSING");
            assertThat(count("SELECT count(*) FROM t_interview_turn_attempt "
                    + "WHERE id IN ('commit-in-flight', 'fork-during-commit')"))
                    .isEqualTo(2);
        } finally {
            releaseCommit.countDown();
            executor.shutdownNow();
        }
    }

    private void insertNestedFocusedBranch() {
        jdbc.update("""
                INSERT INTO t_interview_session(
                    id, user_id, candidate_name, stage, status, resume_content,
                    job_requirements, project_questions_count, target_project_questions,
                    project_questions_pool, technical_questions_pool,
                    current_followup_count, lineage_id, parent_session_id,
                    fork_point_message_id, fork_trigger_message_id, branch_label,
                    branch_version, last_business_activity_at, legacy_migrated,
                    created_at, updated_at
                ) VALUES (
                    'nested-focused', 1, 'Candidate', 'technical_qna', 1,
                    'resume', 'job', 2, 4, '[]'::jsonb, '[]'::jsonb, 0,
                    ?, ?, 6, 6, '分支 1', 1, CURRENT_TIMESTAMP, FALSE,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, ROOT, ROOT);
    }

    private void awaitDatabaseLockWait() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline && jdbc.queryForObject("""
                SELECT count(*)
                FROM pg_stat_activity
                WHERE datname = current_database()
                  AND wait_event_type = 'Lock'
                """, Long.class) == 0L) {
            Thread.sleep(20);
        }
    }

    @Test
    void deniesCrossUserForkBeforeCreatingChild() {
        jdbc.update("INSERT INTO t_user(id) VALUES (2)");
        long baseline = count("SELECT count(*) FROM t_interview_session");

        assertThatThrownBy(() -> forkService.create(
                        ROOT, 2L, null, request("fork-foreign", "answer", 1L, 6L)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(2003);
        assertThat(count("SELECT count(*) FROM t_interview_session")).isEqualTo(baseline);
    }

    private BranchTranscriptDTO rootTranscript() {
        BranchMessageDTO prompt = new BranchMessageDTO();
        prompt.setId(6L);
        prompt.setOwningBranchId(ROOT);
        prompt.setRole("ai");
        prompt.setMessageType("ai_question");
        prompt.setDeliveryStatus("completed");
        prompt.setExpectsResponse(true);
        prompt.setForkable(true);
        prompt.setForkPointMessageId(6L);
        prompt.setCreatedAt(LocalDateTime.of(2026, 7, 17, 3, 38, 43));
        prompt.setMetadata(Map.of(
                "id", "rich-question",
                "media", Map.of("type", "diagram"),
                "_postTurnStateV1", Map.of(
                        "schemaVersion", 1,
                        "currentStage", "technical_qna",
                        "branchStatus", 1,
                        "projectQuestionsCount", 2,
                        "targetProjectQuestions", 4,
                        "currentFollowupCount", 1,
                        "projectQuestionsPool", List.of(Map.of(
                                "id", "p-next",
                                "context", Map.of("difficulty", "senior"))),
                        "technicalQuestionsPool", List.of(Map.of("id", "t1")))));
        BranchTranscriptDTO transcript = new BranchTranscriptDTO();
        transcript.setLineageId(ROOT);
        transcript.setBranchId(ROOT);
        transcript.setBranchVersion(1L);
        transcript.setStatus(1);
        transcript.setMessages(List.of(prompt));
        return transcript;
    }

    private CreateForkAttemptRequest request(
            String turnId,
            String answer,
            Long version,
            Long tail) {
        CreateForkAttemptRequest request = new CreateForkAttemptRequest();
        request.setTurnId(turnId);
        request.setTriggerMessageId(6L);
        request.setCandidateAnswer(answer);
        request.setExpectedFocusedBranchVersion(version);
        request.setExpectedFocusedTailMessageId(tail);
        return request;
    }

    private TurnModelResult result(String aiMessage, int score) {
        return new TurnModelResult(
                aiMessage,
                "technical_qna",
                false,
                score,
                "feedback",
                Map.of("id", "child-next", "media", Map.of("type", "code")),
                "child-python",
                new AuthoritativeTurnState(
                        "technical_qna",
                        1,
                        2,
                        4,
                        0,
                        List.of("p-next"),
                        List.of()));
    }

    private String sourceFingerprint() {
        return query("""
                SELECT session.status || '|' || session.branch_version || '|'
                    || session.last_business_activity_at || '|'
                    || (SELECT count(*) FROM t_interview_message WHERE session_id = session.id) || '|'
                    || (SELECT count(*) FROM t_score_record WHERE session_id = session.id) || '|'
                    || (SELECT count(*) FROM t_evaluation WHERE session_id = session.id)
                FROM t_interview_session session WHERE session.id = '%s'
                """.formatted(ROOT));
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private String query(String sql) {
        return jdbc.queryForObject(sql, String.class);
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
        TurnAttemptRepository.class,
        ForkBranchRepository.class,
        TurnAttemptEventPublisher.class,
        TurnAttemptService.class,
        ForkAttemptService.class,
        TurnCommitService.class
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
    }
}

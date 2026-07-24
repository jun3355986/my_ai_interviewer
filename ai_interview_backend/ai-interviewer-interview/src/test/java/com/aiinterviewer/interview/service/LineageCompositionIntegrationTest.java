package com.aiinterviewer.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.interview.dto.BranchTranscriptDTO;
import com.aiinterviewer.interview.dto.ComposedAssessmentDTO;
import com.aiinterviewer.interview.dto.LineageTreeDTO;
import com.aiinterviewer.interview.entity.InterviewTurnAttempt;
import com.aiinterviewer.interview.mapper.InterviewLineageMapper;
import com.aiinterviewer.interview.mapper.InterviewMessageMapper;
import com.aiinterviewer.interview.mapper.InterviewSessionMapper;
import com.aiinterviewer.interview.mapper.ScoreRecordMapper;
import com.aiinterviewer.interview.model.BranchSnapshot;
import com.aiinterviewer.interview.repository.TurnAttemptRepository;
import com.aiinterviewer.interview.repository.LineageTreeRepository;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class LineageCompositionIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = dataSource();
        jdbc = new JdbcTemplate(dataSource);
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
        seedNestedLineage();
    }

    @Test
    void nestedTranscriptSnapshotAndAssessmentsShareAncestorBoundarySemantics() throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new ClassPathResource("mapper/InterviewLineageMapper.xml"));
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(InterviewSessionMapper.class);
        configuration.addMapper(InterviewMessageMapper.class);
        configuration.addMapper(ScoreRecordMapper.class);
        configuration.addMapper(InterviewLineageMapper.class);
        factoryBean.setConfiguration(configuration);
        SqlSessionFactory factory = factoryBean.getObject();

        try (SqlSession session = factory.openSession(true)) {
            InterviewSessionMapper sessionMapper = session.getMapper(InterviewSessionMapper.class);
            InterviewMessageMapper messageMapper = session.getMapper(InterviewMessageMapper.class);
            ScoreRecordMapper scoreMapper = session.getMapper(ScoreRecordMapper.class);
            InterviewLineageMapper lineageMapper = session.getMapper(InterviewLineageMapper.class);
            InterviewHistoryService history = new InterviewHistoryService(
                    sessionMapper,
                    messageMapper,
                    lineageMapper);
            ComposedAssessmentService assessmentService = new ComposedAssessmentService(
                    history,
                    scoreMapper);
            TurnAttemptRepository attemptRepository = new TurnAttemptRepository(jdbc);
            BranchSnapshotComposer snapshotComposer = new BranchSnapshotComposer(
                    history,
                    sessionMapper,
                    scoreMapper,
                    attemptRepository);
            LineageTreeService treeService = new LineageTreeService(
                    new LineageTreeRepository(jdbc),
                    assessmentService);

            BranchTranscriptDTO transcript = history.getBranchTranscript("nested", 1L);
            List<ComposedAssessmentDTO> assessments = assessmentService.compose("nested", 1L);
            InterviewTurnAttempt attempt = attemptRepository.findById("nested-next").orElseThrow();
            BranchSnapshot snapshot = snapshotComposer.compose(attempt, 1L, "alice");
            LineageTreeDTO tree = treeService.getTree("lineage-fresh", 1L);

            assertThat(transcript.getMessages())
                    .extracting(message -> message.getContent())
                    .containsExactly(
                            "root-q1",
                            "root-a1",
                            "ancestor-fork-point",
                            "nested-new-answer",
                            "nested-new-question");
            assertThat(transcript.getMessages())
                    .extracting(message -> message.getOwningBranchId())
                    .containsExactly("root-fresh", "root-fresh", "root-fresh", "nested", "nested");
            assertThat(assessments).extracting(ComposedAssessmentDTO::getScore)
                    .containsExactly(80, 95);
            assertThat(assessments).extracting(ComposedAssessmentDTO::getOwningBranchId)
                    .containsExactly("root-fresh", "nested");
            assertThat(assessments).extracting(ComposedAssessmentDTO::getInherited)
                    .containsExactly(true, false);
            assertThat(snapshot.messages()).extracting(message -> message.content())
                    .containsExactlyElementsOf(transcript.getMessages().stream()
                            .map(message -> message.getContent())
                            .toList());
            assertThat(snapshot.assessments()).extracting(assessment -> assessment.score())
                    .containsExactly(80, 95);
            assertThat(snapshot.expectedTailMessageId())
                    .isEqualTo(transcript.getMessages().getLast().getId());
            assertThat(tree.getRootBranchId()).isEqualTo("root-fresh");
            assertThat(tree.getFocusedBranchId()).isEqualTo("nested");
            assertThat(tree.getNodes()).extracting(node -> node.getBranchId())
                    .containsExactly("root-fresh", "first-child", "nested");
            assertThat(tree.getNodes().getLast().getInheritedAssessmentCount()).isEqualTo(1);
            assertThat(tree.getNodes().getLast().getOwnedAssessmentCount()).isEqualTo(1);
            assertThat(tree.getNodes().getLast().getRecoverableTurnId()).isEqualTo("nested-next");
        }
    }

    @Test
    void reassignedLineageDoesNotExposePreviousOwnersEvaluationOrAttempt() throws Exception {
        jdbc.update("INSERT INTO t_user(id) VALUES (2)");
        jdbc.update("UPDATE t_interview_lineage SET user_id = 2 WHERE id = 'lineage-fresh'");
        jdbc.update("UPDATE t_interview_session SET user_id = 2 WHERE lineage_id = 'lineage-fresh'");

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new ClassPathResource("mapper/InterviewLineageMapper.xml"));
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(InterviewSessionMapper.class);
        configuration.addMapper(InterviewMessageMapper.class);
        configuration.addMapper(ScoreRecordMapper.class);
        configuration.addMapper(InterviewLineageMapper.class);
        factoryBean.setConfiguration(configuration);

        try (SqlSession session = factoryBean.getObject().openSession(true)) {
            InterviewHistoryService history = new InterviewHistoryService(
                    session.getMapper(InterviewSessionMapper.class),
                    session.getMapper(InterviewMessageMapper.class),
                    session.getMapper(InterviewLineageMapper.class));
            ComposedAssessmentService assessmentService = new ComposedAssessmentService(
                    history,
                    session.getMapper(ScoreRecordMapper.class));
            LineageTreeService treeService = new LineageTreeService(
                    new LineageTreeRepository(jdbc),
                    assessmentService);

            LineageTreeDTO tree = treeService.getTree("lineage-fresh", 2L);

            assertThat(tree.getNodes().getFirst().getEvaluationSummary()).isNull();
            assertThat(tree.getNodes().getFirst().getCompletedScore()).isEqualTo(70);
            assertThat(tree.getNodes().getLast().getRecoverableTurnId()).isNull();
        }
    }

    @Test
    void partialLineageReassignmentDoesNotExposeOldRootThroughTree() throws Exception {
        jdbc.update("INSERT INTO t_user(id) VALUES (2)");
        jdbc.update("UPDATE t_interview_lineage SET user_id = 2 WHERE id = 'lineage-fresh'");

        try (SqlSession session = openSqlSession()) {
            InterviewHistoryService history = new InterviewHistoryService(
                    session.getMapper(InterviewSessionMapper.class),
                    session.getMapper(InterviewMessageMapper.class),
                    session.getMapper(InterviewLineageMapper.class));
            ComposedAssessmentService assessmentService = new ComposedAssessmentService(
                    history,
                    session.getMapper(ScoreRecordMapper.class));
            LineageTreeService treeService = new LineageTreeService(
                    new LineageTreeRepository(jdbc),
                    assessmentService);

            for (Long userId : List.of(1L, 2L)) {
                assertThatThrownBy(() -> treeService.getTree("lineage-fresh", userId))
                        .isInstanceOf(BusinessException.class)
                        .extracting("code")
                        .isEqualTo(2003);
                assertThatThrownBy(() -> history.getBranchTranscript("nested", userId))
                        .isInstanceOf(BusinessException.class)
                        .extracting("code")
                        .isEqualTo(2003);
                assertThatThrownBy(() -> assessmentService.compose("nested", userId))
                        .isInstanceOf(BusinessException.class)
                        .extracting("code")
                        .isEqualTo(2003);
            }
        }
    }

    @Test
    void preservesFocusedAndProvablyVisibleAncestorUnlinkedScores() throws Exception {
        jdbc.update("""
                UPDATE t_score_record
                SET question_message_id = NULL,
                    answer_message_id = NULL,
                    question = 'root-q1',
                    answer = 'root-a1'
                WHERE session_id = 'root-fresh' AND question_index = 1
                """);
        jdbc.update("""
                INSERT INTO t_score_record(
                    session_id, question_index, question_type, question, answer,
                    score, feedback, is_followup, created_at
                ) VALUES (
                    'nested', 3, 'technical_qna', 'legacy focused question',
                    'legacy focused answer', 88, 'legacy focused', FALSE,
                    CURRENT_TIMESTAMP
                )
                """);

        try (SqlSession session = openSqlSession()) {
            ComposedAssessmentService assessmentService = assessmentService(session);

            assertThat(assessmentService.compose("nested", 1L))
                    .extracting(ComposedAssessmentDTO::getScore)
                    .containsExactly(80, 95, 88);
        }
    }

    @Test
    void excludesAmbiguousAncestorUnlinkedScoreWhenDuplicatePairCrossesForkCutoff(
            CapturedOutput output) throws Exception {
        jdbc.update("""
                UPDATE t_score_record
                SET question_message_id = NULL,
                    answer_message_id = NULL,
                    question = 'root-q1',
                    answer = 'root-a1'
                WHERE session_id = 'root-fresh' AND question_index = 1
                """);
        message("root-fresh", "ai", "root-q1", 5, "ai_question", true);
        message("root-fresh", "human", "root-a1", 6, "candidate_answer", false);

        try (SqlSession session = openSqlSession()) {
            ComposedAssessmentService assessmentService = assessmentService(session);

            assertThat(assessmentService.compose("nested", 1L))
                    .extracting(ComposedAssessmentDTO::getScore)
                    .containsExactly(95);
        }
        assertThat(output).contains("LEGACY_SCORE_LINK_AMBIGUOUS");
    }

    @Test
    void evaluationGuardRequiresCurrentOwnershipAndACompletedBranch() {
        EvaluationBranchGuard guard = new EvaluationBranchGuard(jdbc);
        TransactionTemplate transaction = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));

        assertThatThrownBy(() -> transaction.executeWithoutResult(
                        ignored -> guard.lockCompletedOwnedBranch("nested", 1L)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(6001);

        transaction.executeWithoutResult(
                ignored -> guard.lockCompletedOwnedBranch("root-fresh", 1L));

        jdbc.update("INSERT INTO t_user(id) VALUES (2)");
        jdbc.update("UPDATE t_interview_session SET user_id = 2 WHERE id = 'root-fresh'");
        assertThatThrownBy(() -> transaction.executeWithoutResult(
                        ignored -> guard.lockCompletedOwnedBranch("root-fresh", 1L)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(2003);
        assertThatThrownBy(() -> transaction.executeWithoutResult(
                        ignored -> guard.lockCompletedOwnedBranch("root-fresh", 2L)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(2003);

        jdbc.update("UPDATE t_interview_lineage SET user_id = 2 WHERE id = 'lineage-fresh'");
        transaction.executeWithoutResult(
                ignored -> guard.lockCompletedOwnedBranch("root-fresh", 2L));
    }

    @Test
    void evaluationGuardHoldsBranchLocksUntilTheReportTransactionFinishes() throws Exception {
        EvaluationBranchGuard guard = new EvaluationBranchGuard(jdbc);
        DataSourceTransactionManager transactionManager =
                new DataSourceTransactionManager(dataSource);
        CountDownLatch guardLocked = new CountDownLatch(1);
        CountDownLatch releaseGuard = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> guardedReport = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(ignored -> {
                        guard.lockCompletedOwnedBranch("root-fresh", 1L);
                        guardLocked.countDown();
                        try {
                            if (!releaseGuard.await(5, TimeUnit.SECONDS)) {
                                throw new AssertionError("Timed out waiting to release evaluation guard");
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(exception);
                        }
                    }));
            Future<?> competingCommit = executor.submit(() -> {
                try {
                    if (!guardLocked.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Evaluation guard was not acquired");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                new TransactionTemplate(transactionManager).executeWithoutResult(ignored ->
                        jdbc.update("UPDATE t_interview_session SET status = 1 "
                                + "WHERE id = 'root-fresh'"));
            });

            assertThatThrownBy(() -> competingCommit.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            releaseGuard.countDown();
            guardedReport.get(5, TimeUnit.SECONDS);
            competingCommit.get(5, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM t_interview_session WHERE id = 'root-fresh'",
                    Integer.class)).isEqualTo(1);
        } finally {
            releaseGuard.countDown();
            executor.shutdownNow();
        }
    }

    private SqlSession openSqlSession() throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new ClassPathResource("mapper/InterviewLineageMapper.xml"));
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(InterviewSessionMapper.class);
        configuration.addMapper(InterviewMessageMapper.class);
        configuration.addMapper(ScoreRecordMapper.class);
        configuration.addMapper(InterviewLineageMapper.class);
        factoryBean.setConfiguration(configuration);
        return factoryBean.getObject().openSession(true);
    }

    private ComposedAssessmentService assessmentService(SqlSession session) {
        InterviewHistoryService history = new InterviewHistoryService(
                session.getMapper(InterviewSessionMapper.class),
                session.getMapper(InterviewMessageMapper.class),
                session.getMapper(InterviewLineageMapper.class));
        return new ComposedAssessmentService(
                history,
                session.getMapper(ScoreRecordMapper.class));
    }

    private void seedNestedLineage() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("SET CONSTRAINTS ALL DEFERRED");
            statement.execute("""
                    INSERT INTO t_interview_lineage(
                        id, user_id, root_session_id, last_business_activity_at,
                        archived, created_at, updated_at
                    ) VALUES (
                        'lineage-fresh', 1, 'root-fresh', CURRENT_TIMESTAMP,
                        FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    INSERT INTO t_interview_session(
                        id, user_id, candidate_name, stage, status, resume_content,
                        job_requirements, project_questions_count, target_project_questions,
                        project_questions_pool, technical_questions_pool,
                        current_followup_count, lineage_id, branch_label, branch_version,
                        last_business_activity_at, legacy_migrated, created_at, updated_at
                    ) VALUES (
                        'root-fresh', 1, 'Candidate', 'project_qna', 1, 'resume',
                        'job', 1, 3, '["p2"]'::jsonb, '[]'::jsonb,
                        0, 'lineage-fresh', '原始分支', 1,
                        CURRENT_TIMESTAMP, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """);
            connection.commit();
        }

        long rootQ1 = message("root-fresh", "ai", "root-q1", 1, "ai_question", true);
        long rootA1 = message("root-fresh", "human", "root-a1", 2, "candidate_answer", false);
        long forkPoint = message(
                "root-fresh", "ai", "ancestor-fork-point", 3, "ai_question", true);
        long rootOldAnswer = message(
                "root-fresh", "human", "excluded-root-answer", 4, "candidate_answer", false);
        score("root-fresh", 1, rootQ1, rootA1, 80);
        score("root-fresh", 2, forkPoint, rootOldAnswer, 60);

        insertBranch("first-child", "root-fresh", forkPoint, forkPoint, 1L);
        long selectedChildAnswer = message(
                "first-child", "human", "selected-child-answer", 1, "candidate_answer", false);
        long childQuestion = message(
                "first-child", "ai", "excluded-child-question", 2, "ai_question", true);
        score("first-child", 2, forkPoint, selectedChildAnswer, 70);

        insertBranch("nested", "first-child", forkPoint, selectedChildAnswer, 2L);
        long nestedAnswer = message(
                "nested", "human", "nested-new-answer", 1, "candidate_answer", false);
        long nestedQuestion = message(
                "nested", "ai", "nested-new-question", 2, "ai_question", true);
        score("nested", 2, forkPoint, nestedAnswer, 95);

        jdbc.update("""
                INSERT INTO t_interview_turn_attempt(
                    id, lineage_id, session_id, owner_user_id, expected_branch_version,
                    expected_tail_message_id, candidate_answer, status
                ) VALUES ('nested-next', 'lineage-fresh', 'nested', 1, 2, ?, 'next', 'PROCESSING')
                """, nestedQuestion);
        jdbc.update("""
                UPDATE t_interview_session
                SET status = 2, stage = 'concluded',
                    last_business_activity_at = TIMESTAMP '2026-07-24 08:00:00'
                WHERE id = 'root-fresh'
                """);
        jdbc.update("""
                UPDATE t_interview_session
                SET last_business_activity_at = TIMESTAMP '2026-07-24 09:00:00'
                WHERE id = 'first-child'
                """);
        jdbc.update("""
                UPDATE t_interview_session
                SET last_business_activity_at = TIMESTAMP '2026-07-24 10:00:00'
                WHERE id = 'nested'
                """);
        jdbc.update("""
                INSERT INTO t_evaluation(session_id, user_id, overall_score, summary)
                VALUES ('root-fresh', 1, 85, 'root independent report')
                """);
    }

    private void insertBranch(
            String id,
            String parent,
            long forkPoint,
            long trigger,
            long version) {
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
                    ?, 1, 'Candidate', 'project_qna', 1, 'resume', 'job',
                    1, 3, '["p2"]'::jsonb, '[]'::jsonb, 0, 'lineage-fresh', ?,
                    ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, id, parent, forkPoint, trigger, id, version);
    }

    private long message(
            String branch,
            String role,
            String content,
            int sequence,
            String type,
            boolean expectsResponse) {
        String metadata = type.equals("ai_question")
                ? """
                  {"id":"%s","_postTurnStateV1":{"schemaVersion":1,"currentStage":"project_qna","branchStatus":1,"projectQuestionsCount":1,"targetProjectQuestions":3,"currentFollowupCount":0,"projectQuestionsPool":["p2"],"technicalQuestionsPool":[]}}
                  """.formatted(content)
                : "{}";
        return jdbc.queryForObject("""
                INSERT INTO t_interview_message(
                    session_id, role, content, stage, sequence, message_type,
                    expects_response, delivery_status, metadata, created_at
                ) VALUES (?, ?, ?, 'project_qna', ?, ?, ?, 'completed', CAST(? AS jsonb), CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class, branch, role, content, sequence, type, expectsResponse, metadata);
    }

    private void score(
            String branch,
            int index,
            long questionId,
            long answerId,
            int value) {
        jdbc.update("""
                INSERT INTO t_score_record(
                    session_id, question_index, question_type, question, answer,
                    score, feedback, is_followup, question_message_id,
                    answer_message_id, created_at
                ) VALUES (?, ?, 'project', 'q', 'a', ?, 'feedback', FALSE, ?, ?, CURRENT_TIMESTAMP)
                """, branch, index, value, questionId, answerId);
    }

    private static DataSource dataSource() {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        return source;
    }
}

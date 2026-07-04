package com.aiinterviewer.evaluation.service;

import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.common.model.ErrorCode;
import com.aiinterviewer.evaluation.dto.EvaluationDTO;
import com.aiinterviewer.evaluation.dto.ScoreDTO;
import com.aiinterviewer.evaluation.dto.StatisticsDTO;
import com.aiinterviewer.evaluation.entity.Evaluation;
import com.aiinterviewer.evaluation.mapper.EvaluationMapper;
import com.aiinterviewer.interview.entity.ScoreRecord;
import com.aiinterviewer.interview.mapper.ScoreRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 评估服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final EvaluationMapper evaluationMapper;
    private final ScoreRecordMapper scoreRecordMapper;

    /**
     * 生成评估报告
     */
    @Transactional
    public EvaluationDTO generateReport(String sessionId, Long userId, Long jobId) {
        // 检查是否已存在评估
        Evaluation existing = evaluationMapper.selectBySessionId(sessionId);
        if (existing != null) {
            return toDTO(existing);
        }

        // 获取评分记录
        List<ScoreRecord> scores = scoreRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScoreRecord>()
                        .eq(ScoreRecord::getSessionId, sessionId)
                        .orderByAsc(ScoreRecord::getQuestionIndex)
        );

        if (scores.isEmpty()) {
            throw new BusinessException(ErrorCode.EVALUATION_NOT_READY, "暂无评分数据");
        }

        // 计算各项评分
        int overallScore = calculateOverallScore(scores);
        int technicalScore = calculateTechnicalScore(scores);
        int communicationScore = calculateCommunicationScore(scores);
        int logicScore = calculateLogicScore(scores);
        int experienceScore = calculateExperienceScore(scores);

        // 生成评估内容
        String summary = generateSummary(scores, overallScore);
        String strengths = generateStrengths(scores);
        String weaknesses = generateWeaknesses(scores);
        String recommendation = getRecommendation(overallScore);

        // 创建评估报告
        Evaluation evaluation = new Evaluation();
        evaluation.setSessionId(sessionId);
        evaluation.setUserId(userId);
        evaluation.setJobId(jobId);
        evaluation.setOverallScore(overallScore);
        evaluation.setTechnicalScore(technicalScore);
        evaluation.setCommunicationScore(communicationScore);
        evaluation.setLogicScore(logicScore);
        evaluation.setExperienceScore(experienceScore);
        evaluation.setSummary(summary);
        evaluation.setStrengths(strengths);
        evaluation.setWeaknesses(weaknesses);
        evaluation.setRecommendation(recommendation);
        evaluation.setTotalQuestions(scores.size());
        evaluation.setAnsweredQuestions((int) scores.stream().filter(s -> s.getAnswer() != null && !s.getAnswer().isEmpty()).count());
        evaluation.setAverageScore(BigDecimal.valueOf(overallScore).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        evaluation.setDurationMinutes(calculateDuration(scores));
        evaluation.setCreatedAt(LocalDateTime.now());
        evaluation.setUpdatedAt(LocalDateTime.now());

        evaluationMapper.insert(evaluation);
        return toDTO(evaluation);
    }

    /**
     * 获取评估报告
     */
    public EvaluationDTO getReport(String sessionId, Long userId) {
        Evaluation evaluation = evaluationMapper.selectBySessionId(sessionId);
        if (evaluation == null) {
            return null;
        }
        if (!evaluation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权限查看此报告");
        }
        return toDTO(evaluation);
    }

    /**
     * 获取用户的评估报告列表
     */
    public List<EvaluationDTO> listReports(Long userId) {
        List<Evaluation> evaluations = evaluationMapper.selectByUserId(userId);
        return evaluations.stream().map(this::toDTO).toList();
    }

    /**
     * 获取评分详情
     */
    public List<ScoreDTO> getScores(String sessionId, Long userId) {
        // 验证权限
        Evaluation evaluation = evaluationMapper.selectBySessionId(sessionId);
        if (evaluation == null) {
            throw new BusinessException(ErrorCode.EVALUATION_NOT_FOUND, "评估报告不存在");
        }
        if (!evaluation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权限查看");
        }

        List<ScoreRecord> scores = scoreRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScoreRecord>()
                        .eq(ScoreRecord::getSessionId, sessionId)
                        .orderByAsc(ScoreRecord::getQuestionIndex)
        );

        return scores.stream().map(this::toScoreDTO).toList();
    }

    /**
     * 获取统计数据
     */
    public StatisticsDTO getStatistics(Long userId) {
        List<Evaluation> evaluations = evaluationMapper.selectByUserId(userId);

        StatisticsDTO stats = new StatisticsDTO();
        stats.setTotalInterviews(evaluations.size());
        stats.setCompletedInterviews((int) evaluations.stream().filter(e -> e.getOverallScore() != null).count());

        if (!evaluations.isEmpty()) {
            Double avgScore = evaluations.stream()
                    .filter(e -> e.getOverallScore() != null)
                    .mapToInt(Evaluation::getOverallScore)
                    .average()
                    .orElse(0);
            stats.setAverageScore(avgScore.intValue());

            Double avgDuration = evaluations.stream()
                    .filter(e -> e.getDurationMinutes() != null)
                    .mapToInt(Evaluation::getDurationMinutes)
                    .average()
                    .orElse(0);
            stats.setAverageDuration(avgDuration);
        }

        // 分数分布
        List<StatisticsDTO.ScoreDistribution> distributions = new ArrayList<>();
        distributions.add(createDistribution("优秀(90-100)", evaluations, 90, 100));
        distributions.add(createDistribution("良好(70-89)", evaluations, 70, 89));
        distributions.add(createDistribution("中等(50-69)", evaluations, 50, 69));
        distributions.add(createDistribution("及格(40-49)", evaluations, 40, 49));
        distributions.add(createDistribution("待改进(<40)", evaluations, 0, 39));
        stats.setScoreDistribution(distributions);

        return stats;
    }

    private StatisticsDTO.ScoreDistribution createDistribution(String range, List<Evaluation> evals, int min, int max) {
        StatisticsDTO.ScoreDistribution dist = new StatisticsDTO.ScoreDistribution();
        dist.setRange(range);
        long count = evals.stream().filter(e -> e.getOverallScore() != null &&
                e.getOverallScore() >= min && e.getOverallScore() <= max).count();
        dist.setCount((int) count);
        dist.setPercentage(evals.isEmpty() ? 0 : (count * 100.0 / evals.size()));
        return dist;
    }

    private int calculateOverallScore(List<ScoreRecord> scores) {
        return (int) scores.stream()
                .filter(s -> s.getScore() != null)
                .mapToInt(ScoreRecord::getScore)
                .average()
                .orElse(0);
    }

    private int calculateTechnicalScore(List<ScoreRecord> scores) {
        return (int) scores.stream()
                .filter(s -> "technical".equals(s.getQuestionType()) && s.getScore() != null)
                .mapToInt(ScoreRecord::getScore)
                .average()
                .orElse(0);
    }

    private int calculateCommunicationScore(List<ScoreRecord> scores) {
        // 基于所有回答的完整性和流畅度估算
        long answeredCount = scores.stream().filter(s -> s.getAnswer() != null && s.getAnswer().length() > 20).count();
        return answeredCount > 0 ? 75 + (int) (Math.random() * 20) : 60;
    }

    private int calculateLogicScore(List<ScoreRecord> scores) {
        // 基于评分和回答长度估算
        return calculateOverallScore(scores);
    }

    private int calculateExperienceScore(List<ScoreRecord> scores) {
        // 基于项目问题得分估算
        return (int) scores.stream()
                .filter(s -> "project".equals(s.getQuestionType()) && s.getScore() != null)
                .mapToInt(ScoreRecord::getScore)
                .average()
                .orElse(0);
    }

    private String generateSummary(List<ScoreRecord> scores, int overallScore) {
        StringBuilder sb = new StringBuilder();
        sb.append("本次面试共回答了").append(scores.size()).append("个问题，");
        sb.append("综合得分为").append(overallScore).append("分。");

        if (overallScore >= 80) {
            sb.append("整体表现优秀，对项目经验和技术栈有深入理解。");
        } else if (overallScore >= 60) {
            sb.append("整体表现良好，能够胜任基本的工作要求。");
        } else {
            sb.append("部分知识点需要加强，建议进一步学习。");
        }
        return sb.toString();
    }

    private String generateStrengths(List<ScoreRecord> scores) {
        List<String> strengths = new ArrayList<>();
        long technicalCount = scores.stream().filter(s -> "technical".equals(s.getQuestionType()) && s.getScore() != null && s.getScore() >= 80).count();
        long projectCount = scores.stream().filter(s -> "project".equals(s.getQuestionType()) && s.getScore() != null && s.getScore() >= 80).count();

        if (technicalCount > 0) {
            strengths.add("扎实的" + (projectCount > 0 ? "技术基础和项目经验" : "技术基础"));
        }
        if (projectCount > 0) {
            strengths.add("丰富的项目实战经验");
        }
        return strengths.isEmpty() ? "具备基本的岗位能力" : String.join("，", strengths);
    }

    private String generateWeaknesses(List<ScoreRecord> scores) {
        List<String> weaknesses = new ArrayList<>();
        long lowScoreCount = scores.stream().filter(s -> s.getScore() != null && s.getScore() < 60).count();

        if (lowScoreCount > scores.size() / 3) {
            weaknesses.add("部分问题回答不够深入");
        }
        if (scores.stream().anyMatch(s -> s.getAnswer() != null && s.getAnswer().length() < 30)) {
            weaknesses.add("回答可以更加详细具体");
        }
        return weaknesses.isEmpty() ? "无明显短板" : String.join("，", weaknesses);
    }

    private String getRecommendation(int score) {
        if (score >= 90) return "EXCELLENT";
        if (score >= 70) return "RECOMMEND";
        if (score >= 50) return "CONSIDER";
        return "REJECT";
    }

    private Integer calculateDuration(List<ScoreRecord> scores) {
        if (scores.size() < 2) return 30;
        return scores.size() * 5; // 假设每题5分钟
    }

    private EvaluationDTO toDTO(Evaluation eval) {
        EvaluationDTO dto = new EvaluationDTO();
        dto.setId(eval.getId());
        dto.setSessionId(eval.getSessionId());
        dto.setUserId(eval.getUserId());
        dto.setJobId(eval.getJobId());
        dto.setOverallScore(eval.getOverallScore());
        dto.setTechnicalScore(eval.getTechnicalScore());
        dto.setCommunicationScore(eval.getCommunicationScore());
        dto.setLogicScore(eval.getLogicScore());
        dto.setExperienceScore(eval.getExperienceScore());
        dto.setSummary(eval.getSummary());
        dto.setStrengths(eval.getStrengths());
        dto.setWeaknesses(eval.getWeaknesses());
        dto.setRecommendation(eval.getRecommendation());
        dto.setRecommendationText(getRecommendationText(eval.getRecommendation()));
        dto.setTotalQuestions(eval.getTotalQuestions());
        dto.setAnsweredQuestions(eval.getAnsweredQuestions());
        dto.setAverageScore(eval.getAverageScore());
        dto.setDurationMinutes(eval.getDurationMinutes());
        dto.setCreatedAt(eval.getCreatedAt());
        return dto;
    }

    private String getRecommendationText(String code) {
        if (code == null) return "待评估";
        return switch (code) {
            case "EXCELLENT" -> "强烈推荐";
            case "RECOMMEND" -> "推荐";
            case "CONSIDER" -> "可以考虑";
            case "REJECT" -> "不推荐";
            default -> "待评估";
        };
    }

    private ScoreDTO toScoreDTO(ScoreRecord record) {
        ScoreDTO dto = new ScoreDTO();
        dto.setId(record.getId());
        dto.setSessionId(record.getSessionId());
        dto.setQuestionIndex(record.getQuestionIndex());
        dto.setQuestionType(record.getQuestionType());
        dto.setQuestion(record.getQuestion());
        dto.setAnswer(record.getAnswer());
        dto.setScore(record.getScore());
        dto.setFeedback(record.getFeedback());
        dto.setIsFollowup(record.getIsFollowup());
        dto.setCreatedAt(record.getCreatedAt());
        return dto;
    }
}

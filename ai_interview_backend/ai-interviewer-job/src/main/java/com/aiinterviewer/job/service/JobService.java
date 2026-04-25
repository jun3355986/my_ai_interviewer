package com.aiinterviewer.job.service;

import com.aiinterviewer.common.exception.BusinessException;
import com.aiinterviewer.common.model.ErrorCode;
import com.aiinterviewer.job.dto.*;
import com.aiinterviewer.job.entity.Job;
import com.aiinterviewer.job.entity.JobRequirement;
import com.aiinterviewer.job.mapper.JobMapper;
import com.aiinterviewer.job.mapper.JobRequirementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 职位服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobMapper jobMapper;
    private final JobRequirementMapper requirementMapper;

    /**
     * 创建职位
     */
    @Transactional
    public JobDTO createJob(Long userId, JobRequest request) {
        Job job = new Job();
        job.setTitle(request.getTitle());
        job.setCompany(request.getCompany());
        job.setDepartment(request.getDepartment());
        job.setLocation(request.getLocation());
        job.setJobType(request.getJobType());
        job.setExperienceRequired(request.getExperienceRequired());
        job.setEducationRequired(request.getEducationRequired());
        job.setSalaryMin(request.getSalaryMin());
        job.setSalaryMax(request.getSalaryMax());
        job.setDescription(request.getDescription());
        job.setRequirements(request.getRequirements());
        job.setSkills(request.getSkills());
        job.setStatus(1); // 招聘中
        job.setCreatedBy(userId);
        job.setPublishedAt(LocalDateTime.now());
        job.setDeadline(request.getDeadline());
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());

        jobMapper.insert(job);
        return toDTO(job);
    }

    /**
     * 更新职位
     */
    @Transactional
    public JobDTO updateJob(Long jobId, Long userId, JobRequest request) {
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND, "职位不存在");
        }
        if (!job.getCreatedBy().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权限操作此职位");
        }

        job.setTitle(request.getTitle());
        job.setCompany(request.getCompany());
        job.setDepartment(request.getDepartment());
        job.setLocation(request.getLocation());
        job.setJobType(request.getJobType());
        job.setExperienceRequired(request.getExperienceRequired());
        job.setEducationRequired(request.getEducationRequired());
        job.setSalaryMin(request.getSalaryMin());
        job.setSalaryMax(request.getSalaryMax());
        job.setDescription(request.getDescription());
        job.setRequirements(request.getRequirements());
        job.setSkills(request.getSkills());
        job.setDeadline(request.getDeadline());
        job.setUpdatedAt(LocalDateTime.now());

        jobMapper.updateById(job);
        return toDTO(job);
    }

    /**
     * 获取职位详情
     */
    public JobDTO getJob(Long jobId) {
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND, "职位不存在");
        }
        return toDTO(job);
    }

    /**
     * 获取职位列表
     */
    public List<JobDTO> listJobs() {
        List<Job> jobs = jobMapper.selectActiveJobs();
        return jobs.stream().map(this::toDTO).toList();
    }

    /**
     * 搜索职位
     */
    public List<JobDTO> searchJobs(String keyword) {
        List<Job> jobs = jobMapper.searchByKeyword(keyword);
        return jobs.stream().map(this::toDTO).toList();
    }

    /**
     * 获取用户创建的职位
     */
    public List<JobDTO> listJobsByUser(Long userId) {
        List<Job> jobs = jobMapper.selectByCreator(userId);
        return jobs.stream().map(this::toDTO).toList();
    }

    /**
     * 关闭职位
     */
    @Transactional
    public void closeJob(Long jobId, Long userId) {
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND, "职位不存在");
        }
        if (!job.getCreatedBy().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权限操作此职位");
        }

        job.setStatus(0);
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateById(job);
    }

    /**
     * 删除职位
     */
    @Transactional
    public void deleteJob(Long jobId, Long userId) {
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND, "职位不存在");
        }
        if (!job.getCreatedBy().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权限操作此职位");
        }

        // 删除关联的要求
        requirementMapper.deleteBatchIds(requirementMapper.selectByJobId(jobId).stream()
                .map(JobRequirement::getId).toList());

        jobMapper.deleteById(jobId);
    }

    /**
     * 职位-简历匹配度分析
     */
    public MatchAnalysisResponse analyzeMatch(Long jobId, String resumeContent) {
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND, "职位不存在");
        }

        MatchAnalysisResponse response = new MatchAnalysisResponse();
        List<MatchAnalysisResponse.MatchItem> details = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        // 分析技能匹配
        if (job.getSkills() != null && !job.getSkills().isEmpty()) {
            int skillMatch = analyzeSkillMatch(job.getSkills(), resumeContent, details, suggestions);
            // 技能权重40%
            addWeightedScore(response, details, "SKILL", skillMatch, 40);
        }

        // 分析经验匹配
        int expMatch = analyzeExperienceMatch(job.getExperienceRequired(), resumeContent, details, suggestions);
        addWeightedScore(response, details, "EXPERIENCE", expMatch, 30);

        // 分析学历匹配
        int eduMatch = analyzeEducationMatch(job.getEducationRequired(), resumeContent, details, suggestions);
        addWeightedScore(response, details, "EDUCATION", eduMatch, 20);

        // 综合评分
        int totalScore = details.stream()
                .mapToInt(MatchAnalysisResponse.MatchItem::getScore)
                .sum();
        response.setMatchScore(BigDecimal.valueOf(totalScore).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        response.setMatchDetails(details);
        response.setSuggestions(suggestions);
        response.setMatchLevel(calculateMatchLevel(response.getMatchScore()));

        return response;
    }

    private int analyzeSkillMatch(List<String> requiredSkills, String resumeContent,
                                   List<MatchAnalysisResponse.MatchItem> details,
                                   List<String> suggestions) {
        int matchCount = 0;
        for (String skill : requiredSkills) {
            boolean matched = resumeContent.toLowerCase().contains(skill.toLowerCase());
            MatchAnalysisResponse.MatchItem item = new MatchAnalysisResponse.MatchItem();
            item.setCategory("SKILL");
            item.setName(skill);
            item.setMatched(matched);
            if (matched) {
                matchCount++;
            } else {
                suggestions.add("建议补充 " + skill + " 相关经验");
            }
            details.add(item);
        }
        return requiredSkills.isEmpty() ? 100 : (matchCount * 100) / requiredSkills.size();
    }

    private int analyzeExperienceMatch(String requiredExp, String resumeContent,
                                        List<MatchAnalysisResponse.MatchItem> details,
                                        List<String> suggestions) {
        // 简单匹配: 检查是否提到工作年限
        if (requiredExp == null || requiredExp.isEmpty()) {
            return 100;
        }

        boolean matched = resumeContent.toLowerCase().contains("年") ||
                resumeContent.toLowerCase().contains("经验");
        MatchAnalysisResponse.MatchItem item = new MatchAnalysisResponse.MatchItem();
        item.setCategory("EXPERIENCE");
        item.setName("经验要求");
        item.setMatched(matched);
        item.setResumeValue(requiredExp);
        details.add(item);

        return matched ? 80 : 40;
    }

    private int analyzeEducationMatch(String requiredEdu, String resumeContent,
                                       List<MatchAnalysisResponse.MatchItem> details,
                                       List<String> suggestions) {
        if (requiredEdu == null || requiredEdu.isEmpty()) {
            return 100;
        }

        boolean matched = resumeContent.toLowerCase().contains("本科") ||
                resumeContent.toLowerCase().contains("硕士") ||
                resumeContent.toLowerCase().contains("博士") ||
                resumeContent.toLowerCase().contains("学历");
        MatchAnalysisResponse.MatchItem item = new MatchAnalysisResponse.MatchItem();
        item.setCategory("EDUCATION");
        item.setName("学历要求");
        item.setMatched(matched);
        item.setResumeValue(requiredEdu);
        details.add(item);

        return matched ? 90 : 50;
    }

    private void addWeightedScore(MatchAnalysisResponse response, List<MatchAnalysisResponse.MatchItem> details,
                                   String category, int score, int weight) {
        // 计算加权分数并累加到details中的各item
        for (MatchAnalysisResponse.MatchItem item : details) {
            if (item.getCategory().equals(category)) {
                item.setScore(score * weight / 100);
            }
        }
    }

    private String calculateMatchLevel(BigDecimal score) {
        double s = score.doubleValue();
        if (s >= 80) return "EXCELLENT";
        if (s >= 60) return "GOOD";
        if (s >= 40) return "MATCHED";
        if (s >= 20) return "PARTIAL";
        return "MISMATCHED";
    }

    private JobDTO toDTO(Job job) {
        JobDTO dto = new JobDTO();
        dto.setId(job.getId());
        dto.setTitle(job.getTitle());
        dto.setCompany(job.getCompany());
        dto.setDepartment(job.getDepartment());
        dto.setLocation(job.getLocation());
        dto.setJobType(job.getJobType());
        dto.setExperienceRequired(job.getExperienceRequired());
        dto.setEducationRequired(job.getEducationRequired());
        dto.setSalaryMin(job.getSalaryMin());
        dto.setSalaryMax(job.getSalaryMax());
        dto.setSalaryDisplay(formatSalary(job.getSalaryMin(), job.getSalaryMax()));
        dto.setDescription(job.getDescription());
        dto.setRequirements(job.getRequirements());
        dto.setSkills(job.getSkills());
        dto.setStatus(job.getStatus());
        dto.setStatusText(job.getStatus() == 1 ? "招聘中" : "已关闭");
        dto.setPublishedAt(job.getPublishedAt());
        dto.setDeadline(job.getDeadline());
        dto.setCreatedAt(job.getCreatedAt());
        return dto;
    }

    private String formatSalary(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) return "面议";
        if (min != null && max == null) return min.intValue() + "K以上";
        if (min == null && max != null) return "最高 " + max.intValue() + "K";
        return min.intValue() + "K-" + max.intValue() + "K";
    }
}

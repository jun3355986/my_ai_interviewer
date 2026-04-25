package com.aiinterviewer.admin.questionbank;

import com.aiinterviewer.admin.common.model.PageResult;
import com.aiinterviewer.admin.common.model.Result;
import com.aiinterviewer.admin.questionbank.dto.QuestionCreateRequest;
import com.aiinterviewer.admin.questionbank.dto.QuestionUpdateRequest;
import com.aiinterviewer.admin.questionbank.entity.QuestionBankItem;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    @PostMapping
    public Result<Long> createQuestion(@RequestBody QuestionCreateRequest request) {
        return Result.success(questionService.createQuestion(request));
    }

    @PutMapping("/{questionId}")
    public Result<Void> updateQuestion(
            @PathVariable Long questionId,
            @RequestBody QuestionUpdateRequest request) {
        questionService.updateQuestion(questionId, request);
        return Result.success();
    }

    @GetMapping
    public Result<PageResult<QuestionBankItem>> listQuestions(
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size) {
        QuestionQuery query = new QuestionQuery();
        query.setQuestionType(questionType);
        query.setDifficulty(difficulty);
        query.setTag(tag);
        query.setStatus(status);
        query.setJobId(jobId);
        query.setKeyword(keyword);
        query.setCurrent(current);
        query.setSize(size);
        return Result.success(questionService.listQuestions(query));
    }

    @GetMapping("/{questionId}")
    public Result<QuestionBankItem> getQuestion(@PathVariable Long questionId) {
        return Result.success(questionService.getQuestion(questionId));
    }

    @DeleteMapping("/{questionId}")
    public Result<Void> deleteQuestion(@PathVariable Long questionId) {
        questionService.deleteQuestion(questionId);
        return Result.success();
    }
}

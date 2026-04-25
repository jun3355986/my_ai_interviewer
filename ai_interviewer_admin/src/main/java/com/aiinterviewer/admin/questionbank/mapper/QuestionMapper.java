package com.aiinterviewer.admin.questionbank.mapper;

import com.aiinterviewer.admin.questionbank.QuestionQuery;
import com.aiinterviewer.admin.questionbank.QuestionService;
import com.aiinterviewer.admin.questionbank.entity.QuestionBankItem;
import com.aiinterviewer.admin.questionbank.entity.QuestionTag;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface QuestionMapper {

    Long countQuestions(@Param("query") QuestionQuery query);

    List<QuestionBankItem> selectQuestions(
            @Param("query") QuestionQuery query,
            @Param("limit") long limit,
            @Param("offset") long offset);

    QuestionBankItem selectQuestionById(@Param("questionId") Long questionId);

    Integer countExistingQuestion(@Param("questionId") Long questionId);

    int insertQuestion(@Param("item") QuestionBankItem item);

    int updateQuestion(@Param("item") QuestionBankItem item);

    int softDeleteQuestion(@Param("questionId") Long questionId);

    QuestionTag selectTagByName(@Param("tagName") String tagName);

    int insertTag(@Param("tag") QuestionTag tag);

    int deleteQuestionTags(@Param("questionId") Long questionId);

    int insertQuestionTag(
            @Param("questionId") Long questionId,
            @Param("tagId") Long tagId);

    List<QuestionService.QuestionTagNameRow> selectTagNamesByQuestionIds(@Param("questionIds") List<Long> questionIds);
}

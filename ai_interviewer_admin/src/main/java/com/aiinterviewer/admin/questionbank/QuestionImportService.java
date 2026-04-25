package com.aiinterviewer.admin.questionbank;

import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import com.aiinterviewer.admin.questionbank.dto.QuestionCreateRequest;
import com.aiinterviewer.admin.questionbank.dto.QuestionImportRow;
import com.aiinterviewer.admin.questionbank.entity.QuestionImportBatch;
import com.aiinterviewer.admin.questionbank.mapper.QuestionMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class QuestionImportService {

    private static final List<String> EXPECTED_HEADERS = List.of(
            "question_text",
            "answer_reference",
            "question_type",
            "difficulty",
            "tags",
            "skill_area",
            "job_id",
            "status");

    private final QuestionMapper questionMapper;
    private final QuestionService questionService;
    private final ObjectProvider<PlatformTransactionManager> transactionManagerProvider;

    public QuestionImportBatch importCsv(String fileName, InputStream inputStream, Long importedBy) {
        if (inputStream == null) {
            throw new AdminBusinessException(400, "导入文件不能为空");
        }
        QuestionImportBatch batch = createBatch(fileName, importedBy);
        List<QuestionImportRow> rows = List.of();
        List<String> errors = new ArrayList<>();
        int successCount = 0;

        try {
            rows = parseCsv(inputStream);
            Set<String> seenQuestionTexts = new LinkedHashSet<>();
            for (QuestionImportRow row : rows) {
                String normalizedQuestionText = normalizeDuplicateKey(row.getQuestionText());
                if (!StringUtils.hasText(normalizedQuestionText)) {
                    errors.add(rowError(row.getRowNumber(), "题目内容不能为空"));
                    continue;
                }
                if (!seenQuestionTexts.add(normalizedQuestionText)) {
                    errors.add(rowError(row.getRowNumber(), "重复题目内容"));
                    continue;
                }
                String rowError = importRow(batch.getId(), row, importedBy);
                if (rowError == null) {
                    successCount++;
                } else {
                    errors.add(rowError);
                }
            }
        } catch (AdminBusinessException ex) {
            errors.add(ex.getMessage());
        }

        int totalCount = rows.size();
        int failedCount = totalCount - successCount;
        String status = resolveStatus(totalCount, successCount, failedCount, errors);
        String errorMessage = errors.isEmpty() ? null : String.join("; ", errors);
        questionMapper.finishImportBatch(batch.getId(), status, totalCount, successCount, failedCount, errorMessage);
        return questionMapper.selectImportBatchById(batch.getId());
    }

    private QuestionImportBatch createBatch(String fileName, Long importedBy) {
        QuestionImportBatch batch = new QuestionImportBatch();
        batch.setBatchNo("QIB-" + UUID.randomUUID().toString().replace("-", ""));
        batch.setFileName(StringUtils.hasText(fileName) ? fileName.trim() : "questions.csv");
        batch.setStatus(QuestionImportBatch.STATUS_PROCESSING);
        batch.setTotalCount(0);
        batch.setSuccessCount(0);
        batch.setFailedCount(0);
        batch.setImportedBy(importedBy);
        int inserted = questionMapper.insertImportBatch(batch);
        if (inserted == 0 || batch.getId() == null) {
            throw new AdminBusinessException(500, "导入批次创建失败");
        }
        return batch;
    }

    private List<QuestionImportRow> parseCsv(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new AdminBusinessException(400, "CSV 文件不能为空");
            }
            List<String> headers = parseCsvLine(headerLine);
            if (!EXPECTED_HEADERS.equals(headers)) {
                throw new AdminBusinessException(400, "CSV 表头不匹配，固定表头为 " + String.join(",", EXPECTED_HEADERS));
            }

            List<QuestionImportRow> rows = new ArrayList<>();
            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                List<String> columns = parseCsvLine(line);
                if (columns.size() != EXPECTED_HEADERS.size()) {
                    throw new AdminBusinessException(400, rowError(rowNumber, "列数不匹配"));
                }
                rows.add(toRow(rowNumber, columns));
            }
            return rows;
        } catch (IOException ex) {
            throw new AdminBusinessException(400, "CSV 文件读取失败");
        }
    }

    private QuestionImportRow toRow(int rowNumber, List<String> columns) {
        QuestionImportRow row = new QuestionImportRow();
        row.setRowNumber(rowNumber);
        row.setQuestionText(trimToNull(columns.get(0)));
        row.setAnswerReference(trimToNull(columns.get(1)));
        row.setQuestionType(trimToNull(columns.get(2)));
        row.setDifficulty(trimToNull(columns.get(3)));
        row.setTags(parseTags(columns.get(4)));
        row.setSkillArea(trimToNull(columns.get(5)));
        row.setJobId(parseLong(rowNumber, "job_id", columns.get(6)));
        row.setStatus(parseInteger(rowNumber, "status", columns.get(7)));
        return row;
    }

    private String importRow(Long batchId, QuestionImportRow row, Long importedBy) {
        PlatformTransactionManager transactionManager = transactionManagerProvider.getIfAvailable();
        if (transactionManager == null) {
            throw new AdminBusinessException(500, "题目导入事务管理器未配置");
        }
        TransactionTemplate rowTransaction = new TransactionTemplate(transactionManager);
        rowTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            rowTransaction.executeWithoutResult(status -> questionService.createImportedQuestion(toCreateRequest(row, importedBy), batchId));
            return null;
        } catch (AdminBusinessException ex) {
            return rowError(row.getRowNumber(), ex.getMessage());
        } catch (RuntimeException ex) {
            return rowError(row.getRowNumber(), "题目导入失败");
        }
    }

    private QuestionCreateRequest toCreateRequest(QuestionImportRow row, Long importedBy) {
        QuestionCreateRequest request = new QuestionCreateRequest();
        request.setQuestionText(row.getQuestionText());
        request.setAnswerReference(row.getAnswerReference());
        request.setQuestionType(row.getQuestionType());
        request.setDifficulty(row.getDifficulty());
        request.setSkillArea(row.getSkillArea());
        request.setJobId(row.getJobId());
        request.setStatus(row.getStatus());
        request.setCreatedBy(importedBy);
        request.setTags(row.getTags());
        return request;
    }

    private List<String> parseCsvLine(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char currentChar = line.charAt(i);
            if (currentChar == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (currentChar == ',' && !inQuotes) {
                columns.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(currentChar);
            }
        }
        columns.add(current.toString().trim());
        return columns;
    }

    private List<String> parseTags(String rawTags) {
        if (!StringUtils.hasText(rawTags)) {
            return List.of();
        }
        Set<String> tags = new LinkedHashSet<>();
        for (String tag : rawTags.split("[;|]")) {
            if (StringUtils.hasText(tag)) {
                tags.add(tag.trim());
            }
        }
        return new ArrayList<>(tags);
    }

    private Long parseLong(int rowNumber, String columnName, String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            return Long.valueOf(rawValue.trim());
        } catch (NumberFormatException ex) {
            throw new AdminBusinessException(400, rowError(rowNumber, columnName + " 必须是数字"));
        }
    }

    private Integer parseInteger(int rowNumber, String columnName, String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            return Integer.valueOf(rawValue.trim());
        } catch (NumberFormatException ex) {
            throw new AdminBusinessException(400, rowError(rowNumber, columnName + " 必须是数字"));
        }
    }

    private String resolveStatus(int totalCount, int successCount, int failedCount, List<String> errors) {
        if (totalCount == 0 || (successCount == 0 && !errors.isEmpty())) {
            return QuestionImportBatch.STATUS_FAILED;
        }
        if (failedCount > 0) {
            return QuestionImportBatch.STATUS_PARTIAL_FAILED;
        }
        return QuestionImportBatch.STATUS_SUCCESS;
    }

    private String normalizeDuplicateKey(String value) {
        return StringUtils.hasText(value) ? value.trim().replaceAll("\\s+", " ").toLowerCase() : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String rowError(int rowNumber, String message) {
        return "第" + rowNumber + "行: " + message;
    }
}

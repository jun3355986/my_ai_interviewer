package com.aiinterviewer.admin.questionbank;

import com.aiinterviewer.admin.common.exception.AdminBusinessException;
import com.aiinterviewer.admin.questionbank.dto.QuestionCreateRequest;
import com.aiinterviewer.admin.questionbank.dto.QuestionImportRow;
import com.aiinterviewer.admin.questionbank.entity.QuestionImportBatch;
import com.aiinterviewer.admin.questionbank.mapper.QuestionMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
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
    private static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
    private static final int MAX_DATA_ROWS = 5000;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 8000;
    private static final String ERROR_TRUNCATION_MARKER = "...(错误信息已截断)";

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
        int totalCount = 0;
        int successCount = 0;

        try {
            CsvParseResult parseResult = parseCsv(readCsvBytes(inputStream));
            rows = parseResult.rows();
            errors.addAll(parseResult.errors());
            totalCount = parseResult.totalCount();
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

        int failedCount = totalCount - successCount;
        String status = resolveStatus(totalCount, successCount, failedCount, errors);
        String errorMessage = errors.isEmpty() ? null : truncateErrorMessage(String.join("; ", errors));
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

    private byte[] readCsvBytes(InputStream inputStream) {
        try {
            byte[] content = inputStream.readNBytes(MAX_UPLOAD_BYTES + 1);
            if (content.length > MAX_UPLOAD_BYTES) {
                throw new AdminBusinessException(400, "CSV 文件不能超过 " + (MAX_UPLOAD_BYTES / 1024 / 1024) + "MB");
            }
            return content;
        } catch (IOException ex) {
            throw new AdminBusinessException(400, "CSV 文件读取失败");
        }
    }

    private CsvParseResult parseCsv(byte[] content) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();
        try (CSVParser parser = format.parse(new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8))) {
            List<CSVRecord> records = parser.getRecords();
            if (records.isEmpty()) {
                throw new AdminBusinessException(400, "CSV 文件不能为空");
            }
            List<String> headers = recordValues(records.get(0));
            if (!headers.isEmpty()) {
                headers.set(0, stripUtf8Bom(headers.get(0)));
            }
            if (!EXPECTED_HEADERS.equals(headers)) {
                throw new AdminBusinessException(400, "CSV 表头不匹配，实际表头为 "
                        + headers + "，固定表头为 " + EXPECTED_HEADERS);
            }

            List<QuestionImportRow> rows = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            int totalCount = 0;
            for (int i = 1; i < records.size(); i++) {
                CSVRecord record = records.get(i);
                List<String> columns = recordValues(record);
                if (isBlankRecord(columns)) {
                    continue;
                }
                totalCount++;
                if (totalCount > MAX_DATA_ROWS) {
                    throw new AdminBusinessException(400, "CSV 数据行不能超过 " + MAX_DATA_ROWS + " 行");
                }
                int rowNumber = Math.toIntExact(record.getRecordNumber());
                if (columns.size() != EXPECTED_HEADERS.size()) {
                    errors.add(rowError(rowNumber, "列数不匹配，实际 " + columns.size()
                            + " 列，期望 " + EXPECTED_HEADERS.size() + " 列"));
                    continue;
                }
                QuestionImportRow row = toRow(rowNumber, columns, errors);
                if (row != null) {
                    rows.add(row);
                }
            }
            if (totalCount == 0) {
                errors.add("CSV 文件没有数据行");
            }
            return new CsvParseResult(totalCount, rows, errors);
        } catch (IOException ex) {
            throw new AdminBusinessException(400, "CSV 文件读取失败");
        } catch (IllegalArgumentException ex) {
            throw new AdminBusinessException(400, "CSV 文件格式无效: " + ex.getMessage());
        }
    }

    private QuestionImportRow toRow(int rowNumber, List<String> columns, List<String> errors) {
        List<String> rowErrors = new ArrayList<>();
        QuestionImportRow row = new QuestionImportRow();
        row.setRowNumber(rowNumber);
        row.setQuestionText(trimToNull(columns.get(0)));
        row.setAnswerReference(trimToNull(columns.get(1)));
        row.setQuestionType(trimToNull(columns.get(2)));
        row.setDifficulty(trimToNull(columns.get(3)));
        row.setTags(parseTags(columns.get(4)));
        row.setSkillArea(trimToNull(columns.get(5)));
        row.setJobId(parseLong(rowNumber, "job_id", columns.get(6), rowErrors));
        row.setStatus(parseInteger(rowNumber, "status", columns.get(7), rowErrors));
        if (!rowErrors.isEmpty()) {
            errors.addAll(rowErrors);
            return null;
        }
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

    private List<String> recordValues(CSVRecord record) {
        List<String> values = new ArrayList<>();
        for (String value : record) {
            values.add(value == null ? null : value.trim());
        }
        return values;
    }

    private boolean isBlankRecord(List<String> columns) {
        return columns.stream().noneMatch(StringUtils::hasText);
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

    private Long parseLong(int rowNumber, String columnName, String rawValue, List<String> errors) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            return Long.valueOf(rawValue.trim());
        } catch (NumberFormatException ex) {
            errors.add(rowError(rowNumber, columnName + " 必须是数字"));
            return null;
        }
    }

    private Integer parseInteger(int rowNumber, String columnName, String rawValue, List<String> errors) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            return Integer.valueOf(rawValue.trim());
        } catch (NumberFormatException ex) {
            errors.add(rowError(rowNumber, columnName + " 必须是数字"));
            return null;
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
        return StringUtils.hasText(value) ? value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT) : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String stripUtf8Bom(String value) {
        if (value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }

    private String truncateErrorMessage(String errorMessage) {
        if (errorMessage.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH - ERROR_TRUNCATION_MARKER.length())
                + ERROR_TRUNCATION_MARKER;
    }

    private String rowError(int rowNumber, String message) {
        return "第" + rowNumber + "行: " + message;
    }

    private record CsvParseResult(int totalCount, List<QuestionImportRow> rows, List<String> errors) {
    }
}

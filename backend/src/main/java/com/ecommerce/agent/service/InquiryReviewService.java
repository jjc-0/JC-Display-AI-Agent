package com.ecommerce.agent.service;

import com.ecommerce.agent.llm.MultiModelOrchestrator;
import com.ecommerce.agent.model.inquiry.*;
import com.ecommerce.agent.model.v2.AgentTask;
import com.ecommerce.agent.rag.FileParserService;
import com.ecommerce.agent.repository.AgentTaskRepository;
import com.ecommerce.agent.repository.inquiry.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryReviewService {

    private final InquiryCaseRepository caseRepo;
    private final InquiryArtifactRepository artifactRepo;
    private final ExtractedRequirementRepository requirementRepo;
    private final MissingFieldRepository missingFieldRepo;
    private final RiskFlagRepository riskFlagRepo;
    private final QuoteTaskDraftRepository quoteTaskDraftRepo;
    private final AgentTaskRepository agentTaskRepo;
    private final FileParserService fileParserService;
    private final MultiModelOrchestrator orchestrator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Map<String, Object>> listCases(String ownerId, boolean admin, String keyword) {
        List<InquiryCase> cases;
        if (keyword != null && !keyword.isBlank()) {
            cases = admin ? caseRepo.searchAll(keyword.trim()) : caseRepo.searchMine(ownerId, keyword.trim());
        } else {
            cases = admin ? caseRepo.findAllByOrderByUpdatedAtDesc() : caseRepo.findByOwnerIdOrderByUpdatedAtDesc(ownerId);
        }
        return cases.stream().map(this::toCaseSummaryMap).toList();
    }

    @Transactional
    public Map<String, Object> createCase(Map<String, Object> body, String ownerId, String ownerName) {
        String title = stringValue(body.get("title"));
        if (title == null || title.isBlank()) {
            title = "未命名询盘";
        }

        InquiryCase inquiryCase = InquiryCase.builder()
                .caseNo(generateCaseNo())
                .title(trimTo(title, 300))
                .customerName(trimTo(stringValue(body.get("customerName")), 300))
                .contactName(trimTo(stringValue(body.get("contactName")), 200))
                .contactEmail(trimTo(stringValue(body.get("contactEmail")), 300))
                .country(trimTo(stringValue(body.get("country")), 80))
                .source(trimTo(stringValue(body.get("source")), 80))
                .status("DRAFT")
                .ownerId(ownerId != null && !ownerId.isBlank() ? ownerId : "user")
                .ownerName(ownerName)
                .build();

        inquiryCase = caseRepo.save(inquiryCase);

        String emailText = stringValue(body.get("emailText"));
        if (emailText != null && !emailText.isBlank()) {
            addTextArtifact(inquiryCase.getId(), "客户邮件正文", "EMAIL_TEXT", "PASTE", emailText);
        }

        return getCaseDetail(inquiryCase.getId());
    }

    public Map<String, Object> getCaseDetail(Long caseId) {
        InquiryCase inquiryCase = getCaseOrThrow(caseId);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("case", toCaseSummaryMap(inquiryCase));
        detail.put("artifacts", artifactRepo.findByCaseIdOrderByCreatedAtDesc(caseId).stream().map(this::toArtifactMap).toList());
        detail.put("requirements", requirementRepo.findByCaseIdOrderByFieldKeyAsc(caseId).stream().map(this::toRequirementMap).toList());
        detail.put("missingFields", missingFieldRepo.findByCaseIdOrderByPriorityDesc(caseId).stream().map(this::toMissingFieldMap).toList());
        detail.put("risks", riskFlagRepo.findByCaseIdOrderByLevelDesc(caseId).stream().map(this::toRiskMap).toList());
        detail.put("quoteTaskDraft", quoteTaskDraftRepo.findByCaseId(caseId).map(this::toQuoteTaskDraftMap).orElse(null));
        return detail;
    }

    @Transactional
    public Map<String, Object> analyzeCase(Long caseId) {
        InquiryCase inquiryCase = getCaseOrThrow(caseId);
        List<InquiryArtifact> artifacts = artifactRepo.findByCaseIdOrderByCreatedAtDesc(caseId).stream()
                .filter(item -> "SUCCESS".equals(item.getParseStatus()) && item.getRawText() != null && !item.getRawText().isBlank())
                .toList();
        if (artifacts.isEmpty()) {
            throw new IllegalArgumentException("请先上传或粘贴至少一份可解析的客户资料");
        }

        String systemPrompt = """
                你是资深外贸销售经理和报价前资料审查 Agent。你的任务是审查客户询盘资料，而不是直接报价。
                必须只返回一个 JSON 对象，不要返回 Markdown，不要解释，不要包裹 ```。
                所有不确定的字段必须标记为 NEED_CONFIRM 或 MISSING，不允许编造价格、交期、认证或付款条件。
                英文邮件必须专业、简洁，目标是向客户确认报价前缺失信息。
                """;

        String userPrompt = buildAnalyzePrompt(inquiryCase, artifacts);

        String raw;
        try {
            raw = orchestrator.reasoning(systemPrompt, userPrompt).get(120, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("询盘资料 AI 审查失败: caseId={}", caseId, e);
            throw new IllegalArgumentException("AI 审查失败: " + readableError(e));
        }

        JsonNode root = parseJsonObject(raw);
        saveAnalysisResult(inquiryCase, root);
        return getCaseDetail(caseId);
    }

    @Transactional
    public Map<String, Object> updateCaseStatus(Long caseId, Map<String, Object> body) {
        InquiryCase inquiryCase = getCaseOrThrow(caseId);
        String status = stringValue(body.get("status"));
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("案件状态不能为空");
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DRAFT", "REVIEWING", "WAITING_CUSTOMER", "READY_TO_QUOTE", "CLOSED").contains(normalized)) {
            throw new IllegalArgumentException("不支持的案件状态: " + status);
        }
        inquiryCase.setStatus(normalized);
        inquiryCase = caseRepo.save(inquiryCase);
        return toCaseSummaryMap(inquiryCase);
    }

    @Transactional
    public Map<String, Object> addTextArtifact(Long caseId, Map<String, Object> body) {
        String title = stringValue(body.get("title"));
        String text = stringValue(body.get("text"));
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("粘贴内容不能为空");
        }
        InquiryArtifact artifact = addTextArtifact(
                caseId,
                title != null && !title.isBlank() ? title : "客户邮件正文",
                "EMAIL_TEXT",
                "PASTE",
                text
        );
        touchCase(caseId);
        return toArtifactMap(artifact);
    }

    @Transactional
    public Map<String, Object> uploadArtifact(Long caseId, MultipartFile file) {
        getCaseOrThrow(caseId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }

        InquiryArtifact artifact;
        try {
            FileParserService.ParsedDocument parsed = fileParserService.parse(file);
            artifact = InquiryArtifact.builder()
                    .caseId(caseId)
                    .fileName(parsed.fileName())
                    .fileType(resolveFileType(parsed.fileName(), parsed.mimeType()))
                    .sourceType("UPLOAD")
                    .rawText(parsed.content())
                    .parseStatus("SUCCESS")
                    .build();
        } catch (Exception e) {
            String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
            artifact = InquiryArtifact.builder()
                    .caseId(caseId)
                    .fileName(fileName)
                    .fileType(resolveFileType(fileName, null))
                    .sourceType("UPLOAD")
                    .rawText("")
                    .parseStatus("FAILED")
                    .parseError(e.getMessage())
                    .build();
            log.warn("询盘资料解析失败: caseId={}, file={}, error={}", caseId, fileName, e.getMessage());
        }

        artifact = artifactRepo.save(artifact);
        touchCase(caseId);
        return toArtifactMap(artifact);
    }

    @Transactional
    public Map<String, Object> createRequirement(Long caseId, Map<String, Object> body, String updatedBy) {
        getCaseOrThrow(caseId);
        String fieldKey = stringValue(body.get("fieldKey"));
        String fieldLabel = stringValue(body.get("fieldLabel"));
        if (fieldLabel == null || fieldLabel.isBlank()) {
            throw new IllegalArgumentException("字段名称不能为空");
        }
        if (fieldKey == null || fieldKey.isBlank()) {
            fieldKey = fieldLabel.trim()
                    .replaceAll("\\s+", "_")
                    .replaceAll("[^A-Za-z0-9_\\u4e00-\\u9fa5]", "");
        }
        ExtractedRequirement requirement = ExtractedRequirement.builder()
                .caseId(caseId)
                .fieldKey(trimTo(fieldKey, 80))
                .fieldLabel(trimTo(fieldLabel, 120))
                .fieldValue(stringValue(body.get("fieldValue")))
                .confidence(null)
                .sourceEvidence(stringValue(body.get("sourceEvidence")))
                .status(normalizeStatus(stringValue(body.get("status")) != null ? stringValue(body.get("status")) : "USER_CONFIRMED"))
                .updatedBy(updatedBy)
                .build();
        requirement = requirementRepo.save(requirement);
        touchCase(caseId);
        return toRequirementMap(requirement);
    }

    @Transactional
    public Map<String, Object> updateRequirement(Long caseId, Long requirementId, Map<String, Object> body, String updatedBy) {
        getCaseOrThrow(caseId);
        ExtractedRequirement requirement = requirementRepo.findById(requirementId)
                .orElseThrow(() -> new IllegalArgumentException("字段不存在: " + requirementId));
        if (!Objects.equals(requirement.getCaseId(), caseId)) {
            throw new IllegalArgumentException("字段不属于当前询盘案件");
        }
        if (body.containsKey("fieldValue")) {
            requirement.setFieldValue(stringValue(body.get("fieldValue")));
        }
        if (body.containsKey("status")) {
            requirement.setStatus(trimTo(stringValue(body.get("status")), 30));
        } else {
            requirement.setStatus("USER_CONFIRMED");
        }
        requirement.setUpdatedBy(updatedBy);
        requirement = requirementRepo.save(requirement);
        touchCase(caseId);
        return toRequirementMap(requirement);
    }

    @Transactional
    public void deleteRequirement(Long caseId, Long requirementId) {
        getCaseOrThrow(caseId);
        ExtractedRequirement requirement = requirementRepo.findById(requirementId)
                .orElseThrow(() -> new IllegalArgumentException("字段不存在: " + requirementId));
        if (!Objects.equals(requirement.getCaseId(), caseId)) {
            throw new IllegalArgumentException("字段不属于当前询盘案件");
        }
        requirementRepo.delete(requirement);
        touchCase(caseId);
    }

    @Transactional
    public Map<String, Object> createMissingField(Long caseId, Map<String, Object> body) {
        getCaseOrThrow(caseId);
        String fieldKey = stringValue(body.get("fieldKey"));
        if (fieldKey == null || fieldKey.isBlank()) {
            throw new IllegalArgumentException("缺失字段名称不能为空");
        }
        MissingField item = MissingField.builder()
                .caseId(caseId)
                .fieldKey(trimTo(fieldKey, 80))
                .reason(stringValue(body.get("reason")))
                .questionEn(stringValue(body.get("questionEn")))
                .priority(normalizePriority(stringValue(body.get("priority"))))
                .build();
        item = missingFieldRepo.save(item);
        touchCase(caseId);
        return toMissingFieldMap(item);
    }

    @Transactional
    public Map<String, Object> updateMissingField(Long caseId, Long missingId, Map<String, Object> body) {
        getCaseOrThrow(caseId);
        MissingField item = missingFieldRepo.findById(missingId)
                .orElseThrow(() -> new IllegalArgumentException("缺失项不存在: " + missingId));
        if (!Objects.equals(item.getCaseId(), caseId)) {
            throw new IllegalArgumentException("缺失项不属于当前询盘案件");
        }
        if (body.containsKey("fieldKey")) item.setFieldKey(trimTo(stringValue(body.get("fieldKey")), 80));
        if (body.containsKey("reason")) item.setReason(stringValue(body.get("reason")));
        if (body.containsKey("questionEn")) item.setQuestionEn(stringValue(body.get("questionEn")));
        if (body.containsKey("priority")) item.setPriority(normalizePriority(stringValue(body.get("priority"))));
        item = missingFieldRepo.save(item);
        touchCase(caseId);
        return toMissingFieldMap(item);
    }

    @Transactional
    public void deleteMissingField(Long caseId, Long missingId) {
        getCaseOrThrow(caseId);
        MissingField item = missingFieldRepo.findById(missingId)
                .orElseThrow(() -> new IllegalArgumentException("缺失项不存在: " + missingId));
        if (!Objects.equals(item.getCaseId(), caseId)) {
            throw new IllegalArgumentException("缺失项不属于当前询盘案件");
        }
        missingFieldRepo.delete(item);
        touchCase(caseId);
    }

    @Transactional
    public Map<String, Object> createRisk(Long caseId, Map<String, Object> body) {
        getCaseOrThrow(caseId);
        String title = stringValue(body.get("title"));
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("风险标题不能为空");
        }
        RiskFlag item = RiskFlag.builder()
                .caseId(caseId)
                .riskType(trimTo(defaultValue(stringValue(body.get("riskType")), "SPEC"), 40))
                .level(normalizePriority(stringValue(body.get("level"))))
                .title(trimTo(title, 200))
                .description(stringValue(body.get("description")))
                .suggestion(stringValue(body.get("suggestion")))
                .build();
        item = riskFlagRepo.save(item);
        touchCase(caseId);
        return toRiskMap(item);
    }

    @Transactional
    public Map<String, Object> updateRisk(Long caseId, Long riskId, Map<String, Object> body) {
        getCaseOrThrow(caseId);
        RiskFlag item = riskFlagRepo.findById(riskId)
                .orElseThrow(() -> new IllegalArgumentException("风险项不存在: " + riskId));
        if (!Objects.equals(item.getCaseId(), caseId)) {
            throw new IllegalArgumentException("风险项不属于当前询盘案件");
        }
        if (body.containsKey("riskType")) item.setRiskType(trimTo(defaultValue(stringValue(body.get("riskType")), "SPEC"), 40));
        if (body.containsKey("level")) item.setLevel(normalizePriority(stringValue(body.get("level"))));
        if (body.containsKey("title")) item.setTitle(trimTo(stringValue(body.get("title")), 200));
        if (body.containsKey("description")) item.setDescription(stringValue(body.get("description")));
        if (body.containsKey("suggestion")) item.setSuggestion(stringValue(body.get("suggestion")));
        item = riskFlagRepo.save(item);
        touchCase(caseId);
        return toRiskMap(item);
    }

    @Transactional
    public void deleteRisk(Long caseId, Long riskId) {
        getCaseOrThrow(caseId);
        RiskFlag item = riskFlagRepo.findById(riskId)
                .orElseThrow(() -> new IllegalArgumentException("风险项不存在: " + riskId));
        if (!Objects.equals(item.getCaseId(), caseId)) {
            throw new IllegalArgumentException("风险项不属于当前询盘案件");
        }
        riskFlagRepo.delete(item);
        touchCase(caseId);
    }

    @Transactional
    public Map<String, Object> createAgentTaskFromDraft(Long caseId, String ownerId) {
        InquiryCase inquiryCase = getCaseOrThrow(caseId);
        QuoteTaskDraft draft = quoteTaskDraftRepo.findByCaseId(caseId)
                .orElseThrow(() -> new IllegalArgumentException("请先生成或保存内部报价任务单"));

        String description = """
                询盘案件: %s
                客户: %s
                联系人: %s
                邮箱: %s
                国家/地区: %s

                已知信息:
                %s

                缺失信息:
                %s

                风险摘要:
                %s
                """.formatted(
                inquiryCase.getCaseNo(),
                nullToEmpty(inquiryCase.getCustomerName()),
                nullToEmpty(inquiryCase.getContactName()),
                nullToEmpty(inquiryCase.getContactEmail()),
                nullToEmpty(inquiryCase.getCountry()),
                nullToEmpty(draft.getKnownInfo()),
                nullToEmpty(draft.getMissingInfo()),
                nullToEmpty(draft.getRiskSummary())
        );

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("caseId", caseId);
        context.put("caseNo", inquiryCase.getCaseNo());
        context.put("customerName", inquiryCase.getCustomerName());
        context.put("contactEmail", inquiryCase.getContactEmail());
        context.put("assigneeRole", draft.getAssigneeRole());

        AgentTask task = AgentTask.builder()
                .userId(ownerId != null && !ownerId.isBlank() ? ownerId : inquiryCase.getOwnerId())
                .name(draft.getTaskTitle() != null && !draft.getTaskTitle().isBlank()
                        ? draft.getTaskTitle()
                        : "询盘报价准备 - " + inquiryCase.getCaseNo())
                .type("inquiry_quote")
                .scheduleType("ONE_TIME")
                .status("PENDING")
                .priority(resolveTaskPriority(inquiryCase.getScore()))
                .description(description)
                .context(writeJson(context))
                .agentType("inquiry-review")
                .input(writeJson(Map.of("caseId", caseId, "caseNo", inquiryCase.getCaseNo())))
                .maxExecutions(1)
                .nextRunAt(LocalDateTime.now())
                .build();
        task = agentTaskRepo.save(task);

        draft.setStatus("CREATED");
        quoteTaskDraftRepo.save(draft);
        inquiryCase.setStatus("READY_TO_QUOTE");
        caseRepo.save(inquiryCase);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", task.getId());
        result.put("name", task.getName());
        result.put("type", task.getType());
        result.put("status", task.getStatus());
        result.put("priority", task.getPriority());
        result.put("createdAt", task.getCreatedAt() != null ? task.getCreatedAt().toString() : "");
        return result;
    }

    @Transactional
    public Map<String, Object> saveQuoteTaskDraft(Long caseId, Map<String, Object> body) {
        getCaseOrThrow(caseId);
        QuoteTaskDraft draft = quoteTaskDraftRepo.findByCaseId(caseId).orElseGet(() ->
                QuoteTaskDraft.builder()
                        .caseId(caseId)
                        .taskTitle("内部报价任务")
                        .status("DRAFT")
                        .build()
        );
        if (body.containsKey("taskTitle")) draft.setTaskTitle(trimTo(stringValue(body.get("taskTitle")), 300));
        if (body.containsKey("knownInfo")) draft.setKnownInfo(stringValue(body.get("knownInfo")));
        if (body.containsKey("missingInfo")) draft.setMissingInfo(stringValue(body.get("missingInfo")));
        if (body.containsKey("riskSummary")) draft.setRiskSummary(stringValue(body.get("riskSummary")));
        if (body.containsKey("assigneeRole")) draft.setAssigneeRole(trimTo(stringValue(body.get("assigneeRole")), 40));
        if (body.containsKey("status")) draft.setStatus(trimTo(stringValue(body.get("status")), 20));
        if (body.containsKey("emailDraft")) draft.setEmailDraft(stringValue(body.get("emailDraft")));
        draft = quoteTaskDraftRepo.save(draft);
        touchCase(caseId);
        return toQuoteTaskDraftMap(draft);
    }

    @Transactional
    public Map<String, Object> saveEmailDraft(Long caseId, Map<String, Object> body) {
        getCaseOrThrow(caseId);
        QuoteTaskDraft draft = quoteTaskDraftRepo.findByCaseId(caseId).orElseGet(() ->
                QuoteTaskDraft.builder()
                        .caseId(caseId)
                        .taskTitle("内部报价任务")
                        .status("DRAFT")
                        .build()
        );
        draft.setEmailDraft(stringValue(body.get("emailDraft")));
        draft = quoteTaskDraftRepo.save(draft);
        touchCase(caseId);
        return toQuoteTaskDraftMap(draft);
    }

    public boolean canAccess(Long caseId, String ownerId, boolean admin) {
        if (admin) return true;
        return caseRepo.findById(caseId)
                .map(item -> Objects.equals(item.getOwnerId(), ownerId))
                .orElse(false);
    }

    private InquiryCase getCaseOrThrow(Long caseId) {
        return caseRepo.findById(caseId).orElseThrow(() -> new IllegalArgumentException("询盘案件不存在: " + caseId));
    }

    private InquiryArtifact addTextArtifact(Long caseId, String fileName, String fileType, String sourceType, String rawText) {
        getCaseOrThrow(caseId);
        InquiryArtifact artifact = InquiryArtifact.builder()
                .caseId(caseId)
                .fileName(trimTo(fileName, 300))
                .fileType(fileType)
                .sourceType(sourceType)
                .rawText(rawText)
                .parseStatus("SUCCESS")
                .build();
        return artifactRepo.save(artifact);
    }

    private void touchCase(Long caseId) {
        InquiryCase inquiryCase = getCaseOrThrow(caseId);
        caseRepo.save(inquiryCase);
    }

    private String buildAnalyzePrompt(InquiryCase inquiryCase, List<InquiryArtifact> artifacts) {
        StringBuilder material = new StringBuilder();
        for (InquiryArtifact artifact : artifacts) {
            material.append("\n\n---资料: ").append(artifact.getFileName())
                    .append(" / ").append(artifact.getFileType()).append("---\n")
                    .append(limit(artifact.getRawText(), 6000));
        }

        return """
                请审查以下外贸询盘案件资料，并按指定 JSON Schema 输出。

                案件信息:
                - 标题: %s
                - 客户公司: %s
                - 联系人: %s
                - 邮箱: %s
                - 国家/地区: %s
                - 来源: %s

                客户资料:
                %s

                输出 JSON Schema:
                {
                  "summary": "中文摘要，说明客户要什么、当前资料是否足够报价",
                  "score": 0-100,
                  "requirements": [
                    {
                      "fieldKey": "productType",
                      "fieldLabel": "产品类型",
                      "fieldValue": "字段值，没有则留空",
                      "confidence": 0.0-1.0,
                      "sourceEvidence": "来自哪份资料的哪句话或哪一行",
                      "status": "AI_EXTRACTED | NEED_CONFIRM | MISSING"
                    }
                  ],
                  "missingFields": [
                    {
                      "fieldKey": "glass",
                      "reason": "为什么报价前必须确认",
                      "questionEn": "英文追问句",
                      "priority": "HIGH | MEDIUM | LOW"
                    }
                  ],
                  "risks": [
                    {
                      "riskType": "SPEC | PRICE | DELIVERY | PAYMENT | COMPLIANCE | LOGISTICS",
                      "level": "HIGH | MEDIUM | LOW",
                      "title": "风险标题",
                      "description": "风险说明",
                      "suggestion": "处理建议"
                    }
                  ],
                  "customerEmailDraft": "英文追问邮件正文",
                  "internalTaskDraft": {
                    "taskTitle": "内部报价任务标题",
                    "knownInfo": "已知信息",
                    "missingInfo": "缺失信息",
                    "riskSummary": "风险摘要",
                    "assigneeRole": "SALES | ENGINEERING | PURCHASING | MANAGER"
                  }
                }

                字段至少覆盖这些报价前常用项:
                projectName, customerCountry, productType, productName, intendedUse, width, height, depth, sizeUnit,
                quantity, material, color, surfaceFinish, glass, hardware, accessories, packagingRequirement,
                tradeTerm, destinationPort, deliveryDeadline, targetPrice, certificationRequired.
                """.formatted(
                nullToEmpty(inquiryCase.getTitle()),
                nullToEmpty(inquiryCase.getCustomerName()),
                nullToEmpty(inquiryCase.getContactName()),
                nullToEmpty(inquiryCase.getContactEmail()),
                nullToEmpty(inquiryCase.getCountry()),
                nullToEmpty(inquiryCase.getSource()),
                limit(material.toString(), 18000)
        );
    }

    private void saveAnalysisResult(InquiryCase inquiryCase, JsonNode root) {
        Long caseId = inquiryCase.getId();

        requirementRepo.deleteByCaseId(caseId);
        missingFieldRepo.deleteByCaseId(caseId);
        riskFlagRepo.deleteByCaseId(caseId);

        inquiryCase.setSummary(root.path("summary").asText(""));
        if (root.has("score") && root.path("score").canConvertToInt()) {
            inquiryCase.setScore(Math.max(0, Math.min(100, root.path("score").asInt())));
        }
        inquiryCase.setStatus("REVIEWING");
        caseRepo.save(inquiryCase);

        JsonNode requirements = root.path("requirements");
        if (requirements.isArray()) {
            for (JsonNode node : requirements) {
                String fieldKey = node.path("fieldKey").asText("");
                if (fieldKey.isBlank()) continue;
                requirementRepo.save(ExtractedRequirement.builder()
                        .caseId(caseId)
                        .fieldKey(trimTo(fieldKey, 80))
                        .fieldLabel(trimTo(node.path("fieldLabel").asText(fieldKey), 120))
                        .fieldValue(node.path("fieldValue").asText(""))
                        .confidence(toConfidence(node.path("confidence")))
                        .sourceEvidence(node.path("sourceEvidence").asText(""))
                        .status(normalizeStatus(node.path("status").asText("AI_EXTRACTED")))
                        .updatedBy("AI")
                        .build());
            }
        }

        JsonNode missingFields = root.path("missingFields");
        if (missingFields.isArray()) {
            for (JsonNode node : missingFields) {
                String fieldKey = node.path("fieldKey").asText("");
                if (fieldKey.isBlank()) continue;
                missingFieldRepo.save(MissingField.builder()
                        .caseId(caseId)
                        .fieldKey(trimTo(fieldKey, 80))
                        .reason(node.path("reason").asText(""))
                        .questionEn(node.path("questionEn").asText(""))
                        .priority(normalizePriority(node.path("priority").asText("MEDIUM")))
                        .build());
            }
        }

        JsonNode risks = root.path("risks");
        if (risks.isArray()) {
            for (JsonNode node : risks) {
                String title = node.path("title").asText("");
                if (title.isBlank()) continue;
                riskFlagRepo.save(RiskFlag.builder()
                        .caseId(caseId)
                        .riskType(trimTo(node.path("riskType").asText("SPEC"), 40))
                        .level(normalizePriority(node.path("level").asText("MEDIUM")))
                        .title(trimTo(title, 200))
                        .description(node.path("description").asText(""))
                        .suggestion(node.path("suggestion").asText(""))
                        .build());
            }
        }

        JsonNode internalTask = root.path("internalTaskDraft");
        QuoteTaskDraft draft = quoteTaskDraftRepo.findByCaseId(caseId).orElseGet(() ->
                QuoteTaskDraft.builder()
                        .caseId(caseId)
                        .taskTitle("内部报价任务")
                        .status("DRAFT")
                        .build()
        );
        draft.setEmailDraft(root.path("customerEmailDraft").asText(nullToEmpty(draft.getEmailDraft())));
        if (internalTask.isObject()) {
            draft.setTaskTitle(trimTo(internalTask.path("taskTitle").asText(nullToEmpty(draft.getTaskTitle())), 300));
            draft.setKnownInfo(internalTask.path("knownInfo").asText(nullToEmpty(draft.getKnownInfo())));
            draft.setMissingInfo(internalTask.path("missingInfo").asText(nullToEmpty(draft.getMissingInfo())));
            draft.setRiskSummary(internalTask.path("riskSummary").asText(nullToEmpty(draft.getRiskSummary())));
            draft.setAssigneeRole(trimTo(internalTask.path("assigneeRole").asText("SALES"), 40));
        }
        quoteTaskDraftRepo.save(draft);
    }

    private JsonNode parseJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("AI 返回为空");
        }
        String json = raw.trim();
        if (json.startsWith("```")) {
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isObject()) {
                throw new IllegalArgumentException("AI 返回不是 JSON 对象");
            }
            return root;
        } catch (IOException e) {
            log.warn("AI JSON 解析失败，raw={}", limit(raw, 1000));
            throw new IllegalArgumentException("AI 返回 JSON 解析失败: " + e.getMessage());
        }
    }

    private BigDecimal toConfidence(JsonNode node) {
        if (node == null || !node.isNumber()) return null;
        double value = Math.max(0, Math.min(1, node.asDouble()));
        return BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String normalizeStatus(String value) {
        if (value == null) return "AI_EXTRACTED";
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (Set.of("AI_EXTRACTED", "USER_CONFIRMED", "MISSING", "NEED_CONFIRM").contains(normalized)) {
            return normalized;
        }
        return "AI_EXTRACTED";
    }

    private String normalizePriority(String value) {
        if (value == null) return "MEDIUM";
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (Set.of("HIGH", "MEDIUM", "LOW").contains(normalized)) {
            return normalized;
        }
        return "MEDIUM";
    }

    private int resolveTaskPriority(Integer score) {
        if (score == null) return 5;
        if (score >= 85) return 9;
        if (score >= 70) return 7;
        if (score >= 50) return 5;
        return 3;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String readableError(Exception e) {
        Throwable cause = e.getCause();
        String message = cause != null && cause.getMessage() != null ? cause.getMessage() : e.getMessage();
        return message != null && !message.isBlank() ? message : e.getClass().getSimpleName();
    }

    private String limit(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) + "\n...[内容已截断]" : value;
    }

    private String generateCaseNo() {
        String prefix = "INQ-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        for (int i = 0; i < 5; i++) {
            String caseNo = prefix + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
            if (caseRepo.findByCaseNo(caseNo).isEmpty()) return caseNo;
        }
        return prefix + System.currentTimeMillis();
    }

    private String resolveFileType(String fileName, String mimeType) {
        String lowerName = fileName != null ? fileName.toLowerCase(Locale.ROOT) : "";
        String mime = mimeType != null ? mimeType.toLowerCase(Locale.ROOT) : "";
        if (mime.contains("pdf") || lowerName.endsWith(".pdf")) return "PDF";
        if (mime.contains("spreadsheet") || mime.contains("excel") || lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) return "XLSX";
        if (mime.contains("word") || lowerName.endsWith(".docx") || lowerName.endsWith(".doc")) return "DOCX";
        if (lowerName.endsWith(".eml")) return "EMAIL";
        if (mime.startsWith("image/")) return "IMAGE";
        return "TXT";
    }

    private Map<String, Object> toCaseSummaryMap(InquiryCase item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", item.getId());
        m.put("caseNo", item.getCaseNo());
        m.put("title", item.getTitle());
        m.put("customerName", nullToEmpty(item.getCustomerName()));
        m.put("contactName", nullToEmpty(item.getContactName()));
        m.put("contactEmail", nullToEmpty(item.getContactEmail()));
        m.put("country", nullToEmpty(item.getCountry()));
        m.put("source", nullToEmpty(item.getSource()));
        m.put("status", item.getStatus());
        m.put("score", item.getScore());
        m.put("summary", nullToEmpty(item.getSummary()));
        m.put("ownerId", item.getOwnerId());
        m.put("ownerName", nullToEmpty(item.getOwnerName()));
        m.put("createdAt", item.getCreatedAt() != null ? item.getCreatedAt().toString() : "");
        m.put("updatedAt", item.getUpdatedAt() != null ? item.getUpdatedAt().toString() : "");
        return m;
    }

    private Map<String, Object> toArtifactMap(InquiryArtifact item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", item.getId());
        m.put("caseId", item.getCaseId());
        m.put("fileName", item.getFileName());
        m.put("fileType", item.getFileType());
        m.put("sourceType", item.getSourceType());
        m.put("rawText", nullToEmpty(item.getRawText()));
        m.put("contentPreview", preview(item.getRawText(), 280));
        m.put("parseStatus", item.getParseStatus());
        m.put("parseError", nullToEmpty(item.getParseError()));
        m.put("createdAt", item.getCreatedAt() != null ? item.getCreatedAt().toString() : "");
        return m;
    }

    private Map<String, Object> toRequirementMap(ExtractedRequirement item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", item.getId());
        m.put("caseId", item.getCaseId());
        m.put("fieldKey", item.getFieldKey());
        m.put("fieldLabel", item.getFieldLabel());
        m.put("fieldValue", nullToEmpty(item.getFieldValue()));
        m.put("confidence", item.getConfidence());
        m.put("sourceEvidence", nullToEmpty(item.getSourceEvidence()));
        m.put("status", item.getStatus());
        m.put("updatedBy", nullToEmpty(item.getUpdatedBy()));
        return m;
    }

    private Map<String, Object> toMissingFieldMap(MissingField item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", item.getId());
        m.put("caseId", item.getCaseId());
        m.put("fieldKey", item.getFieldKey());
        m.put("reason", nullToEmpty(item.getReason()));
        m.put("questionEn", nullToEmpty(item.getQuestionEn()));
        m.put("priority", item.getPriority());
        return m;
    }

    private Map<String, Object> toRiskMap(RiskFlag item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", item.getId());
        m.put("caseId", item.getCaseId());
        m.put("riskType", item.getRiskType());
        m.put("level", item.getLevel());
        m.put("title", item.getTitle());
        m.put("description", nullToEmpty(item.getDescription()));
        m.put("suggestion", nullToEmpty(item.getSuggestion()));
        return m;
    }

    private Map<String, Object> toQuoteTaskDraftMap(QuoteTaskDraft item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", item.getId());
        m.put("caseId", item.getCaseId());
        m.put("taskTitle", item.getTaskTitle());
        m.put("knownInfo", nullToEmpty(item.getKnownInfo()));
        m.put("missingInfo", nullToEmpty(item.getMissingInfo()));
        m.put("riskSummary", nullToEmpty(item.getRiskSummary()));
        m.put("assigneeRole", nullToEmpty(item.getAssigneeRole()));
        m.put("status", item.getStatus());
        m.put("emailDraft", nullToEmpty(item.getEmailDraft()));
        return m;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String trimTo(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String preview(String value, int max) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > max ? normalized.substring(0, max) + "..." : normalized;
    }
}

package com.ecommerce.agent.controller;

import com.ecommerce.agent.config.AuthUser;
import com.ecommerce.agent.service.InquiryReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/inquiry-review")
@RequiredArgsConstructor
public class InquiryReviewController {

    private final InquiryReviewService inquiryReviewService;

    @GetMapping("/cases")
    public ResponseEntity<Map<String, Object>> listCases(@RequestParam(required = false) String keyword,
                                                         Authentication authentication) {
        return ResponseEntity.ok(Map.of(
                "cases", inquiryReviewService.listCases(ownerKey(authentication), isAdmin(authentication), keyword)
        ));
    }

    @PostMapping("/cases")
    public ResponseEntity<Map<String, Object>> createCase(@RequestBody Map<String, Object> body,
                                                          Authentication authentication) {
        Map<String, Object> detail = inquiryReviewService.createCase(
                body,
                ownerKey(authentication),
                AuthUser.username(authentication)
        );
        return ResponseEntity.ok(Map.of("success", true, "detail", detail));
    }

    @GetMapping("/cases/{caseId}")
    public ResponseEntity<Map<String, Object>> getCase(@PathVariable Long caseId,
                                                       Authentication authentication) {
        if (!canAccess(caseId, authentication)) {
            return ResponseEntity.status(403).body(Map.of("message", "无权查看该询盘案件"));
        }
        return ResponseEntity.ok(inquiryReviewService.getCaseDetail(caseId));
    }

    @PutMapping("/cases/{caseId}/status")
    public ResponseEntity<Map<String, Object>> updateCaseStatus(@PathVariable Long caseId,
                                                                @RequestBody Map<String, Object> body,
                                                                Authentication authentication) {
        if (!canAccess(caseId, authentication)) {
            return ResponseEntity.status(403).body(Map.of("message", "无权修改该询盘案件"));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "case", inquiryReviewService.updateCaseStatus(caseId, body)
        ));
    }

    @PostMapping("/cases/{caseId}/artifacts/text")
    public ResponseEntity<Map<String, Object>> addTextArtifact(@PathVariable Long caseId,
                                                               @RequestBody Map<String, Object> body,
                                                               Authentication authentication) {
        if (!canAccess(caseId, authentication)) {
            return ResponseEntity.status(403).body(Map.of("message", "无权修改该询盘案件"));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "artifact", inquiryReviewService.addTextArtifact(caseId, body)
        ));
    }

    @PostMapping("/cases/{caseId}/artifacts")
    public ResponseEntity<Map<String, Object>> uploadArtifact(@PathVariable Long caseId,
                                                              @RequestParam("file") MultipartFile file,
                                                              Authentication authentication) {
        if (!canAccess(caseId, authentication)) {
            return ResponseEntity.status(403).body(Map.of("message", "无权修改该询盘案件"));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "artifact", inquiryReviewService.uploadArtifact(caseId, file)
        ));
    }

    @PostMapping("/cases/{caseId}/analyze")
    public ResponseEntity<Map<String, Object>> analyzeCase(@PathVariable Long caseId,
                                                           Authentication authentication) {
        if (!canAccess(caseId, authentication)) {
            return ResponseEntity.status(403).body(Map.of("message", "无权审查该询盘案件"));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "detail", inquiryReviewService.analyzeCase(caseId)
        ));
    }

    @PutMapping("/cases/{caseId}/requirements/{fieldId}")
    public ResponseEntity<Map<String, Object>> updateRequirement(@PathVariable Long caseId,
                                                                 @PathVariable Long fieldId,
                                                                 @RequestBody Map<String, Object> body,
                                                                 Authentication authentication) {
        if (!canAccess(caseId, authentication)) {
            return ResponseEntity.status(403).body(Map.of("message", "无权修改该询盘案件"));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "requirement", inquiryReviewService.updateRequirement(caseId, fieldId, body, AuthUser.username(authentication))
        ));
    }

    @PostMapping("/cases/{caseId}/requirements")
    public ResponseEntity<Map<String, Object>> createRequirement(@PathVariable Long caseId,
                                                                 @RequestBody Map<String, Object> body,
                                                                 Authentication authentication) {
        if (!canAccess(caseId, authentication)) {
            return ResponseEntity.status(403).body(Map.of("message", "无权修改该询盘案件"));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "requirement", inquiryReviewService.createRequirement(caseId, body, AuthUser.username(authentication))
        ));
    }

    @DeleteMapping("/cases/{caseId}/requirements/{fieldId}")
    public ResponseEntity<Map<String, Object>> deleteRequirement(@PathVariable Long caseId,
                                                                 @PathVariable Long fieldId,
                                                                 Authentication authentication) {
        if (!canAccess(caseId, authentication)) {
            return ResponseEntity.status(403).body(Map.of("message", "无权修改该询盘案件"));
        }
        inquiryReviewService.deleteRequirement(caseId, fieldId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/cases/{caseId}/missing-fields")
    public ResponseEntity<Map<String, Object>> createMissingField(@PathVariable Long caseId,
                                                                  @RequestBody Map<String, Object> body,
                                                                  Authentication authentication) {
        if (!canAccess(caseId, authentication)) {
            return ResponseEntity.status(403).body(Map.of("message", "无权修改该询盘案件"));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "missingField", inquiryReviewService.createMissingField(caseId, body)
        ));
    }

    @PutMapping("/cases/{caseId}/missing-fields/{missingId}")
    public ResponseEntity<Map<String, Object>> updateMissingField(@PathVariable Long caseId,
                                                                  @PathVariable Long missingId,
                                                                  @RequestBody Map<String, Object> body,
                                                                  Authentication authentication) {
        if (!canAccess(caseId, authentication)) {
            return ResponseEntity.status(403).body(Map.of("message", "无权修改该询盘案件"));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "missingField", inquiryReviewService.updateMissingField(caseId, missingId, body)
        ));
    }

    @DeleteMapping("/cases/{caseId}/missing-fields/{missingId}")
    public ResponseEntity<Map<String, Object>> deleteMissingField(@PathVariable Long caseId,
                                                                  @PathVariable Long missingId,
                                                                  Authentication authentication) {
        if (!canAccess(caseId, authentication)) {
            return ResponseEntity.status(403).body(Map.of("message", "无权修改该询盘案件"));
        }
        inquiryReviewService.deleteMissingField(caseId, missingId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/cases/{caseId}/risks")
    public ResponseEntity<Map<String, Object>> createRisk(@PathVariable Long caseId,
                                                          @RequestBody Map<String, Object> body,
                                                          Authentication authentication) {
        if (!canAccess(caseId, authentication)) {
            return ResponseEntity.status(403).body(Map.of("message", "无权修改该询盘案件"));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "risk", inquiryReviewService.createRisk(caseId, body)
        ));
    }

    @PutMapping("/cases/{caseId}/risks/{riskId}")
    public ResponseEntity<Map<String, Object>> updateRisk(@PathVariable Long caseId,
                                                          @PathVariable Long riskId,
                                                          @RequestBody Map<String, Object> body,
                                                          Authentication authentication) {
        if (!canAccess(caseId, authentication)) {
            return ResponseEntity.status(403).body(Map.of("message", "无权修改该询盘案件"));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "risk", inquiryReviewService.updateRisk(caseId, riskId, body)
        ));
    }

    @DeleteMapping("/cases/{caseId}/risks/{riskId}")
    public ResponseEntity<Map<String, Object>> deleteRisk(@PathVariable Long caseId,
                                                          @PathVariable Long riskId,
                                                          Authentication authentication) {
        if (!canAccess(caseId, authentication)) {
            return ResponseEntity.status(403).body(Map.of("message", "无权修改该询盘案件"));
        }
        inquiryReviewService.deleteRisk(caseId, riskId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/cases/{caseId}/email-draft")
    public ResponseEntity<Map<String, Object>> saveEmailDraft(@PathVariable Long caseId,
                                                              @RequestBody Map<String, Object> body,
                                                              Authentication authentication) {
        if (!canAccess(caseId, authentication)) {
            return ResponseEntity.status(403).body(Map.of("message", "无权修改该询盘案件"));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "quoteTaskDraft", inquiryReviewService.saveEmailDraft(caseId, body)
        ));
    }

    @PutMapping("/cases/{caseId}/quote-task-draft")
    public ResponseEntity<Map<String, Object>> saveQuoteTaskDraft(@PathVariable Long caseId,
                                                                  @RequestBody Map<String, Object> body,
                                                                  Authentication authentication) {
        if (!canAccess(caseId, authentication)) {
            return ResponseEntity.status(403).body(Map.of("message", "无权修改该询盘案件"));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "quoteTaskDraft", inquiryReviewService.saveQuoteTaskDraft(caseId, body)
        ));
    }

    @PostMapping("/cases/{caseId}/create-task")
    public ResponseEntity<Map<String, Object>> createTask(@PathVariable Long caseId,
                                                          Authentication authentication) {
        if (!canAccess(caseId, authentication)) {
            return ResponseEntity.status(403).body(Map.of("message", "无权为该询盘创建任务"));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "task", inquiryReviewService.createAgentTaskFromDraft(caseId, ownerKey(authentication)),
                "detail", inquiryReviewService.getCaseDetail(caseId)
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", e.getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    private boolean canAccess(Long caseId, Authentication authentication) {
        return inquiryReviewService.canAccess(caseId, ownerKey(authentication), isAdmin(authentication));
    }

    private String ownerKey(Authentication authentication) {
        String stableUserId = AuthUser.stableUserId(authentication);
        if (stableUserId != null && !stableUserId.isBlank()) {
            return stableUserId;
        }
        String username = AuthUser.username(authentication);
        if (username != null && !username.isBlank()) {
            return username;
        }
        return "user";
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}

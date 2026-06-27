package com.ecommerce.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class RagIndexProgressService {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "rag-index-rebuild");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Status status = Status.idle();

    public Map<String, Object> start(String trigger, Runnable task) {
        if (!beginIfIdle(trigger)) {
            Map<String, Object> current = snapshot();
            current.put("accepted", false);
            current.put("message", "索引更新正在进行中");
            return current;
        }

        executor.submit(() -> {
            try {
                task.run();
                if (running.get()) {
                    complete("索引更新完成");
                }
            } catch (Exception e) {
                log.error("RAG 索引更新失败", e);
                fail("索引更新失败: " + e.getMessage());
            }
        });

        Map<String, Object> current = snapshot();
        current.put("accepted", true);
        return current;
    }

    public boolean beginIfIdle(String trigger) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        String taskId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        status = new Status(
                taskId,
                "running",
                normalizeTrigger(trigger),
                "preparing",
                1,
                0,
                0,
                0,
                0,
                "准备更新 RAG 向量索引",
                now,
                now,
                null,
                null
        );
        return true;
    }

    public void update(String phase, int progress, int processedItems, int totalItems, String message) {
        Status current = status;
        if (!running.get()) return;
        status = current.withProgress(
                phase,
                Math.max(1, Math.min(99, progress)),
                Math.max(0, processedItems),
                Math.max(0, totalItems),
                message
        );
    }

    public void updateTotals(int totalItems, int changedItems, int removedItems, String message) {
        Status current = status;
        if (!running.get()) return;
        status = current.withTotals(
                Math.max(0, totalItems),
                Math.max(0, changedItems),
                Math.max(0, removedItems),
                message
        );
    }

    public void complete(String message) {
        Status current = status;
        status = current.asDone(message);
        running.set(false);
    }

    public void fail(String message) {
        Status current = status;
        status = current.asFailed(message);
        running.set(false);
    }

    public Map<String, Object> snapshot() {
        return status.toMap();
    }

    private String normalizeTrigger(String trigger) {
        return trigger == null || trigger.isBlank() ? "manual" : trigger;
    }

    private record Status(
            String taskId,
            String state,
            String trigger,
            String phase,
            int progress,
            int processedItems,
            int totalItems,
            int changedItems,
            int removedItems,
            String message,
            LocalDateTime startedAt,
            LocalDateTime updatedAt,
            LocalDateTime finishedAt,
            String error
    ) {
        static Status idle() {
            LocalDateTime now = LocalDateTime.now();
            return new Status(
                    "",
                    "idle",
                    "",
                    "idle",
                    100,
                    0,
                    0,
                    0,
                    0,
                    "当前没有索引更新任务",
                    null,
                    now,
                    null,
                    null
            );
        }

        Status withProgress(String nextPhase, int nextProgress, int nextProcessedItems, int nextTotalItems, String nextMessage) {
            return new Status(
                    taskId,
                    state,
                    trigger,
                    nextPhase,
                    nextProgress,
                    nextProcessedItems,
                    nextTotalItems > 0 ? nextTotalItems : totalItems,
                    changedItems,
                    removedItems,
                    nextMessage,
                    startedAt,
                    LocalDateTime.now(),
                    finishedAt,
                    error
            );
        }

        Status withTotals(int nextTotalItems, int nextChangedItems, int nextRemovedItems, String nextMessage) {
            return new Status(
                    taskId,
                    state,
                    trigger,
                    phase,
                    progress,
                    processedItems,
                    nextTotalItems,
                    nextChangedItems,
                    nextRemovedItems,
                    nextMessage,
                    startedAt,
                    LocalDateTime.now(),
                    finishedAt,
                    error
            );
        }

        Status asDone(String nextMessage) {
            LocalDateTime now = LocalDateTime.now();
            return new Status(
                    taskId,
                    "completed",
                    trigger,
                    "completed",
                    100,
                    totalItems > 0 ? totalItems : processedItems,
                    totalItems,
                    changedItems,
                    removedItems,
                    nextMessage,
                    startedAt,
                    now,
                    now,
                    null
            );
        }

        Status asFailed(String nextMessage) {
            LocalDateTime now = LocalDateTime.now();
            return new Status(
                    taskId,
                    "failed",
                    trigger,
                    "failed",
                    progress,
                    processedItems,
                    totalItems,
                    changedItems,
                    removedItems,
                    nextMessage,
                    startedAt,
                    now,
                    now,
                    nextMessage
            );
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("taskId", taskId);
            map.put("state", state);
            map.put("running", "running".equals(state));
            map.put("trigger", trigger);
            map.put("phase", phase);
            map.put("progress", progress);
            map.put("processedItems", processedItems);
            map.put("totalItems", totalItems);
            map.put("changedItems", changedItems);
            map.put("removedItems", removedItems);
            map.put("message", message);
            map.put("startedAt", startedAt != null ? startedAt.toString() : null);
            map.put("updatedAt", updatedAt != null ? updatedAt.toString() : null);
            map.put("finishedAt", finishedAt != null ? finishedAt.toString() : null);
            map.put("error", error);
            return map;
        }
    }
}

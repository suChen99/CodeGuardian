package com.codemate.codereview.service;


import com.codemate.codereview.entity.ReviewCommit;
import com.codemate.codereview.entity.ReviewFile;
import com.codemate.codereview.entity.ReviewIssue;
import com.codemate.codereview.model.CommitInfo;
import com.codemate.codereview.model.ReviewProfile;
import com.codemate.codereview.repository.ReviewCommitRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

@Slf4j
@Service
public class ReviewStoreService {
    public static final String PENDING = "PENDING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    private final ReviewCommitRepository commitRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReviewStoreService(ReviewCommitRepository commitRepository) {
        this.commitRepository = commitRepository;
    }

    /** 整单已按当前配置版本 SUCCESS，调度器可以跳过。 */
    public boolean alreadyFreshSuccess(String commitHash) {
        return commitRepository.findByCommitHash(commitHash)
                .filter(c -> SUCCESS.equals(c.getStatus()))
                .filter(c -> ReviewProfile.VERSION.equals(c.getProfileVersion()))
                .isPresent();
    }

    /**
     * forceOverwrite=true：再审，清空全部文件。
     * false：只丢掉失败或旧版本，留下「本版本已 SUCCESS」的文件。
     */
    @Transactional
    public ReviewCommit markPending(CommitInfo info, boolean forceOverwrite) {
        ReviewCommit commit = commitRepository.findByCommitHash(info.getCommitId())
                .orElseGet(ReviewCommit::new);
        commit.setCommitHash(info.getCommitId());
        commit.setAuthor(info.getAuthor());
        commit.setMessage(info.getMessage());
        commit.setStatus(PENDING);
        commit.setErrorMessage(null);
        commit.setProfileVersion(ReviewProfile.VERSION);
        if (forceOverwrite) {
            commit.getFiles().clear();
        } else {
            commit.getFiles().removeIf(f ->
                    !SUCCESS.equals(f.getStatus())
                            || !ReviewProfile.VERSION.equals(f.getProfileVersion()));
        }
        LocalDateTime now = LocalDateTime.now();
        if (commit.getCreatedAt() == null) {
            commit.setCreatedAt(now);
        }
        commit.setUpdatedAt(now);
        return commitRepository.save(commit);
    }

    @Transactional
    public boolean isFreshFileSuccess(String commitHash, String fileName) {
        ReviewCommit commit = commitRepository.findByCommitHash(commitHash).orElse(null);
        if (commit == null) {
            return false;
        }
        for (ReviewFile file : commit.getFiles()) {
            if (fileName.equals(file.getFileName())
                    && SUCCESS.equals(file.getStatus())
                    && ReviewProfile.VERSION.equals(file.getProfileVersion())) {
                return true;
            }
        }
        return false;
    }

    /** 按文件名更新，不要每次 new 一行。 */
    @Transactional
    public void upsertFileResult(String commitHash, String fileName, String diffText, String rawJson) {
        ReviewCommit commit = commitRepository.findByCommitHash(commitHash)
                .orElseThrow(() -> new IllegalStateException("找不到提交" + commitHash));

        ReviewFile file = null;
        for (ReviewFile existing : commit.getFiles()) {
            if (fileName.equals(existing.getFileName())) {
                file = existing;
                break;
            }
        }
        if (file == null) {
            file = new ReviewFile();
            commit.addFile(file);
        }
        file.setFileName(fileName);
        file.setDiffText(diffText);
        file.setRawJson(rawJson);
        file.setStatus(SUCCESS);
        file.setProfileVersion(ReviewProfile.VERSION);
        file.setErrorMessage(null);
        file.getIssues().clear();
        parseIssues(rawJson, file);
        commit.setUpdatedAt(LocalDateTime.now());
        commitRepository.save(commit);
    }

    @Transactional
    public void markSuccess(String commitHash) {
        ReviewCommit commit = commitRepository.findByCommitHash(commitHash)
                .orElseThrow(() -> new IllegalStateException("找不到提交" + commitHash));
        commit.setStatus(SUCCESS);
        commit.setErrorMessage(null);
        commit.setProfileVersion(ReviewProfile.VERSION);
        commit.setUpdatedAt(LocalDateTime.now());
        commitRepository.save(commit);
        log.info("入库成功：{}，文件数 {}，版本 {}", commitHash, commit.getFiles().size(), ReviewProfile.VERSION);
    }

    @Transactional
    public void markFailed(String commitHash, String reason) {
        ReviewCommit commit = commitRepository.findByCommitHash(commitHash)
                .orElseThrow(() -> new IllegalStateException("找不到提交" + commitHash));
        commit.setStatus(FAILED);
        commit.setErrorMessage(reason == null ? null : reason.substring(0, Math.min(reason.length(), 1000)));
        commit.setUpdatedAt(LocalDateTime.now());
        commitRepository.save(commit);
        log.warn("入库标记失败：{}，原因：{}", commitHash, reason);
    }

    private void parseIssues(String rawJson, ReviewFile file) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode issuesNode = root.get("issues");
            if (issuesNode == null || !issuesNode.isArray()) {
                return;
            }
            for (JsonNode node : issuesNode) {
                ReviewIssue issue = new ReviewIssue();
                issue.setLineNo(node.get("line") == null ? null : node.get("line").asInt());
                issue.setLevel(textOr(node, "level", "WARN"));
                issue.setSuggestion(textOr(node, "suggestion", "无建议"));
                issue.setSource(textOr(node, "source", "LLM"));
                file.addIssue(issue);
            }
        } catch (Exception e) {
            log.warn("解析 AI JSON 失败，文件仍保存 raw_json: {}", e.getMessage());
        }
    }

    private String textOr(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        return value.asString();
    }

}

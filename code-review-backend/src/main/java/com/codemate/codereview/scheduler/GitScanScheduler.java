package com.codemate.codereview.scheduler;

import com.codemate.codereview.model.AiAnalyzeResult;
import com.codemate.codereview.model.CommitInfo;
import com.codemate.codereview.service.AIClientService;
import com.codemate.codereview.service.GitScanService;
import com.codemate.codereview.service.ReviewStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GitScanScheduler {
    @Autowired
    private GitScanService gitScanService;

    @Autowired
    private AIClientService aiClientService;  // 注入AI客户端

    @Autowired
    private ReviewStoreService reviewStoreService;

    /**
     * 上次任务结束后再隔 10 秒。
     * 用 fixedDelay 而不是 fixedRate：AI 若跑了 40 秒，不会叠加重入。
     */
    @Scheduled(fixedDelay = 10000)
    public void scanTask() {
        try {
            List<CommitInfo> pending = gitScanService.scanPendingCommits();
            if (pending.isEmpty()) {
                return;
            }
            log.info("本轮待审查提交数：{}", pending.size());

            for (CommitInfo commitInfo : pending) {
                // 重启后内存 lastReviewed 是空的，但库里可能已经 SUCCESS
                if (reviewStoreService.alreadyFreshSuccess(commitInfo.getCommitId())) {
                    log.info("当前版本已成功，跳过：{}", commitInfo.getCommitId());
                    gitScanService.markReviewed(commitInfo.getCommitId());
                    continue;
                }
                boolean ok = reviewOneCommit(commitInfo);
                if (ok) {
                    //成功才打勾，失败的下次还会出现在 pending 里
                    gitScanService.markReviewed(commitInfo.getCommitId());
                } else {
                    log.warn("提交 {} 审查未完成，本轮停止，等待下次重试", commitInfo.getCommitId());
                    break;
                }
            }
        } catch (Exception e) {
            log.error("本轮扫描任务异常：{}", e.getMessage(), e);

        }

    }

    /**
     * @return true 表示这条 commit 可以标记为已审查
     */
    private boolean reviewOneCommit(CommitInfo commitInfo) {

        log.info("捕获提交：{}，作者：{}", commitInfo.getCommitId(), commitInfo.getAuthor());
        reviewStoreService.markPending(commitInfo, false);
        Map<String, String> diffs = commitInfo.getFileDiffs();

        if (diffs == null || diffs.isEmpty()) {
            log.warn("提交 {} 没有可审查的diff(首次提交或全是跳过文件)，标记为完成", commitInfo.getCommitId());
            reviewStoreService.markSuccess(commitInfo.getCommitId());
            return true;
        }
        List<String> changedFiles = new ArrayList<>(diffs.keySet());
        for (Map.Entry<String, String> entry : diffs.entrySet()) {
            String fileName = entry.getKey();
            if (reviewStoreService.isFreshFileSuccess(commitInfo.getCommitId(), fileName)) {
                log.info("同版本已成功，跳过文件：{}", fileName);
                continue;
            }
            log.info("正在提交 {} 给AI审查...", fileName);
            AiAnalyzeResult result = aiClientService.analyzeCode(
                    commitInfo.getCommitId(),
                    commitInfo.getAuthor(),
                    fileName,
                    entry.getValue(),
                    changedFiles
            );
            if (!result.isSuccess()) {
                log.error("文件 {} 审查失败：{}", fileName, result.getErrorMessage());
                reviewStoreService.markFailed(commitInfo.getCommitId(), result.getErrorMessage());
                return false;

            }
            log.info("AI 审查结果：{}", result.getRawJson());
            reviewStoreService.upsertFileResult(
                    commitInfo.getCommitId(),
                    fileName,
                    entry.getValue(),
                    result.getRawJson()
            );
        }
        reviewStoreService.markSuccess(commitInfo.getCommitId());
        return true;
    }

}

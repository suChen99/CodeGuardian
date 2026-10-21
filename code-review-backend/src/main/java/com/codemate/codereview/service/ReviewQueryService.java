package com.codemate.codereview.service;


import com.codemate.codereview.entity.ReviewCommit;
import com.codemate.codereview.entity.ReviewFile;
import com.codemate.codereview.entity.ReviewIssue;
import com.codemate.codereview.model.AiAnalyzeResult;
import com.codemate.codereview.model.CommitInfo;
import com.codemate.codereview.repository.ReviewCommitRepository;
import com.codemate.codereview.repository.ReviewIssueRepository;
import com.codemate.codereview.vo.CommitVo;
import com.codemate.codereview.vo.FileVo;
import com.codemate.codereview.vo.IssueVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ReviewQueryService {
    private final ReviewCommitRepository commitRepository;
    private final ReviewIssueRepository issueRepository;
    private final ReviewStoreService reviewStoreService;
    private final AIClientService aiClientService;

    public ReviewQueryService(ReviewCommitRepository commitRepository,
                              ReviewIssueRepository issueRepository,
                              ReviewStoreService reviewStoreService,
                              AIClientService aiClientService) {
        this.commitRepository = commitRepository;
        this.issueRepository = issueRepository;
        this.reviewStoreService = reviewStoreService;
        this.aiClientService = aiClientService;
    }

    // 列表：在事务里碰懒加载，才能数 file / issue。
    @Transactional(readOnly = true)
    public List<CommitVo> listCommits() {
        List<CommitVo> result = new ArrayList<>();
        for (ReviewCommit commit : commitRepository.findAllByOrderByCreatedAtDesc()) {
            result.add(toCommitVo(commit, false));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public CommitVo getCommit(Long id) {
        ReviewCommit commit = commitRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "提交不存在：" + id));
        return toCommitVo(commit, true);
    }

    @Transactional(readOnly = true)
    public List<IssueVo> listIssues(String level) {
        List<IssueVo> result = new ArrayList<>();
        for (ReviewIssue issue : issueRepository.findRecent(level)) {
            result.add(toIssueVo(issue, true));
        }
        return result;
    }

    /**
     * 手动再审：用库里上次保存的 diff 立刻调 AI。
     * 不能只改成 PENDING 等定时器——内存 lastReviewed 已经越过这条，调度器扫不到。
     */
    public CommitVo reanalyze(Long id) {
        List<String[]> snaps = loadDiffSnaps(id);
        ReviewCommit commit = commitRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "提交不存在：" + id));
        if (snaps.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有保存的 diff，无法再审");
        }
        CommitInfo info = new CommitInfo();
        info.setCommitId(commit.getCommitHash());
        info.setAuthor(commit.getAuthor());
        info.setMessage(commit.getMessage());
        reviewStoreService.markPending(info, true);

        List<String> changedFiles = new ArrayList<>();
        for (String[] snap : snaps) {
            changedFiles.add(snap[0]);
        }
        for (String[] snap : snaps) {
            String fileName = snap[0];
            String diffText = snap[1];
            AiAnalyzeResult result = aiClientService.analyzeCode(
                    info.getCommitId(), info.getAuthor(), fileName, diffText, changedFiles);
            if (!result.isSuccess()) {
                reviewStoreService.markFailed(info.getCommitId(), result.getErrorMessage());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "再审失败：" + result.getErrorMessage());

            }
            reviewStoreService.upsertFileResult(info.getCommitId(), fileName, diffText, result.getRawJson());
        }

        reviewStoreService.markSuccess(info.getCommitId());
        log.info("手动再审完成：{}", info.getCommitId());
        // markPending 之后 id 不变（更新原行），再查一次详情
        return getCommit(id);
    }

    @Transactional(readOnly = true)
    public List<String[]> loadDiffSnaps(Long id) {
        ReviewCommit commit = commitRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "提交不存在：" + id));
        List<String[]> snaps = new ArrayList<>();
        for (ReviewFile file : commit.getFiles()) {
            if (file.getDiffText() != null && !file.getDiffText().isBlank()) {
                snaps.add(new String[]{file.getFileName(), file.getDiffText()});
            }
        }
        return snaps;
    }

    //  includeFiles = false 只填计数，给列表用
    private CommitVo toCommitVo(ReviewCommit commit, boolean includeFiles) {
        CommitVo vo = new CommitVo();
        vo.setId(commit.getId());
        vo.setCommitHash(commit.getCommitHash());
        vo.setAuthor(commit.getAuthor());
        vo.setMessage(commit.getMessage());
        vo.setStatus(commit.getStatus());
        vo.setErrorMessage(commit.getErrorMessage());
        vo.setCreatedAt(commit.getCreatedAt());
        vo.setUpdatedAt(commit.getUpdatedAt());

        int issueCount = 0;
        List<FileVo> files = new ArrayList<>();
        for (ReviewFile file : commit.getFiles()) {
            issueCount += file.getIssues().size();
            if (includeFiles) {
                FileVo fv = new FileVo();
                fv.setId(file.getId());
                fv.setFileName(file.getFileName());
                fv.setDiffText(file.getDiffText());
                for (ReviewIssue issue : file.getIssues()) {
                    fv.getIssues().add(toIssueVo(issue, false));
                }
                files.add(fv);
            }
        }
        vo.setFileCount(commit.getFiles().size());
        vo.setIssueCount(issueCount);
        vo.setFiles(files);
        return vo;
    }

    private IssueVo toIssueVo(ReviewIssue issue, boolean withCommit) {
        IssueVo vo = new IssueVo();
        vo.setId(issue.getId());
        vo.setLineNo(issue.getLineNo());
        vo.setLevel(issue.getLevel());
        vo.setSuggestion(issue.getSuggestion());
        vo.setSource(issue.getSource());
        if (issue.getFile() != null) {
            vo.setFileName(issue.getFile().getFileName());
            if (withCommit && issue.getFile().getCommit() != null) {
                vo.setCommitId(issue.getFile().getCommit().getId());
                vo.setCommitHash(issue.getFile().getCommit().getCommitHash());
            }
        }
        return vo;
    }

}

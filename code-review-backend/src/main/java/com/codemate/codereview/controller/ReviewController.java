package com.codemate.codereview.controller;
import com.codemate.codereview.service.ReviewQueryService;
import com.codemate.codereview.vo.ApiResponse;
import com.codemate.codereview.vo.CommitVo;
import com.codemate.codereview.vo.IssueVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 给 Vue / Apifox 用的只读 + 再审接口。
 * 扫描仍由 GitScanScheduler 负责，这里不扫 Git。
 */
@RestController
@RequestMapping("/api")
public class ReviewController {
    private final ReviewQueryService reviewQueryService;
    public ReviewController(ReviewQueryService reviewQueryService) {
        this.reviewQueryService = reviewQueryService;
    }

    @GetMapping("/commits")
    public ApiResponse<List<CommitVo>> listCommits() {
        return ApiResponse.ok(reviewQueryService.listCommits());
    }

    @GetMapping("/commits/{id}")
    public ApiResponse<CommitVo> getCommit(@PathVariable Long id) {
        return ApiResponse.ok(reviewQueryService.getCommit(id));
    }

    @GetMapping("/issues")
    public ApiResponse<List<IssueVo>> listIssues(
            @RequestParam(required = false) String level) {
        return ApiResponse.ok(reviewQueryService.listIssues(level));
    }

    @PostMapping("/commits/{id}/reanalyze")
    public ApiResponse<CommitVo> reanalyze(@PathVariable Long id) {
        return ApiResponse.ok(reviewQueryService.reanalyze(id));
    }
}

package com.codemate.codereview.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
@Data


public class CommitVo {
    private Long id;
    private String commitHash;
    private String author;
    private String message;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int fileCount;
    private int issueCount;
    /** 仅详情接口填充 */
    private List<FileVo> files = new ArrayList<>();
}

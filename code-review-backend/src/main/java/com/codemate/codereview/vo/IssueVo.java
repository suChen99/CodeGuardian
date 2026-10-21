package com.codemate.codereview.vo;

import lombok.Data;

@Data
public class IssueVo {
    private Long id;
    private Integer lineNo;
    private String level;
    private String suggestion;
    private String source;
    /** 跨提交列表时用得上 */
    private String fileName;
    private Long commitId;
    private String commitHash;
}

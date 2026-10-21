package com.codemate.codereview.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CommitInfo {
    private String commitId;  // 提交的哈希值
    private String author;    // 提交者名字
    private String message;   // 提交备注
    private List<String> changeFiles;  // 变更的文件列表

    private Map<String,String> fileDiffs; // 新增字段：key:文件名，value=该文件的diff文件
}

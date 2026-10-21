package com.codemate.codereview.vo;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;


@Data
public class FileVo {
    private Long id;
    private String fileName;
    /** 详情页可以展示 diff；列表接口不要带这个字段 */
    private String diffText;
    private List<IssueVo> issues = new ArrayList<>();
}

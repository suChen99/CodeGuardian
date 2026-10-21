package com.codemate.codereview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 一个文件上的一条审查意见。 */
@Getter
@Setter
@Entity
@Table(name = "review_issue")
public class ReviewIssue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private ReviewFile file;
    @Column(name = "line_no")
    private Integer lineNo;
    @Column(length = 20)
    private String level;
    @Column(columnDefinition = "TEXT")
    private String suggestion;
    /**
     * 先一律 LLM。下一步做规则引擎时再写 RULE。
     * 现在就把字段留上，免得以后改表。
     */
    @Column(length = 16)
    private String source = "LLM";
}

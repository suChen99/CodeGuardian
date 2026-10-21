package com.codemate.codereview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
/** 某次提交里的一个变更文件，以及该文件的 AI 原始返回。 */
@Getter
@Setter
@Entity
@Table(name = "review_file", uniqueConstraints = {
        @UniqueConstraint(name = "uk_commit_file", columnNames = {"commit_id", "file_name"})
})
public class ReviewFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commit_id", nullable = false)
    private ReviewCommit commit;
    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;
    /** PENDING / SUCCESS / FAILED。旧行可能为 null，不当成新鲜成功。 */
    @Column(length = 20)
    private String status = "PENDING";
    @Column(name = "profile_version", length = 32)
    private String profileVersion;
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
    /** diff 可能很长，用 LONGTEXT */
    @Lob
    @Column(name = "diff_text", columnDefinition = "LONGTEXT")
    private String diffText;
    @Lob
    @Column(name = "raw_json", columnDefinition = "LONGTEXT")
    private String rawJson;
    @OneToMany(mappedBy = "file", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReviewIssue> issues = new ArrayList<>();
    public void addIssue(ReviewIssue issue) {
        issues.add(issue);
        issue.setFile(this);
    }
}

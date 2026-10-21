package com.codemate.codereview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次 Git 提交的审查总单。
 * commit_hash 唯一：同一条提交只留一行，重试时更新这一行，不插第二条。
 */
@Getter
@Setter
@Entity
@Table(name = "review_commit",uniqueConstraints = {
        @UniqueConstraint(name = "uk_commit_hash", columnNames = "commit_hash")
})
public class ReviewCommit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "commit_hash", nullable = false,length = 64)
    private String commitHash;

    @Column(length = 128)
    private String author;

    @Column(length = 512)
    private String message;

    /** PENDING / SUCCESS / FAILED */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /** 这笔提交最后一次按哪个配置版本审完。null = 升级前的旧数据，不算新鲜。 */
    @Column(name = "profile_version", length = 32)
    private String profileVersion;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 一个提交下有多个文件。
     * CascadeType.ALL：保存提交时文件一起保存。
     * orphanRemoval：丢掉失败/旧版本文件时，对应行和 issue 一起删。
     */
    @OneToMany(mappedBy = "commit",cascade = CascadeType.ALL,orphanRemoval = true)
    private List<ReviewFile> files = new ArrayList<>();
    public void addFile(ReviewFile file) {
        files.add(file);
        file.setCommit(this);
    }
}

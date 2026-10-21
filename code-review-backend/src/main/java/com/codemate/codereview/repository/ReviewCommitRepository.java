package com.codemate.codereview.repository;

import com.codemate.codereview.entity.ReviewCommit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewCommitRepository extends JpaRepository<ReviewCommit,Long> {
    Optional<ReviewCommit> findByCommitHash(String commitHash);
    boolean existsByCommitHashAndStatus(String commitHash,String status);

    // 列表页：新的在前
    List<ReviewCommit> findAllByOrderByCreatedAtDesc();


}

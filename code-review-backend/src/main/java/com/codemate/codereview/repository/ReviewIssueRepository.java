package com.codemate.codereview.repository;

import com.codemate.codereview.entity.ReviewIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ReviewIssueRepository extends JpaRepository<ReviewIssue, Long> {
    /**
     * JOIN FETCH 把 file、commit 一起取出，避免等会儿拼 VO 时懒加载报错。
     * 这里只 FETCH 多对一，不会触发 Hibernate 的 MultipleBag 问题。
     */
    @Query("""
            SELECT i FROM ReviewIssue i
                        JOIN FETCH i.file f
                        JOIN FETCH f.commit c
                        WHERE (:level IS NULL OR :level = '' OR i.level = :level)
                        ORDER BY i.id DESC
            """)
    List<ReviewIssue> findRecent(@Param("level") String level);
}

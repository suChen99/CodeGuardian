package com.codemate.codereview.service;

import com.codemate.codereview.model.CommitInfo;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class GitScanService {
    // 排除部分后缀文件
    private static final Set<String> SKIP_SUFFIXES = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg",
            ".jar", ".class", ".war", ".zip",
            ".lock", ".woff", ".woff2", ".ttf"
    );
    // 从application.yml中读取配置的仓库路径
    @Value("${codemate.repo.path}")
    private String repoPath;
    /**
     * 已成功审查的最后一条 commit。
     * 注意：扫描时不要改它，只有 markReviewed() 才能改。
     */
    private String lastReviewedCommitId = "";

    /**
     * 找出所有「尚未审查成功」的提交，按时间从旧到新。
     * 一次可能返回多条（10 秒内连续 commit 的情况）。
     */
    public List<CommitInfo> scanPendingCommits() {
        List<CommitInfo> pendingInfos = new ArrayList<>();
        try (Repository repository = FileRepositoryBuilder.create(new File(repoPath, ".git"));
             Git git = new Git(repository);
             RevWalk revWalk = new RevWalk(repository)) {
            // 空仓库：还没有任何commit
            ObjectId headId = repository.resolve("HEAD");
            if (headId == null) {
                log.info("仓库还没有任何提交，跳过扫描");
                return pendingInfos;
            }
            RevCommit head = revWalk.parseCommit(headId);
            List<RevCommit> pendingCommits = new ArrayList<>();

            if (lastReviewedCommitId == null || lastReviewedCommitId.isBlank()) {
                // 首次启动：不把历史全部重审，只抓当前HEAD
                pendingCommits.add(head);
            } else {
                ObjectId lastId = repository.resolve(lastReviewedCommitId);
                if (lastId == null) {
                    //比如仓库被force push,上次的哈希已经不存在
                    log.warn("上次记录的 commit 已不存在，回退为只审 HEAD：{}", lastReviewedCommitId);
                    pendingCommits.add(head);

                } else if (lastId.equals(headId)) {
                    //HEAD 没动
                    log.debug("没有新的提交，跳过扫描");
                    return pendingInfos;
                } else {
                    // 从HEAD 往回走，直到碰到已审查 的那条
                    revWalk.markStart(head);
                    revWalk.markUninteresting(revWalk.parseCommit(lastId));
                    for (RevCommit commit : revWalk) {
                        pendingCommits.add(commit);
                    }
                    // 默认新的在前面，翻成旧->新，才能 一个个mark，失败时不会把后面的也跳过
                    Collections.reverse(pendingCommits);
                }
            }
            for (RevCommit commit : pendingCommits) {
                pendingInfos.add(buildCommitInfo(git, repository, commit));
            }
            return pendingInfos;
        } catch (Exception e) {
            // 把异常栈打印出来，方便对照 JGit报错
            log.error("扫描 Git 仓库失败：{}", e.getMessage(), e);
            return pendingInfos;
        }
    }

    /**
     * 仅在该 commit 的所有文件都审查成功后，由Scheduler 调用
     */
    public void markReviewed(String commitId) {
        this.lastReviewedCommitId = commitId;
        log.info("已标记审查完成：{}", commitId);
    }

    // 把一条 RevCommit 转成自己的CommitInfo
    private CommitInfo buildCommitInfo(Git git, Repository repository, RevCommit commit) throws Exception {
        CommitInfo info = new CommitInfo();
        info.setCommitId(commit.getName());
        info.setAuthor(commit.getAuthorIdent().getName());
        info.setMessage(commit.getFullMessage().split("\n")[0]);

        List<String> files = new ArrayList<>();
        Map<String, String> fileDiffs = new HashMap<>();

        if (commit.getParentCount() == 0) {
            // 仓库的第一个commit：没有父节点，做不了标准diff
            // 这里先留下文件名，fileDiffs 为空；Scheduler 会把它当成无需AI，直接标记完成
            log.warn("提交 {} 是首次提交，暂不生成diff", commit.getName());
            info.setChangeFiles(files);
            info.setFileDiffs(fileDiffs);
            return info;
        }

        // 父提交必须parse，否则getTree() 可能是空
        try (RevWalk walk = new RevWalk(repository);
             ObjectReader reader = repository.newObjectReader()) {
            RevCommit parent = walk.parseCommit(commit.getParent(0));
            CanonicalTreeParser oldTree = new CanonicalTreeParser();
            oldTree.reset(reader,parent.getTree());
            CanonicalTreeParser newTree = new CanonicalTreeParser();
            newTree.reset(reader,commit.getTree());

            List<DiffEntry> diffs = git.diff()
                    .setOldTree(oldTree)
                    .setNewTree(newTree)
                    .call();
            for (DiffEntry diff : diffs) {
                // 删除文件的 newPath 是 /dev/null，要用旧路径当名字
                String fileName = diff.getChangeType() == DiffEntry.ChangeType.DELETE
                        ? diff.getOldPath()
                        : diff.getNewPath();
                files.add(fileName);

                if (shouldSkip(fileName)) {
                    log.info("跳过非代码文件：{}", fileName);
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (DiffFormatter formatter = new DiffFormatter(out)) {
                    formatter.setRepository(repository);
                    formatter.format(diff);
                    String diffText = out.toString(StandardCharsets.UTF_8);
                    fileDiffs.put(fileName,diffText);
                    log.info("文件 {} 的变更已提取，长度 {} 字符",fileName,diffText.length());
                }catch (Exception e) {
                    log.warn("生成 {} 的diff 失败：{}",fileName,e.getMessage());
                }
            }
        }
        info.setChangeFiles(files);
        info.setFileDiffs(fileDiffs);
        log.info("提交 {} 变更文件：{}", commit.getName(),files);
        return info;
    }
    // 按后缀判断要不要送给AI
    private boolean shouldSkip(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String suffix : SKIP_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

}

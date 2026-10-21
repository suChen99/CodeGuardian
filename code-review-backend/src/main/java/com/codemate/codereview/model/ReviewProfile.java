package com.codemate.codereview.model;

/**
 * 审查配置版本。改 classifier / rules / prompt / 合并策略时加一号。
 * 幂等键：commit_hash + file_name + VERSION。
 */
public final class ReviewProfile {
    public static final String VERSION = "v2-classify-1";

    private ReviewProfile() {
    }
}

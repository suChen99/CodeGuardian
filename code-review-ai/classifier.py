"""
变更分类：只看文件名 + 本文件 Diff + 本次提交文件清单。
不调大模型。判错可以复现，不要靠 prompt。
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Iterable, List, Optional, Set, Tuple

# 文档 / 配置：只看后缀，不看内容
_DOC_EXT = (".md", ".txt", ".rst")
_CONFIG_EXT = (".yml", ".yaml", ".properties", ".xml", ".json", ".env")
_SOURCE_EXT = (".java", ".kt", ".js", ".ts", ".vue", ".py")

# 路径里出现这些词，当敏感文件（宁多勿少，后面可收）
_SENSITIVE = ("auth", "security", "login", "jwt", "password", "permission", "oauth")

# public/protected 方法或构造器：public void foo(String a)
_SIG_RE = re.compile(
    r"^\s*(?:public|protected)\s+"
    r"(?:(?:static|final|synchronized|default|native)\s+)*"
    r"(?:[\w.<>,\[\]?]+\s+)?"
    r"(\w+)\s*\(([^)]*)\)"
)


@dataclass
class ChangeDecision:
    kind: str          # FORMAT / DOCS / CONFIG / SIGNATURE / BODY / UNKNOWN
    impact: str        # LOCAL / MAY_AFFECT_CALLERS
    intensity: str     # RULES_ONLY / RULES_AND_LLM / RULES_LLM_HUMAN
    need_expand: bool  # True = 签名变了且本次没看见其他源码文件
    reason: str        # 给人看的一句话

    def human_hint(self) -> Optional[str]:
        """只有「要人看」时才返回文案，会变成一条 line=0 的 RULE INFO。"""
        if self.intensity != "RULES_LLM_HUMAN":
            return None
        if self.need_expand:
            return (
                "检测到 public/protected 方法签名变更，"
                "且本次提交未见其他源码文件。建议人工确认调用链是否已同步。"
            )
        return "该文件路径涉及认证/权限，建议人工复核。"


def classify_change(
        file_name: str,
        diff_text: str,
        changed_files: Optional[Iterable[str]] = None,
) -> ChangeDecision:
    files = [p.replace("\\", "/") for p in (changed_files or []) if p]
    if file_name and file_name.replace("\\", "/") not in files:
        files.append(file_name.replace("\\", "/"))

    added, removed = _changed_code_lines(diff_text)
    semantic_added = [x for x in added if not _is_noise(x)]
    semantic_removed = [x for x in removed if not _is_noise(x)]

    # 1) 纯格式：+/- 去掉空白/注释后什么都不剩
    if not semantic_added and not semantic_removed:
        return _decision("FORMAT", "LOCAL", "RULES_ONLY", False, "仅空白或注释")

    # 2) 文档：改的是字，但按产品约定不烧模型
    if _has_ext(file_name, _DOC_EXT):
        return _decision("DOCS", "LOCAL", "RULES_ONLY", False, "文档文件")

    # 3) import 增删算语义，不能当格式
    # 4) 配置
    if _has_ext(file_name, _CONFIG_EXT):
        return _finish(
            file_name, files,
            kind="CONFIG",
            signature=False,
            reason="配置文件变更",
        )

    signature = _public_signature_changed(semantic_added, semantic_removed)
    kind = "SIGNATURE" if signature else "BODY"
    reason = "public/protected 签名变更" if signature else "方法体或普通代码变更"
    return _finish(file_name, files, kind=kind, signature=signature, reason=reason)


def _finish(file_name: str, files: List[str], kind: str, signature: bool, reason: str) -> ChangeDecision:
    impact = "MAY_AFFECT_CALLERS" if signature else "LOCAL"
    others = _other_source_files(file_name, files)
    # 签名变了，且本次只有这一个源码文件 → 调用方不在这次 Diff 里
    need_expand = signature and not others
    sensitive = _is_sensitive(file_name)
    if need_expand or sensitive:
        intensity = "RULES_LLM_HUMAN"
    else:
        intensity = "RULES_AND_LLM"
    return _decision(kind, impact, intensity, need_expand, reason)


def _decision(kind: str, impact: str, intensity: str, need_expand: bool, reason: str) -> ChangeDecision:
    return ChangeDecision(kind, impact, intensity, need_expand, reason)


def _changed_code_lines(diff_text: str) -> Tuple[List[str], List[str]]:
    """只收集 + / - 的代码，丢掉 +++ / --- / 上下文。"""
    added, removed = [], []
    for raw in (diff_text or "").splitlines():
        if raw.startswith("+++") or raw.startswith("---"):
            continue
        if raw.startswith("+"):
            added.append(raw[1:])
        elif raw.startswith("-"):
            removed.append(raw[1:])
    return added, removed


def _is_noise(code: str) -> bool:
    s = (code or "").strip()
    if not s:
        return True
    return s.startswith("//") or s.startswith("#") or s.startswith("*")


def _has_ext(file_name: str, exts: Tuple[str, ...]) -> bool:
    lower = (file_name or "").replace("\\", "/").lower()
    return any(lower.endswith(ext) for ext in exts)


def _is_sensitive(file_name: str) -> bool:
    lower = (file_name or "").replace("\\", "/").lower()
    return any(key in lower for key in _SENSITIVE)


def _other_source_files(file_name: str, files: List[str]) -> List[str]:
    me = (file_name or "").replace("\\", "/")
    out = []
    for path in files:
        if path == me:
            continue
        if _has_ext(path, _SOURCE_EXT):
            out.append(path)
    return out


def _public_signature_changed(added: List[str], removed: List[str]) -> bool:
    """
    把 + / - 里的 public 方法收成集合，两边不一致就是签名变了。
    只改方法体时，签名行一般是上下文（空格开头），不会进这里。
    """
    a = _signatures(added)
    b = _signatures(removed)
    if not a and not b:
        return False
    return a != b


def _signatures(lines: List[str]) -> Set[Tuple[str, str]]:
    found: Set[Tuple[str, str]] = set()
    for line in lines:
        m = _SIG_RE.search(line)
        if not m:
            continue
        name = m.group(1)
        params = re.sub(r"\s+", " ", (m.group(2) or "").strip())
        found.add((name, params))
    return found
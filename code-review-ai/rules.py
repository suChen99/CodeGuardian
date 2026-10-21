"""
确定性规则：只检查 diff 里新增的行（以 + 开头，排除 +++）。
不调用网络，失败也不应拖垮整个审查。
"""
from typing import List, Tuple


def iter_added_lines(diff_text: str) -> List[Tuple[int, str]]:
    """
    遍历新增代码。
    返回 (源文件行号, 去掉 + 之后的代码)。
    源文件行号来自 hunk 头：@@ -旧起点,行数 +新起点,行数 @@
    """
    result = []
    src_line = 0
    for raw in diff_text.splitlines():
        if raw.startswith("@@"):
            # 例：@@ -10,7 +12,9 @@
            try:
                plus = raw.split("+", 1)[1].split(" ")[0]
                src_line = int(plus.split(",")[0])
            except (IndexError, ValueError):
                src_line = 0
            continue
        if raw.startswith("+++") or raw.startswith("---"):
            continue
        if raw.startswith("+"):
            result.append((src_line, raw[1:]))
            src_line += 1
        elif raw.startswith("-"):
            # 删除行不增加「新文件」行号
            continue
        else:
            # 上下文行（前导空格）
            src_line += 1
    return result


def analyze_rules(diff_text: str) -> list:
    """
    返回与 FastAPI ReviewIssue 字段一致的 dict 列表。
    这里不 import main，避免和 FastAPI 循环引用。
    """
    issues = []
    for line_no, code in iter_added_lines(diff_text):
        stripped = code.strip()
        if not stripped or stripped.startswith("//"):
            continue

        if "System.out.println" in stripped or "System.err.println" in stripped:
            issues.append({
                "line": line_no,
                "level": "WARN",
                "suggestion": "生产代码不要用 System.out/err.println，请改用日志框架（如 @Slf4j + log.info）。",
                "source": "RULE",
            })

        # 同一行空 catch：catch (Exception e) {}
        if "catch" in stripped and "{" in stripped and "}" in stripped:
            inner = stripped[stripped.find("{") + 1:stripped.rfind("}")].strip()
            if inner == "" or inner.startswith("//"):
                issues.append({
                    "line": line_no,
                    "level": "ERROR",
                    "suggestion": "捕获异常后不能空着，至少要打日志或向上抛出。",
                    "source": "RULE",
                })

        if "printStackTrace()" in stripped:
            issues.append({
                "line": line_no,
                "level": "WARN",
                "suggestion": "不要用 printStackTrace()，请使用 log.error(..., e)。",
                "source": "RULE",
            })

    return issues

def collect_diff_window_lines(diff_text:str) -> set:
    """
    本次 Diff 窗口内的「新文件」行号。
    包含：新增行（+）、上下文行（行首空格）。
    不包含：删除行（-）——删除行没有新文件行号。
    """

    window = set()
    src_line = 0
    for raw in diff_text.splitlines():
        if raw.startswith("@@"):
            try:
                plus = raw.split("+", 1)[1].split(" ")[0]
                src_line = int(plus.split(",")[0])
            except (IndexError, ValueError):
                src_line = 0
            continue
        if raw.startswith("+++") or raw.startswith("---"):
            continue
        if raw.startswith("\\"):
           continue
        if raw.startswith("+"):
            window.add(src_line)
            src_line += 1
        elif raw.startswith("-"):
            continue
        else:
            # 上下文：unified diff 以空格开头，也有可能是空行
            window.add(src_line)
            src_line += 1
    return window
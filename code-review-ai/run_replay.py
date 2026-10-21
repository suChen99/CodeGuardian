"""
回放 20 组确定性用例。
在 code-review-ai 目录执行：python run_replay.py
不调用硅基流动。
"""
import sys
import types
from collections import Counter


def _stub_ai_stack_if_needed():
    """回放只测确定性层，缺 openai/fastapi 时不要挡着跑。"""
    if "openai" not in sys.modules:
        try:
            import openai  # noqa: F401
        except ImportError:
            fake = types.ModuleType("openai")
            fake.OpenAI = lambda **kwargs: object()
            sys.modules["openai"] = fake


_stub_ai_stack_if_needed()

from classifier import classify_change
from main import ReviewIssue, filter_llm_by_anchor, merge_issues
from replay_cases import CASES
from rules import analyze_rules


def _as_issues(raw_list):
    issues = []
    for item in raw_list or []:
        if isinstance(item, ReviewIssue):
            issues.append(item)
        else:
            issues.append(ReviewIssue(**item))
    return issues


def _check(case: dict) -> list:
    errors = []
    exp = case["expect"]
    decision = classify_change(
        case["file_name"],
        case["diff"],
        case.get("changed_files"),
    )
    rules = analyze_rules(case["diff"])
    injected = _as_issues(case.get("injected_llm"))
    kept = filter_llm_by_anchor(case["diff"], injected)
    merged = merge_issues(rules, kept)

    mapping = {
        "kind": decision.kind,
        "impact": decision.impact,
        "intensity": decision.intensity,
        "need_expand": decision.need_expand,
    }
    for key, actual in mapping.items():
        if key in exp and actual != exp[key]:
            errors.append(f"{key} 实际={actual!r} 期望={exp[key]!r}")

    if "human" in exp:
        has_hint = decision.human_hint() is not None
        if has_hint != exp["human"]:
            errors.append(f"human 实际={has_hint} 期望={exp['human']}")

    if "rule_count" in exp and len(rules) != exp["rule_count"]:
        errors.append(f"rule_count 实际={len(rules)} 期望={exp['rule_count']}")

    blob = " ".join(item.get("suggestion", "") for item in rules)
    for needle in exp.get("rule_contains") or []:
        if needle not in blob:
            errors.append(f"规则建议里没有 {needle!r}")

    if "rule_line" in exp:
        lines = [item["line"] for item in rules]
        if exp["rule_line"] not in lines:
            errors.append(f"规则行号实际={lines} 期望含 {exp['rule_line']}")

    if "rule_level" in exp:
        levels = [item["level"] for item in rules]
        if exp["rule_level"] not in levels:
            errors.append(f"规则级别实际={levels} 期望含 {exp['rule_level']}")

    kept_lines = {item.line for item in kept}
    for line in exp.get("drop_llm_lines") or []:
        if line in kept_lines:
            errors.append(f"应丢弃的 LLM 行还在：{line}")
    for line in exp.get("keep_llm_lines") or []:
        if line not in kept_lines:
            errors.append(f"应保留的 LLM 行丢了：{line}")

    by_line = {item.line: item for item in merged}
    for line, level in (exp.get("merged_line_level") or {}).items():
        item = by_line.get(line)
        if item is None:
            errors.append(f"合并结果缺少行 {line}")
        elif item.level != level:
            errors.append(f"行 {line} 级别实际={item.level} 期望={level}")
    for line, source in (exp.get("merged_line_source") or {}).items():
        item = by_line.get(line)
        if item is None:
            errors.append(f"合并结果缺少行 {line}")
        elif item.source != source:
            errors.append(f"行 {line} 来源实际={item.source} 期望={source}")

    return errors


def main():
    failed = 0
    tags = Counter()
    print(f"回放 {len(CASES)} 组\n")
    for case in CASES:
        errors = _check(case)
        tags[case["tag"]] += 1
        if errors:
            failed += 1
            print(f"[FAIL] {case['id']} {case['tag']} {case['title']}")
            for err in errors:
                print(f"       - {err}")
        else:
            print(f"[PASS] {case['id']} {case['tag']} {case['title']}")

    passed = len(CASES) - failed
    print(f"\n合计 {passed}/{len(CASES)} 通过  失败 {failed}")
    print("标签：", dict(tags))
    if failed:
        raise SystemExit(1)


if __name__ == "__main__":
    main()

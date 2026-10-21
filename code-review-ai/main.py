"""
CodeMate AI 审查服务  Python端
功能：接收Java传来的diff文本，返回审查建议
"""
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Optional, List
import uvicorn
import logging
import json
from openai import OpenAI
import os
from rules import analyze_rules, collect_diff_window_lines
from classifier import classify_change

def _load_dotenv(path: str) -> None:
    """读取本地 .env，不覆盖已经在系统里的变量。不要把 .env 提交进 Git。"""
    if not os.path.isfile(path):
        return
    with open(path, encoding="utf-8") as handle:
        for raw in handle:
            line = raw.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            key = key.strip()
            value = value.strip().strip('"').strip("'")
            if key and key not in os.environ:
                os.environ[key] = value


_load_dotenv(os.path.join(os.path.dirname(__file__), ".env"))

SILICON_API_KEY = os.environ.get("SILICON_API_KEY", "").strip()
BASE_URL = os.environ.get("SILICON_BASE_URL", "https://api.siliconflow.cn/v1").strip()
MODEL_NAME = os.environ.get("SILICON_MODEL", "Qwen/Qwen2.5-7B-Instruct").strip()

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

if not SILICON_API_KEY:
    logger.warning("未设置 SILICON_API_KEY。复制 .env.example 为 .env 后再启动审查服务。")

client = OpenAI(
    api_key=SILICON_API_KEY or "missing-key",
    base_url=BASE_URL,
)

# 创建FastAPI
app = FastAPI(
    title="CodeMate AI 审查引擎",
    description="接收Java后端传来的代码diff，进行智能审查",
    version="1.0"
)


# ==============定义请求/响应数据结构=========

class ReviewRequest(BaseModel):
    """Java端发来的请求体"""
    commit_id: str  # 提交哈希
    author: str  # 提交者
    file_name: str  # 文件名
    diff_text: str  # 变更的代码（git diff 格式）
    repo_path: Optional[str] = None  # 仓库路径
    changed_files: Optional[List[str]] = None  # 变更的文件列表


class ReviewIssue(BaseModel):
    """审查结果中的单个问题"""
    line: int  # 行号
    level: str  # "ERROR" 或 "WARN"
    suggestion: str  # 修复建议
    source: str = "LLM"


class ReviewResponse(BaseModel):
    """返回给Java的响应体"""
    commit_id: str
    file_name: str
    issues: List[ReviewIssue]  # 问题列表
    summary: str  # 一句话总结


def filter_llm_by_anchor(
        diff_text: str,
        llm_issues: List[ReviewIssue]) -> List[ReviewIssue]:
    """
    有锚定：代码意见的 line 必须在 Diff 窗口内。
    line==0 的系统提示（INFO / 解析失败）不按代码行校验。
    """
    window = collect_diff_window_lines(diff_text)
    kept = []
    for item in llm_issues:
        is_system = item.line == 0 and (
                item.level == "INFO" or "解析" in (item.suggestion or "")
        )
        if is_system:
            kept.append(item)
            continue
        if item.line in window:
            kept.append(item)
        else:
            logger.info("丢弃无锚点LLM意见：line=%s,%s", item.line, item.suggestion[:80])
    return kept


# ===========核心业务逻辑=========
def merge_issues(rule_issues: list, llm_issues: List[ReviewIssue]) -> List[ReviewIssue]:
    """同一行：规则优先，丢掉模型的重复。INFO 仅在完全没问题时保留一条。"""
    rules = [ReviewIssue(**item) for item in rule_issues]
    occupied = {i.line: i for i in rules if i.line}
    merged = list(rules)
    for item in llm_issues:
        if item.level == "INFO":
            continue
        if item.line in occupied:
            """ 双保险：即使以后改了（丢掉同行），也不许ERROR盖过规则的WARN"""
            if _rank(item.level) > _rank(occupied[item.line].level):
                logger.info("忽略模型上调严重度：line=%s %s -> %s",
                            item.line, occupied[item.line].level, item.level)
            continue
        merged.append(item)
    if not merged:
        merged.append(ReviewIssue(
            line=0,
            level="INFO",
            suggestion="✅ 规则与 AI 均未发现明显问题。",
            source="LLM",
        ))
    return merged


def analyze_llm(diff_text: str) -> List[ReviewIssue]:
    """只负责调大模型并解析 JSON，不要在这里跑规则。"""
    """
        使用AI审查
        """
    try:
        # 1、构造Prompt
        system_prompt = """你是一位拥有10年经验的Java架构师，擅长代码审查。请分析以下代码变更（git diff格式），找出其中问题。
                            审查要点：
                            1. 空指针风险（NPE）：检查是否调用了可能为 null 的对象方法
                            2. 资源泄漏：检查文件流、数据库连接等是否有关闭
                            3. 事务边界：检查 @Transactional 是否合理使用
                            4. 性能问题：检查循环内是否有不必要的数据库查询

                            输出要求（重要！）：
                            - 不要再报 System.out.println、空 catch、printStackTrace，这些由规则引擎检查
                            - 每个问题仍只要 line、level、suggestion（不要写 source）
                            - 必须返回一个 JSON 对象，不要返回数组
                            - 格式必须是：{"issues":[{"line":整数,"level":"ERROR或WARN","suggestion":"修复建议"}]}
                            - 可以有多条问题，都放在 issues 数组里
                            - 如果没有任何问题，返回：{"issues":[]}
                            - 不要输出 JSON 以外的任何文字、不要重复字段、不要留大段空白
                            - line 必须是 git diff 中「新文件」的行号，且该行必须出现在本次 diff 的 + 行或上下文行
                            - 不要报告 diff 窗口之外的旧代码行号
                            """
        user_prompt = f"请审查以下代码变更：\n```diff\n{diff_text}\n```"

        # 2、调用API
        response = client.chat.completions.create(
            model=MODEL_NAME,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt}
            ],
            temperature=0.1,  # 降低随机性
            max_tokens=800,  # 审查结果很短，不限制模型可能灌几万空白把JSON写坏
            response_format={"type": "json_object"}  # 强制返回JSON
        )

        # 3、提取AI返回的内容
        ai_response = response.choices[0].message.content
        logger.info(f"AI 原始响应：{ai_response}")

        # 4、解析JSON
        data = json.loads(ai_response)

        # 5、先把各种返回归一成list[dict]
        raw_issues = None
        if isinstance(data, list):
            # 模型直接回了数组
            raw_issues = data
        elif isinstance(data, dict) and isinstance(data.get("issues"), list):
            # 标准形状
            raw_issues = data["issues"]
        elif isinstance(data, dict) and "suggestion" in data:
            raw_issues = [data]

        issues = []
        if raw_issues is None:
            issues.append(ReviewIssue(
                line=0,
                level="INFO",
                suggestion=f"AI 返回非标准格式，原始内容：{str(ai_response)[:200]}"
            ))
        else:
            for item in raw_issues:
                if not isinstance(item, dict):
                    continue
                issues.append(ReviewIssue(
                    line=int(item.get("line", 0) or 0),
                    level=item.get("level", "WARN"),
                    suggestion=item.get("suggestion", "无建议"),
                    source="LLM",
                ))
        if not issues:
            issues.append(ReviewIssue(
                line=0,
                level="INFO",
                suggestion="✅ AI 审查未发现明显问题，代码质量良好！"
            ))
        return issues
    except Exception as e:
        logger.error(f"AI 分析失败：{e}")
        # 这里多半是 JSON 解析失败，不是 API Key 无效
        return [ReviewIssue(
            line=0,
            level="ERROR",
            suggestion=f"AI 返回无法解析。错误：{str(e)}"
        )]


def analyze_code(
        diff_text: str,
        file_name: str = "",
        changed_files: Optional[List[str]] = None,
) -> List[ReviewIssue]:
    decision = classify_change(file_name, diff_text, changed_files)
    logger.info(
        "变更分类 file=%s kind=%s impact=%s intensity=%s expand=%s reason=%s",
        file_name, decision.kind, decision.impact,
        decision.intensity, decision.need_expand, decision.reason,
    )

    rule_issues = analyze_rules(diff_text)

    # 格式 / 文档：不烧模型
    if decision.intensity == "RULES_ONLY":
        merged = [ReviewIssue(**item) for item in rule_issues]
        if not merged:
            merged.append(ReviewIssue(
                line=0,
                level="INFO",
                suggestion="格式/文档类变更，仅跑规则，未调用大模型。",
                source="RULE",
            ))
    else:
        try:
            llm_issues = analyze_llm(diff_text)
        except Exception as e:
            logger.error(f"AI 分析失败：{e}")
            llm_issues = [ReviewIssue(
                line=0, level="ERROR",
                suggestion=f"AI 返回无法解析。错误：{e}",
                source="LLM",
            )]
        llm_issues = filter_llm_by_anchor(diff_text, llm_issues)
        merged = merge_issues(rule_issues, llm_issues)

    hint = decision.human_hint()
    if hint:
        merged.append(ReviewIssue(
            line=0, level="INFO", suggestion=hint, source="RULE",
        ))
    return merged


_LEVEL_RANK = {"INFO": 0, "WARN": 1, "ERROR": 2}


def _rank(level: str) -> int:
    return _LEVEL_RANK.get((level or "WARN").upper(), 2)


# ==============定义API接口=============
@app.post("/analyze", response_model=ReviewResponse)
async def analyze_diff(request: ReviewRequest):
    """
    接收Java端发送的diff文本，进行代码审查
    :param request:
    :return:
    """
    logger.info(f"收到审查请求：commit={request.commit_id},file={request.file_name}")

    # 1、检查入参
    if not request.diff_text or len(request.diff_text.strip()) == 0:
        raise HTTPException(status_code=400, detail="diff_text 不能为空")

    # 2、调用审查逻辑
    issues = analyze_code(
        request.diff_text,
        request.file_name,
        request.changed_files,
        )

    # 3、生成一句话摘要
    summary = f"共发现 {len([i for i in issues if i.level != 'INFO'])} 个问题，{len([i for i in issues if i.level == 'INFO'])} 个提示"

    # 4、返回结果
    return ReviewResponse(
        commit_id=request.commit_id,
        file_name=request.file_name,
        issues=issues,
        summary=summary
    )


# ============健康检查接口==========

@app.get("/health")
async def health_check():
    return {"status": "healthy", "service": "CodeMate AI"}


# =============启动服务===========

if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8000,
        reload=True  # 开发模式：代码修改后自动重启
    )

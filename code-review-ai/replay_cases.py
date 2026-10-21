"""
20 组回放用例。只测确定性层：分类、规则、锚定、合并。
不把 Qwen 结果写进期望——模型不稳定，不能当回归基线。
"""

# 公共 hunk：+ 行的新文件行号是 11
_HUNK_AT_11 = """\
--- a/src/demo/Foo.java
+++ b/src/demo/Foo.java
@@ -10,3 +10,4 @@
     public void run() {
+        {CODE}
         doWork();
     }
"""


def _hunk(code: str) -> str:
    return _HUNK_AT_11.replace("{CODE}", code)


CASES = [
    {
        "id": "01",
        "tag": "TP",
        "title": "新增 System.out.println → RULE WARN",
        "file_name": "src/demo/Foo.java",
        "changed_files": ["src/demo/Foo.java"],
        "diff": _hunk('System.out.println("x");'),
        "expect": {
            "kind": "BODY",
            "intensity": "RULES_AND_LLM",
            "need_expand": False,
            "rule_count": 1,
            "rule_contains": ["System.out"],
            "rule_line": 11,
            "rule_level": "WARN",
        },
    },
    {
        "id": "02",
        "tag": "TP",
        "title": "同一行空 catch → RULE ERROR",
        "file_name": "src/demo/Foo.java",
        "changed_files": ["src/demo/Foo.java"],
        "diff": _hunk("} catch (Exception e) {}"),
        "expect": {
            "kind": "BODY",
            "rule_count": 1,
            "rule_contains": ["空着"],
            "rule_line": 11,
            "rule_level": "ERROR",
        },
    },
    {
        "id": "03",
        "tag": "TP",
        "title": "printStackTrace → RULE WARN",
        "file_name": "src/demo/Foo.java",
        "changed_files": ["src/demo/Foo.java"],
        "diff": _hunk("e.printStackTrace();"),
        "expect": {
            "kind": "BODY",
            "rule_count": 1,
            "rule_contains": ["printStackTrace"],
            "rule_line": 11,
            "rule_level": "WARN",
        },
    },
    {
        "id": "04",
        "tag": "TP",
        "title": "单文件 public 签名变更 → 需扩围 + 要人看",
        "file_name": "src/demo/UserService.java",
        "changed_files": ["src/demo/UserService.java"],
        "diff": """\
--- a/src/demo/UserService.java
+++ b/src/demo/UserService.java
@@ -1,5 +1,5 @@
 public class UserService {
-    public String hello(String name) {
+    public String hello(String name, int flag) {
         return name;
     }
 }
""",
        "expect": {
            "kind": "SIGNATURE",
            "impact": "MAY_AFFECT_CALLERS",
            "intensity": "RULES_LLM_HUMAN",
            "need_expand": True,
            "human": True,
            "rule_count": 0,
        },
    },
    {
        "id": "05",
        "tag": "TP",
        "title": "路径含 auth → 要人看（即使不是签名）",
        "file_name": "src/demo/AuthService.java",
        "changed_files": ["src/demo/AuthService.java"],
        "diff": """\
--- a/src/demo/AuthService.java
+++ b/src/demo/AuthService.java
@@ -3,3 +3,4 @@
     public void login() {
+        int x = 1;
         check();
     }
""",
        "expect": {
            "kind": "BODY",
            "need_expand": False,
            "intensity": "RULES_LLM_HUMAN",
            "human": True,
        },
    },
    {
        "id": "06",
        "tag": "TP",
        "title": "同一行 println + printStackTrace → 两条 RULE",
        "file_name": "src/demo/Foo.java",
        "changed_files": ["src/demo/Foo.java"],
        "diff": _hunk('System.out.println(e); e.printStackTrace();'),
        "expect": {
            "rule_count": 2,
            "rule_contains": ["System.out", "printStackTrace"],
        },
    },
    {
        "id": "07",
        "tag": "FP",
        "title": "只加注释 → FORMAT，不跑模型，无规则命中",
        "file_name": "src/demo/Foo.java",
        "changed_files": ["src/demo/Foo.java"],
        "diff": _hunk("// format only"),
        "expect": {
            "kind": "FORMAT",
            "intensity": "RULES_ONLY",
            "need_expand": False,
            "human": False,
            "rule_count": 0,
        },
    },
    {
        "id": "08",
        "tag": "FP",
        "title": "README 改字 → DOCS，只跑规则",
        "file_name": "README.md",
        "changed_files": ["README.md"],
        "diff": """\
--- a/README.md
+++ b/README.md
@@ -1,2 +1,2 @@
-old intro
+This is the test repo.
""",
        "expect": {
            "kind": "DOCS",
            "intensity": "RULES_ONLY",
            "rule_count": 0,
        },
    },
    {
        "id": "09",
        "tag": "FP",
        "title": "只加空行 → FORMAT（分类器不把去空白后相同的缩进当格式）",
        "file_name": "src/demo/Foo.java",
        "changed_files": ["src/demo/Foo.java"],
        "diff": """\
--- a/src/demo/Foo.java
+++ b/src/demo/Foo.java
@@ -1,2 +1,3 @@
 public class Foo {
+
     void a() {}
 }
""",
        "expect": {
            "kind": "FORMAT",
            "intensity": "RULES_ONLY",
            "rule_count": 0,
        },
    },
    {
        "id": "10",
        "tag": "FP",
        "title": "LLM 假行号 9999 必须丢掉",
        "file_name": "src/demo/Foo.java",
        "changed_files": ["src/demo/Foo.java"],
        "diff": _hunk("int y = 2;"),
        "injected_llm": [
            {"line": 9999, "level": "ERROR", "suggestion": "假意见，应被丢掉", "source": "LLM"},
        ],
        "expect": {
            "drop_llm_lines": [9999],
            "keep_llm_lines": [],
        },
    },
    {
        "id": "11",
        "tag": "FP",
        "title": "LLM 不得把规则 WARN 上调成 ERROR",
        "file_name": "src/demo/Foo.java",
        "changed_files": ["src/demo/Foo.java"],
        "diff": _hunk('System.out.println("x");'),
        "injected_llm": [
            {"line": 11, "level": "ERROR", "suggestion": "这比规则更严重", "source": "LLM"},
        ],
        "expect": {
            "keep_llm_lines": [11],
            "merged_line_level": {11: "WARN"},
            "merged_line_source": {11: "RULE"},
        },
    },
    {
        "id": "12",
        "tag": "FP",
        "title": "新增 import 算语义，但不应误报 println",
        "file_name": "src/demo/Foo.java",
        "changed_files": ["src/demo/Foo.java"],
        "diff": """\
--- a/src/demo/Foo.java
+++ b/src/demo/Foo.java
@@ -1,3 +1,4 @@
 package demo;
+import java.util.List;
 public class Foo {}
""",
        "expect": {
            "kind": "BODY",
            "intensity": "RULES_AND_LLM",
            "rule_count": 0,
        },
    },
    {
        "id": "13",
        "tag": "ROUTE",
        "title": "签名变更但本次还有另一个 Java → 不扩围",
        "file_name": "src/demo/UserService.java",
        "changed_files": [
            "src/demo/UserService.java",
            "src/demo/UserController.java",
        ],
        "diff": """\
--- a/src/demo/UserService.java
+++ b/src/demo/UserService.java
@@ -1,5 +1,5 @@
 public class UserService {
-    public String hello(String name) {
+    public String hello(String name, int flag) {
         return name;
     }
 }
""",
        "expect": {
            "kind": "SIGNATURE",
            "need_expand": False,
            "intensity": "RULES_AND_LLM",
            "human": False,
        },
    },
    {
        "id": "14",
        "tag": "ROUTE",
        "title": "只改方法体（签名是上下文）→ BODY，不是 SIGNATURE",
        "file_name": "src/demo/UserService.java",
        "changed_files": ["src/demo/UserService.java"],
        "diff": """\
--- a/src/demo/UserService.java
+++ b/src/demo/UserService.java
@@ -1,6 +1,6 @@
 public class UserService {
     public String hello(String name) {
-        return name;
+        return "hi " + name;
     }
 }
""",
        "expect": {
            "kind": "BODY",
            "impact": "LOCAL",
            "need_expand": False,
            "intensity": "RULES_AND_LLM",
        },
    },
    {
        "id": "15",
        "tag": "GROUND",
        "title": "LLM 意见落在 + 行上 → 保留",
        "file_name": "src/demo/Foo.java",
        "changed_files": ["src/demo/Foo.java"],
        "diff": _hunk("int y = 2;"),
        "injected_llm": [
            {"line": 11, "level": "WARN", "suggestion": "注意这个赋值", "source": "LLM"},
        ],
        "expect": {
            "keep_llm_lines": [11],
            "drop_llm_lines": [],
        },
    },
    {
        "id": "16",
        "tag": "GROUND",
        "title": "LLM 意见落在 hunk 外 → 丢掉",
        "file_name": "src/demo/Foo.java",
        "changed_files": ["src/demo/Foo.java"],
        "diff": _hunk("int y = 2;"),
        "injected_llm": [
            {"line": 3, "level": "ERROR", "suggestion": "旧代码行，不在窗口", "source": "LLM"},
        ],
        "expect": {
            "drop_llm_lines": [3],
        },
    },
    {
        "id": "17",
        "tag": "ROUTE",
        "title": "yml 配置变更 → CONFIG，仍走模型",
        "file_name": "src/main/resources/application.yml",
        "changed_files": ["src/main/resources/application.yml"],
        "diff": """\
--- a/src/main/resources/application.yml
+++ b/src/main/resources/application.yml
@@ -1,2 +1,2 @@
-server.port: 8080
+server.port: 9090
""",
        "expect": {
            "kind": "CONFIG",
            "intensity": "RULES_AND_LLM",
            "need_expand": False,
        },
    },
    {
        "id": "18",
        "tag": "GROUND",
        "title": "line=0 的系统 INFO 不按锚点丢弃",
        "file_name": "src/demo/Foo.java",
        "changed_files": ["src/demo/Foo.java"],
        "diff": _hunk("int y = 2;"),
        "injected_llm": [
            {"line": 0, "level": "INFO", "suggestion": "AI 返回无法解析。", "source": "LLM"},
        ],
        "expect": {
            "keep_llm_lines": [0],
        },
    },
    {
        "id": "19",
        "tag": "TP",
        "title": "单文件 protected 签名变更 → 同样需扩围",
        "file_name": "src/demo/UserService.java",
        "changed_files": ["src/demo/UserService.java"],
        "diff": """\
--- a/src/demo/UserService.java
+++ b/src/demo/UserService.java
@@ -1,5 +1,5 @@
 public class UserService {
-    protected void save(String id) {
+    protected void save(String id, boolean dryRun) {
         persist(id);
     }
 }
""",
        "expect": {
            "kind": "SIGNATURE",
            "need_expand": True,
            "intensity": "RULES_LLM_HUMAN",
            "human": True,
        },
    },
    {
        "id": "20",
        "tag": "FP",
        "title": "注释里的 println 不能当规则命中",
        "file_name": "src/demo/Foo.java",
        "changed_files": ["src/demo/Foo.java"],
        "diff": _hunk('// System.out.println("bait");'),
        "expect": {
            "kind": "FORMAT",
            "intensity": "RULES_ONLY",
            "rule_count": 0,
        },
    },
]

# -*- coding: utf-8 -*-
"""把 docs/openapi.json 转成可读的 Markdown 接口文档 docs/api.md

用法：
    1. 启动应用（或在跑完 tools/api-test.ps1 之后，它会导出 openapi.json）
    2. python tools/gen-api-md.py
"""
import io
import json
import os

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPEC_PATH = os.path.join(BASE, "docs", "openapi.json")
OUT_PATH = os.path.join(BASE, "docs", "api.md")

METHODS = ["get", "post", "put", "delete", "patch"]

# 分组展示顺序：按使用顺序排，未列出的分组排在后面
TAG_ORDER = ["认证接口", "用户资料接口", "文件上传接口", "读书笔记接口", "点赞接口"]


def resolve(ref, spec):
    node = spec
    for part in ref.lstrip("#/").split("/"):
        node = node.get(part, {})
    return node


def deref(schema, spec):
    if isinstance(schema, dict) and "$ref" in schema:
        return resolve(schema["$ref"], spec)
    return schema or {}


def type_name(schema, spec):
    schema = deref(schema, spec)
    t = schema.get("type", "object")
    if t == "array":
        item = deref(schema.get("items", {}), spec)
        return "array<" + item.get("type", "object") + ">"
    if "enum" in schema:
        return t + "（枚举：" + " / ".join(str(v) for v in schema["enum"]) + "）"
    return t


def field_rows(schema, spec):
    schema = deref(schema, spec)
    props = schema.get("properties") or {}
    required = set(schema.get("required") or [])
    rows = []
    for name, prop in props.items():
        prop = deref(prop, spec)
        rows.append((
            name,
            type_name(prop, spec),
            "是" if name in required else "否",
            (prop.get("description") or "").replace("\n", " ").replace("|", "/"),
        ))
    return rows


def main():
    with io.open(SPEC_PATH, "r", encoding="utf-8") as f:
        spec = json.load(f)

    info = spec.get("info", {})
    out = []
    out.append("# " + info.get("title", "接口文档"))
    out.append("")
    out.append("> 版本 " + info.get("version", "-") + "　｜　本文档由 `docs/openapi.json`（应用运行时自动生成）转换而来。")
    out.append("")
    if info.get("description"):
        out.append(info["description"].strip())
        out.append("")

    schemes = (spec.get("components") or {}).get("securitySchemes") or {}
    if schemes:
        out.append("## 鉴权说明")
        out.append("")
        out.append("| 方案 | 类型 | 说明 |")
        out.append("| --- | --- | --- |")
        for name, scheme in schemes.items():
            out.append("| `%s` | %s %s | %s |" % (
                name, scheme.get("type", ""), scheme.get("scheme", ""),
                (scheme.get("description") or "").replace("|", "/")))
        out.append("")
        out.append("请求时在请求头带上：`Authorization: Bearer {token}`（token 来自登录接口）。"
                   "标注「匿名」的接口不需要令牌；标注「需要令牌」的接口也可以在未登录时访问公开内容，"
                   "但带不带令牌会影响能看到的数据（例如笔记详情里的私有笔记与 liked / editable 字段）。")
        out.append("")

    paths = spec.get("paths", {})
    groups = {}
    order = []
    for path, operations in paths.items():
        for method in METHODS:
            op = operations.get(method)
            if not op:
                continue
            tags = op.get("tags") or ["其他"]
            tag = tags[0]
            if tag not in groups:
                groups[tag] = []
                order.append(tag)
            groups[tag].append((method, path, op, operations))

    order.sort(key=lambda t: TAG_ORDER.index(t) if t in TAG_ORDER else len(TAG_ORDER))

    out.append("## 接口清单")
    out.append("")
    out.append("| 分组 | 方法 | 路径 | 说明 | 鉴权 |")
    out.append("| --- | --- | --- | --- | --- |")
    for tag in order:
        for method, path, op, operations in groups[tag]:
            need_auth = "匿名" if op.get("security") == [] else "需要令牌"
            out.append("| %s | %s | `%s` | %s | %s |" % (
                tag, method.upper(), path, (op.get("summary") or "").replace("|", "/"), need_auth))
    out.append("")

    for tag in order:
        out.append("## " + tag)
        out.append("")
        for method, path, op, operations in groups[tag]:
            need_auth = "匿名（无需令牌）" if op.get("security") == [] else "需要令牌（未登录也能访问公开内容）"
            out.append("### " + (op.get("summary") or path))
            out.append("")
            out.append("`%s %s`　｜　鉴权：%s" % (method.upper(), path, need_auth))
            out.append("")
            if op.get("description"):
                out.append(op["description"].strip())
                out.append("")

            params = op.get("parameters") or []
            if params:
                out.append("**请求参数**")
                out.append("")
                out.append("| 名称 | 位置 | 类型 | 必填 | 说明 |")
                out.append("| --- | --- | --- | --- | --- |")
                for p in params:
                    out.append("| `%s` | %s | %s | %s | %s |" % (
                        p.get("name"), p.get("in"), type_name(p.get("schema", {}), spec),
                        "是" if p.get("required") else "否",
                        (p.get("description") or "").replace("|", "/")))
                out.append("")

            body = (op.get("requestBody") or {})
            if body:
                content = (body.get("content") or {})
                for ctype, media in content.items():
                    out.append("**请求体**（`%s`）" % ctype)
                    out.append("")
                    rows = field_rows(media.get("schema", {}), spec)
                    if rows:
                        out.append("| 字段 | 类型 | 必填 | 说明 |")
                        out.append("| --- | --- | --- | --- |")
                        for name, typ, req, desc in rows:
                            out.append("| `%s` | %s | %s | %s |" % (name, typ, req, desc))
                    else:
                        out.append("文件上传（`multipart/form-data`，字段名 `file`）。")
                    out.append("")

            responses = op.get("responses") or {}
            out.append("**响应**")
            out.append("")
            out.append("| HTTP | 说明 | 数据结构 |")
            out.append("| --- | --- | --- |")
            for code in sorted(responses.keys()):
                resp = responses[code]
                schema = {}
                for media in (resp.get("content") or {}).values():
                    schema = media.get("schema", {})
                    break
                schema = deref(schema, spec)
                data_schema = deref((schema.get("properties") or {}).get("data", {}), spec)
                if data_schema.get("properties"):
                    data_text = "data: " + ", ".join(data_schema["properties"].keys())
                elif data_schema.get("type") == "array":
                    item = deref(data_schema.get("items", {}), spec)
                    data_text = "data: array<" + ", ".join((item.get("properties") or {}).keys()) + ">"
                else:
                    data_text = "data: " + data_schema.get("type", "object")
                out.append("| %s | %s | %s |" % (code, (resp.get("description") or "").replace("|", "/"), data_text))
            out.append("")

    text = "\n".join(out).rstrip() + "\n"
    with io.open(OUT_PATH, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print("已生成: " + OUT_PATH)
    print("接口分组数: %d，路径数: %d，操作数: %d" % (
        len(order), len(paths), sum(len(v) for v in groups.values())))


if __name__ == "__main__":
    main()

package com.notework.common.enums;

/**
 * 笔记审核状态（扩展部分引入）。
 * <p>
 * 状态流转：
 * <ul>
 *     <li>私有笔记：不参与审核，直接 APPROVED</li>
 *     <li>公开笔记：新建或编辑后进入 PENDING（待审核）</li>
 *     <li>管理员审核：通过 → APPROVED；驳回 → REJECTED（并记录原因）</li>
 *     <li>管理员内容管理：对已通过的公开笔记下架 → TAKEN_DOWN；恢复 → APPROVED</li>
 * </ul>
 * 只有 PUBLIC + APPROVED 的笔记才会出现在公开列表、排行、详情与作者主页上。
 */
public enum AuditStatus {

    /** 待审核：仅作者本人与管理员可见 */
    PENDING("待审核"),

    /** 已通过（默认值，历史数据都是这个状态） */
    APPROVED("已通过"),

    /** 已驳回：作者可修改后重新提交 */
    REJECTED("已驳回"),

    /** 已下架：管理员处理违规内容 */
    TAKEN_DOWN("已下架");

    private final String label;

    AuditStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

package com.notework.common.enums;

/**
 * 笔记可见性。
 * <p>
 * 注意 PRIVATE 的准确语义：作者本人 + 管理员可见（管理员可见是为了保证私有笔记的合规），
 * 而不是"只有作者可见"，其他普通用户在任何接口下都读不到。
 */
public enum Visibility {

    /** 私有：仅作者本人与管理员可见 */
    PRIVATE("私有"),

    /** 公开：所有人可见（含未登录访客） */
    PUBLIC("公开");

    private final String label;

    Visibility(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

package com.notework.common.enums;

/**
 * 用户角色。
 * <p>
 * 基础要求里"笔记只有作者本人（或管理员）可编辑或删除"直接用到它，
 * 所以基础阶段就必须有这个字段，管理员后台本身属于进阶内容。
 */
public enum Role {

    /** 普通用户 */
    USER("普通用户"),

    /** 管理员 */
    ADMIN("管理员");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }
}

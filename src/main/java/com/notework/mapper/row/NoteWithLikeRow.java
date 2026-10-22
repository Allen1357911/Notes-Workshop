package com.notework.mapper.row;

import java.time.LocalDateTime;

/**
 * 数据访问层的行对象：笔记字段 + 聚合出来的点赞数。
 * <p>
 * 只用于承载手写 SQL 的查询结果，再在 Service 层组装成返回给前端的 VO。
 */
public class NoteWithLikeRow {

    private Long id;
    private Long userId;
    private String bookName;
    private String content;
    /** 数据库里存的是字符串（PRIVATE / PUBLIC），Service 层再转成枚举 */
    private String visibility;
    private Long likeCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getBookName() {
        return bookName;
    }

    public void setBookName(String bookName) {
        this.bookName = bookName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public Long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Long likeCount) {
        this.likeCount = likeCount;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}

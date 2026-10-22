package com.notework.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 关注关系，对应表 t_follow。
 * <p>
 * follower_id 关注 followee_id；(follower_id, followee_id) 上有唯一索引，重复关注靠它兜底。
 */
@TableName("t_follow")
public class Follow {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关注者（谁关注了别人） */
    private Long followerId;

    /** 被关注者（被关注的人） */
    private Long followeeId;

    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFollowerId() {
        return followerId;
    }

    public void setFollowerId(Long followerId) {
        this.followerId = followerId;
    }

    public Long getFolloweeId() {
        return followeeId;
    }

    public void setFolloweeId(Long followeeId) {
        this.followeeId = followeeId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}

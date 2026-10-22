package com.notework.service;

import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.dto.response.AuthorVO;
import com.notework.dto.response.FollowUserVO;
import com.notework.dto.response.HotAuthorVO;

/**
 * 社区互动：关注关系与作者相关查询。
 */
public interface FollowService {

    /** 关注某人（重复关注按幂等处理） */
    void follow(Long userId);

    /** 取消关注（没关注过时同样返回成功，保证幂等） */
    void unfollow(Long userId);

    /** 作者主页信息：昵称头像 + 粉丝数 / 关注数 / 笔记数 + 我是否已关注 */
    AuthorVO getAuthor(Long userId);

    /** 某人的粉丝列表（分页） */
    PageResult<FollowUserVO> pageFollowers(Long userId, PageQuery pageQuery);

    /** 某人关注的人列表（分页） */
    PageResult<FollowUserVO> pageFollowing(Long userId, PageQuery pageQuery);

    /** 热门作者排行：按粉丝数降序（分页） */
    PageResult<HotAuthorVO> pageHotAuthors(PageQuery pageQuery);
}

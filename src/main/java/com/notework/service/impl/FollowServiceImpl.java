package com.notework.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.enums.AuditStatus;
import com.notework.common.enums.Visibility;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.common.util.BizAssert;
import com.notework.dto.response.AuthorVO;
import com.notework.dto.response.FollowUserVO;
import com.notework.dto.response.HotAuthorVO;
import com.notework.entity.Follow;
import com.notework.entity.Note;
import com.notework.entity.User;
import com.notework.interceptor.UserContext;
import com.notework.mapper.FollowMapper;
import com.notework.mapper.NoteMapper;
import com.notework.mapper.UserMapper;
import com.notework.mapper.row.HotAuthorRow;
import com.notework.service.FollowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 社区互动：关注关系实现。
 * <p>
 * 计数（粉丝数、关注数、笔记数）一律实时查询，不冗余存字段，避免计数与实际记录不一致。
 */
@Service
public class FollowServiceImpl implements FollowService {

    private static final Logger log = LoggerFactory.getLogger(FollowServiceImpl.class);

    private final FollowMapper followMapper;
    private final UserMapper userMapper;
    private final NoteMapper noteMapper;

    public FollowServiceImpl(FollowMapper followMapper, UserMapper userMapper, NoteMapper noteMapper) {
        this.followMapper = followMapper;
        this.userMapper = userMapper;
        this.noteMapper = noteMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void follow(Long userId) {
        Long me = UserContext.requireUserId();
        if (me.equals(userId)) {
            throw new BusinessException(ErrorCode.CANNOT_FOLLOW_SELF);
        }
        BizAssert.notNull(userMapper.selectById(userId), ErrorCode.USER_NOT_FOUND);

        if (hasFollowed(me, userId)) {
            return;   // 已经关注过了，幂等返回
        }
        Follow follow = new Follow();
        follow.setFollowerId(me);
        follow.setFolloweeId(userId);
        try {
            followMapper.insert(follow);
            log.info("用户[{}]关注了用户[{}]", me, userId);
        } catch (DuplicateKeyException e) {
            // 并发下同一瞬间点了两次，唯一索引挡住了，按幂等处理即可
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfollow(Long userId) {
        Long me = UserContext.requireUserId();
        int deleted = followMapper.delete(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, me)
                .eq(Follow::getFolloweeId, userId));
        if (deleted > 0) {
            log.info("用户[{}]取消关注用户[{}]", me, userId);
        }
    }

    @Override
    public AuthorVO getAuthor(Long userId) {
        User user = userMapper.selectById(userId);
        BizAssert.notNull(user, ErrorCode.USER_NOT_FOUND);

        Long me = UserContext.getUserId();
        long followerCount = countFollowers(userId);
        long followingCount = countFollowing(userId);
        long noteCount = countPublicNotes(userId);
        boolean followed = me != null && !me.equals(userId) && hasFollowed(me, userId);
        boolean self = me != null && me.equals(userId);

        return AuthorVO.of(user, followerCount, followingCount, noteCount, followed, self);
    }

    @Override
    public PageResult<FollowUserVO> pageFollowers(Long userId, PageQuery pageQuery) {
        BizAssert.notNull(userMapper.selectById(userId), ErrorCode.USER_NOT_FOUND);

        // 粉丝 = 关注关系里 followee_id = userId 的那些 follower_id
        Page<Follow> page = followMapper.selectPage(new Page<>(pageQuery.getPage(), pageQuery.getSize()),
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFolloweeId, userId)
                        .orderByDesc(Follow::getId));

        List<Long> ids = page.getRecords().stream().map(Follow::getFollowerId).toList();
        return assembleUsers(ids, page, page.getRecords(), userId);
    }

    @Override
    public PageResult<FollowUserVO> pageFollowing(Long userId, PageQuery pageQuery) {
        BizAssert.notNull(userMapper.selectById(userId), ErrorCode.USER_NOT_FOUND);

        Page<Follow> page = followMapper.selectPage(new Page<>(pageQuery.getPage(), pageQuery.getSize()),
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFollowerId, userId)
                        .orderByDesc(Follow::getId));

        List<Long> ids = page.getRecords().stream().map(Follow::getFolloweeId).toList();
        return assembleUsers(ids, page, page.getRecords(), userId);
    }

    @Override
    public PageResult<HotAuthorVO> pageHotAuthors(PageQuery pageQuery) {
        int page = pageQuery.getPage();
        int size = pageQuery.getSize();

        long total = followMapper.countHotAuthors();
        if (total == 0) {
            return PageResult.empty(page, size);
        }

        List<HotAuthorRow> rows = followMapper.selectHotAuthors((page - 1) * size, size);
        List<Long> ids = rows.stream().map(HotAuthorRow::getUserId).toList();
        Map<Long, User> users = loadUsers(ids);
        Set<Long> followedIds = followedAmong(ids);

        List<HotAuthorVO> list = new ArrayList<>();
        for (HotAuthorRow row : rows) {
            HotAuthorVO vo = HotAuthorVO.of(row, users.get(row.getUserId()), followedIds.contains(row.getUserId()));
            list.add(vo);
        }
        return PageResult.of(list, total, page, size);
    }

    /* ---------------- 内部方法 ---------------- */

    private boolean hasFollowed(Long followerId, Long followeeId) {
        Long count = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFolloweeId, followeeId));
        return count != null && count > 0;
    }

    private long countFollowers(Long userId) {
        Long count = followMapper.selectCount(new LambdaQueryWrapper<Follow>().eq(Follow::getFolloweeId, userId));
        return count == null ? 0L : count;
    }

    private long countFollowing(Long userId) {
        Long count = followMapper.selectCount(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId));
        return count == null ? 0L : count;
    }

    private long countPublicNotes(Long userId) {
        Long count = noteMapper.selectCount(new LambdaQueryWrapper<Note>()
                .eq(Note::getUserId, userId)
                .eq(Note::getVisibility, Visibility.PUBLIC)
                .eq(Note::getAuditStatus, AuditStatus.APPROVED));
        return count == null ? 0L : count;
    }

    private Map<Long, User> loadUsers(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /** 当前登录用户关注了这些人的哪几个（一次查出来，避免列表里逐条查） */
    private Set<Long> followedAmong(List<Long> targetIds) {
        Long me = UserContext.getUserId();
        if (me == null || targetIds == null || targetIds.isEmpty()) {
            return Set.of();
        }
        return followMapper.selectList(new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFollowerId, me)
                        .in(Follow::getFolloweeId, targetIds))
                .stream().map(Follow::getFolloweeId).collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * 把关注关系记录组装成用户条目：关系记录里两端分别是"列表所属的人"和"对方"，
     * 取不是 profileOwnerId 的那一端就是列表要展示的人。
     */
    private PageResult<FollowUserVO> assembleUsers(List<Long> ids, Page<Follow> page, List<Follow> records,
                                                   Long profileOwnerId) {
        int current = (int) page.getCurrent();
        int size = (int) page.getSize();
        if (ids.isEmpty()) {
            return PageResult.empty(current, size);
        }

        Map<Long, User> users = loadUsers(ids);
        Set<Long> followedIds = followedAmong(ids);

        List<FollowUserVO> list = new ArrayList<>();
        for (Follow record : records) {
            Long targetId = profileOwnerId.equals(record.getFollowerId())
                    ? record.getFolloweeId()
                    : record.getFollowerId();
            User user = users.get(targetId);
            if (user == null) {
                continue;   // 用户已被删除，跳过
            }
            list.add(FollowUserVO.of(user, followedIds.contains(targetId), record.getCreateTime()));
        }
        return PageResult.of(list, page.getTotal(), current, size);
    }
}

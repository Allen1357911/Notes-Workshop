package com.notework.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.enums.Role;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.dto.response.AuthorVO;
import com.notework.dto.response.FollowUserVO;
import com.notework.dto.response.HotAuthorVO;
import com.notework.entity.Follow;
import com.notework.entity.Note;
import com.notework.entity.User;
import com.notework.interceptor.LoginUser;
import com.notework.interceptor.UserContext;
import com.notework.mapper.FollowMapper;
import com.notework.mapper.NoteMapper;
import com.notework.mapper.UserMapper;
import com.notework.mapper.row.HotAuthorRow;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 社区互动：关注关系单元测试。
 */
@ExtendWith(MockitoExtension.class)
class FollowServiceImplTest {

    private static final Long ME = 1L;
    private static final Long OTHER = 2L;

    @Mock
    private FollowMapper followMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private NoteMapper noteMapper;

    private FollowServiceImpl followService;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Follow.class);
        TableInfoHelper.initTableInfo(assistant, Note.class);
    }

    @BeforeEach
    void setUp() {
        followService = new FollowServiceImpl(followMapper, userMapper, noteMapper);
        UserContext.set(new LoginUser(ME, "me", Role.USER));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /* ---------------- 关注 / 取关 ---------------- */

    @Test
    @DisplayName("关注：写入关注关系")
    void followInsertsRelation() {
        when(userMapper.selectById(OTHER)).thenReturn(user(OTHER));
        ArgumentCaptor<Follow> captor = ArgumentCaptor.forClass(Follow.class);
        when(followMapper.insert(captor.capture())).thenReturn(1);

        followService.follow(OTHER);

        assertEquals(ME, captor.getValue().getFollowerId());
        assertEquals(OTHER, captor.getValue().getFolloweeId());
    }

    @Test
    @DisplayName("关注自己：拒绝（50001）")
    void cannotFollowSelf() {
        BusinessException e = assertThrows(BusinessException.class, () -> followService.follow(ME));

        assertEquals(ErrorCode.CANNOT_FOLLOW_SELF, e.getErrorCode());
        verify(followMapper, never()).insert(any(Follow.class));
    }

    @Test
    @DisplayName("关注不存在的用户：10001")
    void followMissingUser() {
        when(userMapper.selectById(OTHER)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> followService.follow(OTHER));

        assertEquals(ErrorCode.USER_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("重复关注：幂等，不再插入")
    void followIsIdempotent() {
        when(userMapper.selectById(OTHER)).thenReturn(user(OTHER));
        when(followMapper.selectCount(any())).thenReturn(1L);

        followService.follow(OTHER);

        verify(followMapper, never()).insert(any(Follow.class));
    }

    @Test
    @DisplayName("并发重复关注：唯一索引拦下，按幂等处理不报错")
    void followToleratesDuplicateKey() {
        when(userMapper.selectById(OTHER)).thenReturn(user(OTHER));
        when(followMapper.insert(any(Follow.class))).thenThrow(new DuplicateKeyException("uk_follower_followee"));

        followService.follow(OTHER);

        verify(followMapper).insert(any(Follow.class));
    }

    @Test
    @DisplayName("取消关注：删除关注关系；没关注过也算成功")
    void unfollowDeletesRelation() {
        when(followMapper.delete(any())).thenReturn(1);

        followService.unfollow(OTHER);

        verify(followMapper).delete(any());
    }

    /* ---------------- 作者信息 ---------------- */

    @Test
    @DisplayName("作者主页：粉丝数、关注数、笔记数与是否已关注")
    void authorProfileCounts() {
        when(userMapper.selectById(OTHER)).thenReturn(user(OTHER));
        when(followMapper.selectCount(any())).thenReturn(5L);
        when(noteMapper.selectCount(any())).thenReturn(3L);

        AuthorVO vo = followService.getAuthor(OTHER);

        assertEquals(OTHER, vo.getId());
        assertEquals(5L, vo.getFollowerCount());
        assertEquals(5L, vo.getFollowingCount());
        assertEquals(3L, vo.getNoteCount());
        assertTrue(vo.isFollowed());
        assertFalse(vo.isSelf());
    }

    @Test
    @DisplayName("作者主页：看自己时 self=true、followed=false")
    void authorProfileSelf() {
        when(userMapper.selectById(ME)).thenReturn(user(ME));
        when(followMapper.selectCount(any())).thenReturn(0L);
        when(noteMapper.selectCount(any())).thenReturn(0L);

        AuthorVO vo = followService.getAuthor(ME);

        assertTrue(vo.isSelf());
        assertFalse(vo.isFollowed());
    }

    @Test
    @DisplayName("作者不存在：10001")
    void authorProfileMissingUser() {
        when(userMapper.selectById(OTHER)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> followService.getAuthor(OTHER));

        assertEquals(ErrorCode.USER_NOT_FOUND, e.getErrorCode());
    }

    /* ---------------- 粉丝 / 关注 / 热门作者 ---------------- */

    @Test
    @DisplayName("粉丝列表：展示关注者，并标出我是否也关注了他")
    void followersList() {
        Long third = 3L;
        when(userMapper.selectById(OTHER)).thenReturn(user(OTHER));
        when(followMapper.<Page<Follow>>selectPage(any(), any())).thenAnswer(invocation -> {
            Page<Follow> page = invocation.getArgument(0);
            // OTHER 的粉丝是 third
            page.setRecords(List.of(follow(1L, third, OTHER)));
            page.setTotal(1);
            return page;
        });
        when(userMapper.selectByIds(any())).thenReturn(List.of(user(third)));
        // 我（ME）也关注了 third，因此列表里应标记为"已关注"
        when(followMapper.selectList(any())).thenReturn(List.of(follow(9L, ME, third)));

        PageResult<FollowUserVO> result = followService.pageFollowers(OTHER, new PageQuery());

        assertEquals(1L, result.getTotal());
        assertEquals(third, result.getList().get(0).getId(), "粉丝是关注者本人");
        assertTrue(result.getList().get(0).isFollowed(), "我已关注该粉丝");
    }

    @Test
    @DisplayName("粉丝列表：自己不会出现在自己的粉丝里（不能关注自己）")
    void followersListDoesNotMarkSelf() {
        when(userMapper.selectById(ME)).thenReturn(user(ME));
        when(followMapper.<Page<Follow>>selectPage(any(), any())).thenAnswer(invocation -> {
            Page<Follow> page = invocation.getArgument(0);
            page.setRecords(List.of(follow(1L, ME, ME)));
            page.setTotal(1);
            return page;
        });
        when(userMapper.selectByIds(any())).thenReturn(List.of(user(ME)));
        when(followMapper.selectList(any())).thenReturn(List.of());

        PageResult<FollowUserVO> result = followService.pageFollowers(ME, new PageQuery());

        assertFalse(result.getList().get(0).isFollowed(), "不会把自己标记成已关注");
    }

    @Test
    @DisplayName("关注列表：展示被关注的人")
    void followingList() {
        when(userMapper.selectById(ME)).thenReturn(user(ME));
        when(followMapper.<Page<Follow>>selectPage(any(), any())).thenAnswer(invocation -> {
            Page<Follow> page = invocation.getArgument(0);
            page.setRecords(List.of(follow(2L, ME, OTHER)));
            page.setTotal(1);
            return page;
        });
        when(userMapper.selectByIds(any())).thenReturn(List.of(user(OTHER)));
        when(followMapper.selectList(any())).thenReturn(List.of());

        PageResult<FollowUserVO> result = followService.pageFollowing(ME, new PageQuery());

        assertEquals(OTHER, result.getList().get(0).getId(), "关注列表展示被关注者");
        assertFalse(result.getList().get(0).isFollowed());
    }

    @Test
    @DisplayName("热门作者：按粉丝数排行组装昵称与笔记数")
    void hotAuthors() {
        when(followMapper.countHotAuthors()).thenReturn(1L);
        when(followMapper.selectHotAuthors(0, 10)).thenReturn(List.of(hotRow(OTHER, 7L, 3L)));
        when(userMapper.selectByIds(any())).thenReturn(List.of(user(OTHER)));
        when(followMapper.selectList(any())).thenReturn(List.of());

        PageResult<HotAuthorVO> result = followService.pageHotAuthors(new PageQuery());

        assertEquals(1L, result.getTotal());
        HotAuthorVO vo = result.getList().get(0);
        assertEquals(OTHER, vo.getId());
        assertEquals("用户2", vo.getNickname());
        assertEquals(7L, vo.getFollowerCount());
        assertEquals(3L, vo.getNoteCount());
        assertFalse(vo.isFollowed());
    }

    @Test
    @DisplayName("热门作者：没有任何关注关系时返回空分页，不查聚合")
    void hotAuthorsEmpty() {
        when(followMapper.countHotAuthors()).thenReturn(0L);

        PageResult<HotAuthorVO> result = followService.pageHotAuthors(new PageQuery());

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
        verify(followMapper, never()).selectHotAuthors(anyInt(), anyInt());
    }

    /* ---------------- 辅助方法 ---------------- */

    private Follow follow(Long id, Long followerId, Long followeeId) {
        Follow follow = new Follow();
        follow.setId(id);
        follow.setFollowerId(followerId);
        follow.setFolloweeId(followeeId);
        return follow;
    }

    private HotAuthorRow hotRow(Long userId, Long followerCount, Long noteCount) {
        HotAuthorRow row = new HotAuthorRow();
        row.setUserId(userId);
        row.setFollowerCount(followerCount);
        row.setNoteCount(noteCount);
        return row;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setNickname("用户" + id);
        user.setRole(Role.USER);
        user.setStatus(1);
        return user;
    }
}

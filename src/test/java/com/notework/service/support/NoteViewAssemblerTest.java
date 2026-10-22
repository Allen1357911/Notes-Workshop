package com.notework.service.support;

import com.notework.dto.response.NoteRankVO;
import com.notework.entity.User;
import com.notework.mapper.UserMapper;
import com.notework.mapper.row.NoteWithLikeRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 列表视图组装器测试。
 * <p>
 * 重点验证"批量补齐作者信息"这件事：多条笔记也只用一次查询，不做 N+1。
 */
@ExtendWith(MockitoExtension.class)
class NoteViewAssemblerTest {

    @Mock
    private UserMapper userMapper;

    @Test
    @DisplayName("补齐作者信息与点赞数，且作者只查一次")
    void assemblesAuthorInOneBatch() {
        NoteViewAssembler assembler = new NoteViewAssembler(userMapper);
        when(userMapper.selectByIds(any())).thenReturn(List.of(author(1L, "张三"), author(2L, "李四")));

        List<NoteRankVO> list = assembler.assembleRankList(List.of(
                row(10L, 1L, 3L),
                row(11L, 2L, 0L)));

        assertEquals(2, list.size());
        assertEquals("张三", list.get(0).getAuthorNickname());
        assertEquals(3L, list.get(0).getLikeCount());
        assertEquals("李四", list.get(1).getAuthorNickname());
        assertEquals("公开", list.get(1).getVisibilityLabel());
        verify(userMapper, times(1)).selectByIds(any());
    }

    @Test
    @DisplayName("空输入直接返回空列表，不查数据库")
    void emptyInputSkipsQuery() {
        NoteViewAssembler assembler = new NoteViewAssembler(userMapper);

        assertTrue(assembler.assembleRankList(List.of()).isEmpty());
        assertTrue(assembler.assembleRankList(null).isEmpty());
        verify(userMapper, never()).selectByIds(any());
    }

    private NoteWithLikeRow row(Long id, Long userId, Long likeCount) {
        NoteWithLikeRow row = new NoteWithLikeRow();
        row.setId(id);
        row.setUserId(userId);
        row.setBookName("《活着》");
        row.setContent("正文");
        row.setVisibility("PUBLIC");
        row.setLikeCount(likeCount);
        return row;
    }

    private User author(Long id, String nickname) {
        User user = new User();
        user.setId(id);
        user.setNickname(nickname);
        return user;
    }
}

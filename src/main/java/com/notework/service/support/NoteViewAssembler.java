package com.notework.service.support;

import com.notework.dto.response.NoteRankVO;
import com.notework.entity.User;
import com.notework.mapper.UserMapper;
import com.notework.mapper.row.NoteWithLikeRow;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 列表视图组装器：把手写 SQL 查出来的行对象补上作者信息，转成前端要的 VO。
 * <p>
 * 单独抽出来，是因为"按点赞数排行"和"我点赞过的笔记"两个列表都要做同一件事，
 * 而且都必须一次性批量查作者，不能每条笔记查一次（N+1）。
 */
@Component
public class NoteViewAssembler {

    private final UserMapper userMapper;

    public NoteViewAssembler(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public List<NoteRankVO> assembleRankList(List<NoteWithLikeRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Long> authorIds = rows.stream().map(NoteWithLikeRow::getUserId).distinct().toList();
        Map<Long, User> authors = userMapper.selectByIds(authorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return rows.stream()
                .map(row -> NoteRankVO.of(row, authors.get(row.getUserId())))
                .toList();
    }
}

package com.notework.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.enums.AuditStatus;
import com.notework.common.enums.Visibility;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.config.UploadProperties;
import com.notework.dto.request.CreateNoteRequest;
import com.notework.dto.request.UpdateNoteRequest;
import com.notework.dto.response.NoteListVO;
import com.notework.dto.response.NoteRankVO;
import com.notework.dto.response.NoteVO;
import com.notework.entity.Follow;
import com.notework.entity.Note;
import com.notework.entity.NoteImage;
import com.notework.entity.User;
import com.notework.interceptor.UserContext;
import com.notework.mapper.FollowMapper;
import com.notework.mapper.NoteImageMapper;
import com.notework.mapper.NoteMapper;
import com.notework.mapper.UserMapper;
import com.notework.mapper.row.NoteWithLikeRow;
import com.notework.common.util.BizAssert;
import com.notework.service.CommentService;
import com.notework.service.LikeService;
import com.notework.service.NoteService;
import com.notework.service.support.NoteViewAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 读书笔记业务实现。
 * <p>
 * 权限规则集中在这里：
 * <ul>
 *     <li>读：公开笔记任何人可看；私有笔记只有作者本人或管理员可看</li>
 *     <li>写（编辑/删除）：只有作者本人或管理员</li>
 * </ul>
 */
@Service
public class NoteServiceImpl implements NoteService {

    /** 一篇笔记最多允许的配图数量（业务保护，避免一次塞进过多图片） */
    private static final int MAX_NOTE_IMAGES = 9;

    private final NoteMapper noteMapper;
    private final NoteImageMapper noteImageMapper;
    private final UserMapper userMapper;
    private final UploadProperties uploadProperties;
    private final LikeService likeService;
    private final NoteViewAssembler noteViewAssembler;
    private final CommentService commentService;
    private final FollowMapper followMapper;

    public NoteServiceImpl(NoteMapper noteMapper, NoteImageMapper noteImageMapper,
                           UserMapper userMapper, UploadProperties uploadProperties,
                           LikeService likeService, NoteViewAssembler noteViewAssembler,
                           CommentService commentService, FollowMapper followMapper) {
        this.noteMapper = noteMapper;
        this.noteImageMapper = noteImageMapper;
        this.userMapper = userMapper;
        this.uploadProperties = uploadProperties;
        this.likeService = likeService;
        this.noteViewAssembler = noteViewAssembler;
        this.commentService = commentService;
        this.followMapper = followMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteVO createNote(CreateNoteRequest request) {
        Long userId = UserContext.requireUserId();
        List<String> imageUrls = normalizeImageUrls(request.getImageUrls());

        Note note = new Note();
        note.setUserId(userId);
        note.setBookName(request.getBookName().trim());
        note.setContent(request.getContent());
        note.setVisibility(request.getVisibility());
        // 扩展部分：公开笔记先进待审核队列，私有笔记不参与审核
        note.setAuditStatus(initialAuditStatus(request.getVisibility()));
        noteMapper.insert(note);

        saveImages(note.getId(), imageUrls);
        // 回查一次再组装：create_time / update_time 是由数据库默认值生成的，
        // 插入后的实体上并没有这两个值，直接用它组装会让响应里的时间字段是 null
        // （同一个资源随后查询却有时间，接口自相矛盾）。
        return buildDetail(loadNote(note.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteVO updateNote(Long noteId, UpdateNoteRequest request) {
        Note note = loadNote(noteId);
        checkOwnerOrAdmin(note);

        Note update = new Note();
        update.setId(note.getId());
        update.setBookName(request.getBookName().trim());
        update.setContent(request.getContent());
        update.setVisibility(request.getVisibility());
        // 编辑后重新进审核：公开笔记回到待审核并清掉上一次的驳回原因；私有笔记直接视为已通过
        update.setAuditStatus(initialAuditStatus(request.getVisibility()));
        update.setAuditRemark("");
        update.setAuditTime(LocalDateTime.now());
        noteMapper.updateById(update);

        // imageUrls 为 null 表示不改配图；空列表表示清空；有内容则整体替换
        if (request.getImageUrls() != null) {
            noteImageMapper.delete(new LambdaQueryWrapper<NoteImage>().eq(NoteImage::getNoteId, note.getId()));
            saveImages(note.getId(), normalizeImageUrls(request.getImageUrls()));
        }

        // 正文变了，已保存的 AI 结论就过期了：清空，让用户重新生成
        // （和"编辑后重新送审"是同一个原则：内容变了，旧结论自然作废）
        noteMapper.update(null, new LambdaUpdateWrapper<Note>()
                .eq(Note::getId, note.getId())
                .set(Note::getAiType, null)
                .set(Note::getAiResult, null)
                .set(Note::getAiConfirmedAt, null)
                .set(Note::getAiPublic, 0));

        return buildDetail(loadNote(note.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNote(Long noteId) {
        Note note = loadNote(noteId);
        checkOwnerOrAdmin(note);

        // 先删配图、点赞记录与评论，再删笔记；磁盘上的图片文件暂不清理（既定取舍）
        noteImageMapper.delete(new LambdaQueryWrapper<NoteImage>().eq(NoteImage::getNoteId, note.getId()));
        likeService.deleteLikesByNoteId(note.getId());
        commentService.deleteByNoteId(note.getId());
        noteMapper.deleteById(note.getId());
    }

    @Override
    public PageResult<NoteListVO> pageMyNotes(PageQuery pageQuery) {
        Long userId = UserContext.requireUserId();

        Page<Note> page = noteMapper.selectPage(
                new Page<>(pageQuery.getPage(), pageQuery.getSize()),
                new LambdaQueryWrapper<Note>()
                        .eq(Note::getUserId, userId)
                        .orderByDesc(Note::getCreateTime)
                        .orderByDesc(Note::getId));

        // 我的笔记：作者本人需要看到每一篇的审核状态
        return toPageResult(page, true);
    }

    @Override
    public PageResult<NoteListVO> pagePublicNotes(PageQuery pageQuery) {
        Page<Note> page = noteMapper.selectPage(
                new Page<>(pageQuery.getPage(), pageQuery.getSize()),
                new LambdaQueryWrapper<Note>()
                        .eq(Note::getVisibility, Visibility.PUBLIC)
                        // 扩展部分：只有通过审核的公开笔记才对外展示
                        .eq(Note::getAuditStatus, AuditStatus.APPROVED)
                        .orderByDesc(Note::getCreateTime)
                        .orderByDesc(Note::getId));

        return toPageResult(page);
    }

    @Override
    public PageResult<NoteRankVO> pageRankedNotes(PageQuery pageQuery) {
        int page = pageQuery.getPage();
        int size = pageQuery.getSize();

        long total = noteMapper.countRankNotes();
        if (total == 0) {
            return PageResult.empty(page, size);
        }

        // 排行用手写 SQL（联表聚合点赞数 + LIMIT），总数单独查，保证分页总数与列表口径一致
        List<NoteWithLikeRow> rows = noteMapper.selectRankList((page - 1) * size, size);
        return PageResult.of(noteViewAssembler.assembleRankList(rows), total, page, size);
    }

    @Override
    public NoteVO getNoteDetail(Long noteId) {
        Note note = loadNote(noteId);
        assertCanRead(note);
        return buildDetail(note);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteVO resubmitForAudit(Long noteId) {
        Note note = loadNote(noteId);
        checkOwnerOrAdmin(note);

        if (Visibility.PUBLIC != note.getVisibility()) {
            throw new BusinessException(ErrorCode.NOTE_STATE_INVALID, "私有笔记不参与审核，无需重新提交");
        }
        if (AuditStatus.REJECTED != note.getAuditStatus()) {
            throw new BusinessException(ErrorCode.NOTE_STATE_INVALID, "只有被驳回的笔记可以重新提交审核");
        }

        Note update = new Note();
        update.setAuditStatus(AuditStatus.PENDING);
        update.setAuditRemark("");
        update.setAuditTime(LocalDateTime.now());
        // 条件更新：只有"当前确实是被驳回"才改得动，并发重复提交时只有一个能成功
        int affected = noteMapper.update(update, new LambdaUpdateWrapper<Note>()
                .eq(Note::getId, note.getId())
                .eq(Note::getAuditStatus, AuditStatus.REJECTED));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.NOTE_STATE_INVALID, "只有被驳回的笔记可以重新提交审核");
        }

        return buildDetail(loadNote(note.getId()));
    }

    @Override
    public PageResult<NoteListVO> pageUserPublicNotes(Long authorId, PageQuery pageQuery) {
        BizAssert.notNull(userMapper.selectById(authorId), ErrorCode.USER_NOT_FOUND);

        Page<Note> page = noteMapper.selectPage(
                new Page<>(pageQuery.getPage(), pageQuery.getSize()),
                new LambdaQueryWrapper<Note>()
                        .eq(Note::getUserId, authorId)
                        .eq(Note::getVisibility, Visibility.PUBLIC)
                        .eq(Note::getAuditStatus, AuditStatus.APPROVED)
                        .orderByDesc(Note::getCreateTime)
                        .orderByDesc(Note::getId));

        return toPageResult(page);
    }

    @Override
    public PageResult<NoteListVO> pageFollowingFeed(PageQuery pageQuery) {
        Long userId = UserContext.requireUserId();

        // 先取出我关注的人，再用 IN 查他们的笔记：不拼 SQL，避免注入风险
        List<Long> followeeIds = followMapper.selectList(
                        new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId))
                .stream().map(Follow::getFolloweeId).toList();
        if (followeeIds.isEmpty()) {
            return PageResult.empty(pageQuery.getPage(), pageQuery.getSize());
        }

        Page<Note> page = noteMapper.selectPage(
                new Page<>(pageQuery.getPage(), pageQuery.getSize()),
                new LambdaQueryWrapper<Note>()
                        .in(Note::getUserId, followeeIds)
                        .eq(Note::getVisibility, Visibility.PUBLIC)
                        .eq(Note::getAuditStatus, AuditStatus.APPROVED)
                        .orderByDesc(Note::getCreateTime)
                        .orderByDesc(Note::getId));

        return toPageResult(page);
    }

    /* ---------------- 权限 ---------------- */

    /**
     * 可读判定（扩展部分收口在这里，广场、排行、详情、作者主页都走它）：
     * <ul>
     *     <li>作者本人或管理员：永远可读（能在"我的笔记"里看到待审核/已驳回/已下架状态）</li>
     *     <li>其他人：私有笔记 → 20004（保持基础阶段的既有行为）</li>
     *     <li>其他人：公开但未通过审核（待审核/已驳回/已下架）→ 按"笔记不存在"处理，不暴露存在性</li>
     * </ul>
     */
    private void assertCanRead(Note note) {
        if (canEdit(note)) {
            return;
        }
        if (Visibility.PUBLIC != note.getVisibility()) {
            throw new BusinessException(ErrorCode.NOTE_VISIBILITY_DENIED);
        }
        if (!isPubliclyVisible(note)) {
            throw new BusinessException(ErrorCode.NOTE_NOT_FOUND);
        }
    }

    /** 新建或编辑后应该处于的审核状态：公开进待审核，私有不审核 */
    private AuditStatus initialAuditStatus(Visibility visibility) {
        return Visibility.PUBLIC == visibility ? AuditStatus.PENDING : AuditStatus.APPROVED;
    }

    /** 这篇笔记是否已经可以对外公开（公开且审核通过） */
    private boolean isPubliclyVisible(Note note) {
        return Visibility.PUBLIC == note.getVisibility() && AuditStatus.APPROVED == note.getAuditStatus();
    }

    /** 可写：只有作者本人或管理员 */
    private void checkOwnerOrAdmin(Note note) {
        Long userId = UserContext.requireUserId();
        if (userId.equals(note.getUserId()) || UserContext.isAdmin()) {
            return;
        }
        throw new BusinessException(ErrorCode.NOTE_PERMISSION_DENIED);
    }

    private boolean canEdit(Note note) {
        Long userId = UserContext.getUserId();
        return (userId != null && userId.equals(note.getUserId())) || UserContext.isAdmin();
    }

    /* ---------------- 组装 ---------------- */

    private Note loadNote(Long noteId) {
        Note note = noteMapper.selectById(noteId);
        BizAssert.notNull(note, ErrorCode.NOTE_NOT_FOUND);
        return note;
    }

    private NoteVO buildDetail(Note note) {
        User author = userMapper.selectById(note.getUserId());
        List<String> images = loadImageUrls(note.getId());
        long likeCount = likeService.countByNoteId(note.getId());
        boolean liked = likeService.hasLiked(note.getId(), UserContext.getUserId());
        long commentCount = commentService.countByNoteId(note.getId());
        // 审核状态与驳回原因只给作者本人和管理员看，其他人拿到的是 null
        boolean viewerCanManage = canEdit(note);
        NoteVO vo = NoteVO.of(note, author, images, viewerCanManage, likeCount, liked, commentCount,
                viewerCanManage ? note.getAuditStatus() : null,
                viewerCanManage ? note.getAuditRemark() : null,
                viewerCanManage ? note.getAuditTime() : null);

        // AI 结果可见性：作者与管理员永远可见；其他人要"笔记本身公开且已通过审核"且"作者选择公开"
        boolean aiVisible = viewerCanManage
                || (isPubliclyVisible(note) && note.getAiPublic() != null && note.getAiPublic() == 1);
        if (aiVisible && note.getAiType() != null) {
            vo.applyAi(note.getAiType(), note.getAiResult(), note.getAiPublic(), note.getAiConfirmedAt());
        }
        return vo;
    }

    private PageResult<NoteListVO> toPageResult(Page<Note> page) {
        return toPageResult(page, false);
    }

    /**
     * @param withAudit 是否下发审核状态。只有"这些笔记归当前用户管"的场景（我的笔记）才给 true；
     *                  广场、排行、作者主页、关注流里其他人不需要也不应该拿到审核状态
     */
    private PageResult<NoteListVO> toPageResult(Page<Note> page, boolean withAudit) {
        List<Note> records = page.getRecords();
        int current = (int) page.getCurrent();
        int size = (int) page.getSize();
        if (records.isEmpty()) {
            return PageResult.empty(current, size);
        }

        // 作者信息与配图都一次性批量查出来，避免每条笔记查一次的 N+1
        List<Long> authorIds = records.stream().map(Note::getUserId).distinct().toList();
        Map<Long, User> authors = userMapper.selectByIds(authorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, List<String>> images = loadImageUrlsGrouped(records.stream().map(Note::getId).toList());

        List<NoteListVO> list = records.stream()
                .map(note -> NoteListVO.from(note,
                        authors.get(note.getUserId()),
                        images.getOrDefault(note.getId(), List.of()),
                        withAudit))
                .toList();

        return PageResult.of(list, page.getTotal(), current, size);
    }

    /* ---------------- 配图 ---------------- */

    /** 校验配图地址：必须来自本系统的上传接口，顺便去重并保持顺序 */
    private List<String> normalizeImageUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return List.of();
        }
        String requiredPrefix = uploadProperties.getUrlPrefix() + "/";
        Set<String> distinct = new LinkedHashSet<>();
        for (String url : imageUrls) {
            String trimmed = url == null ? "" : url.trim();
            if (!trimmed.startsWith(requiredPrefix)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "配图地址必须来自本系统的图片上传接口");
            }
            distinct.add(trimmed);
        }
        BizAssert.isTrue(distinct.size() <= MAX_NOTE_IMAGES, ErrorCode.PARAM_ERROR,
                "一篇笔记最多 " + MAX_NOTE_IMAGES + " 张配图");
        return new ArrayList<>(distinct);
    }

    private void saveImages(Long noteId, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }
        int sortOrder = 0;
        for (String url : imageUrls) {
            NoteImage image = new NoteImage();
            image.setNoteId(noteId);
            image.setUrl(url);
            image.setSortOrder(sortOrder++);
            noteImageMapper.insert(image);
        }
    }

    private List<String> loadImageUrls(Long noteId) {
        return noteImageMapper.selectList(new LambdaQueryWrapper<NoteImage>()
                        .eq(NoteImage::getNoteId, noteId)
                        .orderByAsc(NoteImage::getSortOrder)
                        .orderByAsc(NoteImage::getId))
                .stream()
                .map(NoteImage::getUrl)
                .toList();
    }

    private Map<Long, List<String>> loadImageUrlsGrouped(Collection<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            return Map.of();
        }
        List<NoteImage> images = noteImageMapper.selectList(new LambdaQueryWrapper<NoteImage>()
                .in(NoteImage::getNoteId, noteIds)
                .orderByAsc(NoteImage::getSortOrder)
                .orderByAsc(NoteImage::getId));

        Map<Long, List<String>> grouped = new HashMap<>();
        for (NoteImage image : images) {
            grouped.computeIfAbsent(image.getNoteId(), key -> new ArrayList<>()).add(image.getUrl());
        }
        return grouped;
    }
}

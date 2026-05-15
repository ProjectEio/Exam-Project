package com.exam.module.score.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.cache.MemoryCacheManager;
import com.exam.common.BizException;
import com.exam.common.PageResult;
import com.exam.common.UserContext;
import com.exam.module.course.entity.Course;
import com.exam.module.course.mapper.CourseMapper;
import com.exam.module.score.dto.ScoreImportDTO;
import com.exam.module.score.dto.ScoreQueryDTO;
import com.exam.module.score.entity.Score;
import com.exam.module.score.mapper.ScoreMapper;
import com.exam.module.user.entity.User;
import com.exam.module.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
public class ScoreService {

    @Autowired
    private ScoreMapper scoreMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private MemoryCacheManager cacheManager;

    public PageResult<Score> page(ScoreQueryDTO query) {
        // 分页结果短时缓存（30秒），减少重复翻页的 DB 压力
        String cacheKey = "score:page:" + query.getCurrent() + ":" + query.getSize()
                + ":" + query.getStudentId() + ":" + query.getCourseId()
                + ":" + query.getExamYear() + ":" + query.getExamTerm()
                + ":" + query.getStatus();
        PageResult<Score> cached = cacheManager.get(MemoryCacheManager.PAGE_CACHE, cacheKey);
        if (cached != null) return cached;

        Page<Score> p = new Page<>(query.getCurrent(), query.getSize());
        PageResult<Score> result = PageResult.of(scoreMapper.pageWithJoin(p, query));
        cacheManager.put(MemoryCacheManager.PAGE_CACHE, cacheKey, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<Score> myScores() {
        Long uid = UserContext.userId();
        String cacheKey = "score:student:" + uid;
        List<Score> cached = cacheManager.get(MemoryCacheManager.SCORE_LIST_CACHE, cacheKey);
        if (cached != null) return cached;

        List<Score> list = scoreMapper.listByStudent(uid);
        cacheManager.put(MemoryCacheManager.SCORE_LIST_CACHE, cacheKey, list);
        return list;
    }

    public Score detail(Long id) {
        Score s = scoreMapper.selectById(id);
        if (s == null) throw new BizException("成绩不存在");
        return s;
    }

    public void save(Score score) {
        autoStatus(score);
        if (score.getId() == null) {
            // EXISTS + LIMIT 1：走 UNIQUE 索引，O(log n) 而非 COUNT 扫描
            if (scoreMapper.existsByStudentCourseYearTerm(
                    score.getStudentId(), score.getCourseId(),
                    score.getExamYear(), score.getExamTerm()) != null) {
                throw new BizException("该考生在该学期已存在该科目成绩");
            }
            scoreMapper.insert(score);
        } else {
            scoreMapper.updateById(score);
        }
        // 写后失效该学生的成绩缓存
        evictStudentScoreCache(score.getStudentId());
    }

    public void delete(Long id) {
        Score s = scoreMapper.selectById(id);
        scoreMapper.deleteById(id);
        if (s != null) evictStudentScoreCache(s.getStudentId());
    }

    /** 失效某学生的成绩缓存（写操作后调用） */
    private void evictStudentScoreCache(Long studentId) {
        if (studentId == null) return;
        cacheManager.remove(MemoryCacheManager.SCORE_LIST_CACHE, "score:student:" + studentId);
        // 分页缓存全量清空（无法精确定位受影响的 key）
        cacheManager.invalidateAll(MemoryCacheManager.PAGE_CACHE);
    }

    private void autoStatus(Score score) {
        if (score.getStatus() != null && !score.getStatus().isEmpty()) return;
        if (score.getScore() == null) {
            score.setStatus("ABSENT");
        } else if (score.getScore() >= 60) {
            score.setStatus("PASS");
        } else {
            score.setStatus("FAIL");
        }
    }

    @Transactional
    public ImportResult importExcel(MultipartFile file) throws Exception {
        ImportResult result = new ImportResult();
        List<ScoreImportDTO> rows = new ArrayList<>();

        EasyExcel.read(file.getInputStream(), ScoreImportDTO.class, new ReadListener<ScoreImportDTO>() {
            @Override
            public void invoke(ScoreImportDTO data, AnalysisContext context) {
                rows.add(data);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {}
        }).sheet().doRead();

        for (int i = 0; i < rows.size(); i++) {
            ScoreImportDTO row = rows.get(i);
            try {
                // 只查 id 列，避免加载全行数据
                User u = userMapper.selectOne(new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, row.getUsername())
                        .select(User::getId)
                        .last("LIMIT 1"));
                if (u == null) throw new BizException("用户不存在");
                Course c = courseMapper.selectOne(new LambdaQueryWrapper<Course>()
                        .eq(Course::getCourseCode, row.getCourseCode())
                        .select(Course::getId)
                        .last("LIMIT 1"));
                if (c == null) throw new BizException("课程不存在");

                Score s = new Score();
                s.setStudentId(u.getId());
                s.setCourseId(c.getId());
                s.setExamYear(row.getExamYear());
                s.setExamTerm(row.getExamTerm());
                s.setScore(row.getScore());
                s.setExamDate(row.getExamDate());
                autoStatus(s);

                // 用 EXISTS 判断是否已存在，不走 selectOne 全行查询
                Integer existFlag = scoreMapper.existsByStudentCourseYearTerm(
                        s.getStudentId(), s.getCourseId(), s.getExamYear(), s.getExamTerm());
                if (existFlag != null) {
                    // 已存在：用 UPDATE WHERE 替代先查后写
                    scoreMapper.update(s, new LambdaQueryWrapper<Score>()
                            .eq(Score::getStudentId, s.getStudentId())
                            .eq(Score::getCourseId, s.getCourseId())
                            .eq(Score::getExamYear, s.getExamYear())
                            .eq(Score::getExamTerm, s.getExamTerm()));
                } else {
                    scoreMapper.insert(s);
                }
                result.success++;
            } catch (Exception e) {
                result.fail++;
                result.errors.add("第 " + (i + 2) + " 行: " + e.getMessage());
            }
        }
        return result;
    }

    public static class ImportResult {
        public int success;
        public int fail;
        public List<String> errors = new ArrayList<>();
    }
}

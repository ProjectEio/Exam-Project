package com.exam.module.score.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.cache.MemoryCacheManager;
import com.exam.common.BizException;
import com.exam.common.PageResult;
import com.exam.common.UserContext;
import com.exam.module.course.entity.Course;
import com.exam.module.course.mapper.CourseMapper;
import com.exam.module.score.dto.ScoreImportDTO;
import com.exam.module.score.dto.ScoreQueryDTO;
import com.exam.module.score.entity.Score;
import com.exam.module.statistics.service.StatisticsService;
import com.exam.module.user.entity.User;
import com.exam.shard.ScoreShardRepository;
import com.exam.shard.UserShardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Map;

@Service
public class ScoreService {

    @Autowired
    private ScoreShardRepository scoreRepo;

    @Autowired
    private UserShardRepository userRepo;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private MemoryCacheManager cacheManager;

    @Autowired
    private StatisticsService statService;

    public PageResult<Score> page(ScoreQueryDTO query) {
        // 分页结果短时缓存（30秒），减少重复翻页的 DB 压力
        String cacheKey = "score:page:" + query.getCurrent() + ":" + query.getSize()
                + ":" + query.getStudentId() + ":" + query.getCourseId()
                + ":" + query.getExamYear() + ":" + query.getExamTerm()
                + ":" + query.getStatus();
        PageResult<Score> cached = cacheManager.get(MemoryCacheManager.PAGE_CACHE, cacheKey);
        if (cached != null) return cached;

        PageResult<Score> result = scoreRepo.page(query);
        cacheManager.put(MemoryCacheManager.PAGE_CACHE, cacheKey, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<Score> myScores() {
        Long uid = UserContext.userId();
        String cacheKey = "score:student:" + uid;
        List<Score> cached = cacheManager.get(MemoryCacheManager.SCORE_LIST_CACHE, cacheKey);
        if (cached != null) return cached;

        List<Score> list = scoreRepo.listByStudent(uid);
        cacheManager.put(MemoryCacheManager.SCORE_LIST_CACHE, cacheKey, list);
        return list;
    }

    public Score detail(Long id) {
        Score s = scoreRepo.findById(id);
        if (s == null) throw new BizException("成绩不存在");
        return s;
    }

    public void save(Score score) {
        autoStatus(score);
        fillDisplayFields(score);
        if (score.getId() == null) {
            if (scoreRepo.findByStudentCourseYearTerm(
                    score.getStudentId(), score.getCourseId(),
                    score.getExamYear(), score.getExamTerm()) != null) {
                throw new BizException("该考生在该学期已存在该科目成绩");
            }
            scoreRepo.insert(score);
        } else {
            Score old = scoreRepo.findById(score.getId());
            if (old == null) throw new BizException("成绩不存在");
            if (!Objects.equals(old.getStudentId(), score.getStudentId()) ||
                    !Objects.equals(old.getCourseId(), score.getCourseId()) ||
                    !Objects.equals(old.getExamYear(), score.getExamYear()) ||
                    !Objects.equals(old.getExamTerm(), score.getExamTerm())) {
                if (scoreRepo.findByStudentCourseYearTerm(
                        score.getStudentId(), score.getCourseId(), score.getExamYear(), score.getExamTerm()) != null) {
                    throw new BizException("该考生在该学期已存在该科目成绩");
                }
            }
            scoreRepo.update(score);
        }
        // 写后失效该学生的成绩缓存
        evictStudentScoreCache(score.getStudentId());
        statService.refreshCoursePassRateCache();
    }

    public void delete(Long id) {
        Score s = scoreRepo.findById(id);
        if (s != null) scoreRepo.softDeleteById(id);
        if (s != null) {
            evictStudentScoreCache(s.getStudentId());
            statService.refreshCoursePassRateCache();
        }
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
                User u = userRepo.findByUsername(row.getUsername());
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
                fillDisplayFields(s);

                scoreRepo.upsertByUniqueKey(s);
                result.success++;
            } catch (Exception e) {
                result.fail++;
                result.errors.add("第 " + (i + 2) + " 行: " + e.getMessage());
            }
        }
        statService.refreshCoursePassRateCache();
        return result;
    }

    private void fillDisplayFields(Score score) {
        if (score == null) return;
        User user = userRepo.findById(score.getStudentId());
        if (user == null) throw new BizException("用户不存在");
        Course course = courseMapper.selectById(score.getCourseId());
        if (course == null) throw new BizException("课程不存在");
        score.setStudentName(user.getRealName());
        score.setCourseCode(course.getCourseCode());
        score.setCourseName(course.getCourseName());
    }

    public static class ImportResult {
        public int success;
        public int fail;
        public List<String> errors = new ArrayList<>();
    }
}

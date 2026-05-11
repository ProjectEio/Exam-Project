package com.exam.module.score.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    public PageResult<Score> page(ScoreQueryDTO query) {
        Page<Score> p = new Page<>(query.getCurrent(), query.getSize());
        return PageResult.of(scoreMapper.pageWithJoin(p, query));
    }

    public List<Score> myScores() {
        return scoreMapper.listByStudent(UserContext.userId());
    }

    public Score detail(Long id) {
        Score s = scoreMapper.selectById(id);
        if (s == null) throw new BizException("成绩不存在");
        return s;
    }

    public void save(Score score) {
        autoStatus(score);
        if (score.getId() == null) {
            Long c = scoreMapper.selectCount(new LambdaQueryWrapper<Score>()
                    .eq(Score::getStudentId, score.getStudentId())
                    .eq(Score::getCourseId, score.getCourseId())
                    .eq(Score::getExamYear, score.getExamYear())
                    .eq(Score::getExamTerm, score.getExamTerm()));
            if (c > 0) throw new BizException("该考生在该学期已存在该科目成绩");
            scoreMapper.insert(score);
        } else {
            scoreMapper.updateById(score);
        }
    }

    public void delete(Long id) {
        scoreMapper.deleteById(id);
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
                User u = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, row.getUsername()));
                if (u == null) throw new BizException("用户不存在");
                Course c = courseMapper.selectOne(new LambdaQueryWrapper<Course>().eq(Course::getCourseCode, row.getCourseCode()));
                if (c == null) throw new BizException("课程不存在");

                Score s = new Score();
                s.setStudentId(u.getId());
                s.setCourseId(c.getId());
                s.setExamYear(row.getExamYear());
                s.setExamTerm(row.getExamTerm());
                s.setScore(row.getScore());
                s.setExamDate(row.getExamDate());
                autoStatus(s);

                Score exist = scoreMapper.selectOne(new LambdaQueryWrapper<Score>()
                        .eq(Score::getStudentId, s.getStudentId())
                        .eq(Score::getCourseId, s.getCourseId())
                        .eq(Score::getExamYear, s.getExamYear())
                        .eq(Score::getExamTerm, s.getExamTerm()));
                if (exist != null) {
                    s.setId(exist.getId());
                    scoreMapper.updateById(s);
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

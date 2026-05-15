package com.exam.module.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.cache.MemoryCacheManager;
import com.exam.common.BizException;
import com.exam.common.PageQuery;
import com.exam.common.PageResult;
import com.exam.module.course.entity.Course;
import com.exam.module.course.entity.MajorCourse;
import com.exam.module.course.mapper.CourseMapper;
import com.exam.module.course.mapper.MajorCourseMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class CourseService {

    private static final String PAGE_KEY_PREFIX = "course:page:";
    private static final String ALL_KEY = "course:all";
    private static final String BY_MAJOR_KEY_PREFIX = "course:major:";

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private MajorCourseMapper majorCourseMapper;

    @Autowired
    private MemoryCacheManager cacheManager;

    public PageResult<Course> page(PageQuery query) {
        String cacheKey = PAGE_KEY_PREFIX + query.getCurrent() + ":" + query.getSize() + ":" + query.getKeyword();
        PageResult<Course> hit = cacheManager.get(MemoryCacheManager.PAGE_CACHE, cacheKey);
        if (hit != null) return hit;

        Page<Course> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<Course> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            w.and(x -> x.like(Course::getCourseCode, query.getKeyword())
                    .or().like(Course::getCourseName, query.getKeyword()));
        }
        w.orderByDesc(Course::getId);
        PageResult<Course> result = PageResult.of(courseMapper.selectPage(page, w));
        cacheManager.put(MemoryCacheManager.PAGE_CACHE, cacheKey, result);
        return result;
    }

    public List<Course> all() {
        List<Course> hit = cacheManager.get(MemoryCacheManager.PAGE_CACHE, ALL_KEY);
        if (hit != null) return hit;
        List<Course> result = courseMapper.selectList(new LambdaQueryWrapper<Course>().orderByDesc(Course::getId));
        cacheManager.put(MemoryCacheManager.PAGE_CACHE, ALL_KEY, result);
        return result;
    }

    public List<Course> byMajor(Long majorId) {
        String cacheKey = BY_MAJOR_KEY_PREFIX + majorId;
        List<Course> hit = cacheManager.get(MemoryCacheManager.PAGE_CACHE, cacheKey);
        if (hit != null) return hit;
        List<Course> result = courseMapper.selectByMajorId(majorId);
        cacheManager.put(MemoryCacheManager.PAGE_CACHE, cacheKey, result);
        return result;
    }

    public Course detail(Long id) {
        Course c = courseMapper.selectById(id);
        if (c == null) throw new BizException("课程不存在");
        return c;
    }

    public void save(Course course) {
        if (course.getId() == null) {
            if (courseMapper.existsByCourseCode(course.getCourseCode()) != null) throw new BizException("课程代码已存在");
            courseMapper.insert(course);
        } else {
            courseMapper.updateById(course);
        }
        cacheManager.invalidateAll(MemoryCacheManager.PAGE_CACHE);
    }

    public void delete(Long id) {
        courseMapper.deleteById(id);
        cacheManager.invalidateAll(MemoryCacheManager.PAGE_CACHE);
    }

    /**
     * 批量删除课程，返回成功删除的数量。
     */
    @Transactional
    public int batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        int deleted = courseMapper.deleteByIds(ids);
        cacheManager.invalidateAll(MemoryCacheManager.PAGE_CACHE);
        return deleted;
    }

    /**
     * 按 course_type 聚合统计，返回 [{type:'公共课', count:3}, ...]
     */
    public List<java.util.Map<String, Object>> countByType() {
        return courseMapper.countByType();
    }

    @Transactional
    public void linkMajorCourses(Long majorId, List<Long> courseIds) {
        majorCourseMapper.delete(new LambdaQueryWrapper<MajorCourse>().eq(MajorCourse::getMajorId, majorId));
        if (courseIds != null) {
            for (Long cid : courseIds) {
                MajorCourse mc = new MajorCourse();
                mc.setMajorId(majorId);
                mc.setCourseId(cid);
                mc.setIsRequired(1);
                majorCourseMapper.insert(mc);
            }
        }
        cacheManager.invalidateAll(MemoryCacheManager.PAGE_CACHE);
    }

    public List<MajorCourse> majorCourses(Long majorId) {
        return majorCourseMapper.selectList(new LambdaQueryWrapper<MajorCourse>().eq(MajorCourse::getMajorId, majorId));
    }
}

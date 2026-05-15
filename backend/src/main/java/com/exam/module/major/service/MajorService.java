package com.exam.module.major.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.cache.MemoryCacheManager;
import com.exam.common.BizException;
import com.exam.common.PageQuery;
import com.exam.common.PageResult;
import com.exam.module.major.entity.Major;
import com.exam.module.major.mapper.MajorMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class MajorService {

    private static final String PAGE_KEY_PREFIX = "major:page:";
    private static final String ALL_KEY = "major:all";

    @Autowired
    private MajorMapper majorMapper;

    @Autowired
    private MemoryCacheManager cacheManager;

    public PageResult<Major> page(PageQuery query) {
        String cacheKey = PAGE_KEY_PREFIX + query.getCurrent() + ":" + query.getSize() + ":" + query.getKeyword();
        PageResult<Major> hit = cacheManager.get(MemoryCacheManager.PAGE_CACHE, cacheKey);
        if (hit != null) return hit;

        Page<Major> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<Major> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            w.and(x -> x.like(Major::getMajorCode, query.getKeyword())
                    .or().like(Major::getMajorName, query.getKeyword()));
        }
        w.orderByDesc(Major::getId);
        PageResult<Major> result = PageResult.of(majorMapper.selectPage(page, w));
        cacheManager.put(MemoryCacheManager.PAGE_CACHE, cacheKey, result);
        return result;
    }

    public List<Major> all() {
        List<Major> hit = cacheManager.get(MemoryCacheManager.PAGE_CACHE, ALL_KEY);
        if (hit != null) return hit;
        List<Major> result = majorMapper.selectList(new LambdaQueryWrapper<Major>().eq(Major::getStatus, 1).orderByDesc(Major::getId));
        cacheManager.put(MemoryCacheManager.PAGE_CACHE, ALL_KEY, result);
        return result;
    }

    public Major detail(Long id) {
        Major m = majorMapper.selectById(id);
        if (m == null) throw new BizException("专业不存在");
        return m;
    }

    public void save(Major major) {
        if (major.getStatus() == null) major.setStatus(1);
        if (major.getId() == null) {
            if (majorMapper.existsByMajorCode(major.getMajorCode()) != null) throw new BizException("专业代码已存在");
            majorMapper.insert(major);
        } else {
            majorMapper.updateById(major);
        }
        cacheManager.invalidateAll(MemoryCacheManager.PAGE_CACHE);
    }

    public void delete(Long id) {
        majorMapper.deleteById(id);
        cacheManager.invalidateAll(MemoryCacheManager.PAGE_CACHE);
    }
}

package com.exam.module.major.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    @Autowired
    private MajorMapper majorMapper;

    public PageResult<Major> page(PageQuery query) {
        Page<Major> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<Major> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            w.and(x -> x.like(Major::getMajorCode, query.getKeyword())
                    .or().like(Major::getMajorName, query.getKeyword()));
        }
        w.orderByDesc(Major::getId);
        return PageResult.of(majorMapper.selectPage(page, w));
    }

    public List<Major> all() {
        return majorMapper.selectList(new LambdaQueryWrapper<Major>().eq(Major::getStatus, 1).orderByDesc(Major::getId));
    }

    public Major detail(Long id) {
        Major m = majorMapper.selectById(id);
        if (m == null) throw new BizException("专业不存在");
        return m;
    }

    public void save(Major major) {
        if (major.getStatus() == null) major.setStatus(1);
        if (major.getId() == null) {
            Long c = majorMapper.selectCount(new LambdaQueryWrapper<Major>().eq(Major::getMajorCode, major.getMajorCode()));
            if (c > 0) throw new BizException("专业代码已存在");
            majorMapper.insert(major);
        } else {
            majorMapper.updateById(major);
        }
    }

    public void delete(Long id) {
        majorMapper.deleteById(id);
    }
}

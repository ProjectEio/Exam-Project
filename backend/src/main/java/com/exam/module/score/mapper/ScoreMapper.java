package com.exam.module.score.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.exam.module.score.dto.ScoreQueryDTO;
import com.exam.module.score.entity.Score;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ScoreMapper extends BaseMapper<Score> {
    IPage<Score> pageWithJoin(IPage<Score> page, @Param("q") ScoreQueryDTO query);
    List<Score> listByStudent(@Param("studentId") Long studentId);
}

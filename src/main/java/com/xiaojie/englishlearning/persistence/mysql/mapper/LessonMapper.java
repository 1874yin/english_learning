package com.xiaojie.englishlearning.persistence.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaojie.englishlearning.persistence.mysql.LessonEntity;
import com.xiaojie.englishlearning.persistence.mysql.LessonNumberSequenceEntity;
import com.xiaojie.englishlearning.persistence.mysql.LessonSummaryRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LessonMapper extends BaseMapper<LessonEntity> {

    @Select("SELECT id, number, title, title_zh AS titleZh, category, level, duration, cover "
            + "FROM lessons FORCE INDEX (uk_lessons_sort_order) ORDER BY sort_order LIMIT #{offset}, #{limit}")
    List<LessonSummaryRow> selectSummaries(@Param("offset") long offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM lessons")
    long countLessons();

    @Select("SELECT id, next_number, next_sort_order FROM lesson_number_sequence WHERE id = 1 FOR UPDATE")
    LessonNumberSequenceEntity lockNumberSequence();
}

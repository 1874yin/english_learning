package com.xiaojie.englishlearning.persistence.mysql;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("lesson_number_sequence")
public class LessonNumberSequenceEntity {
    @TableId(type = IdType.INPUT)
    private Integer id;
    private Integer nextNumber;
    private Long nextSortOrder;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getNextNumber() { return nextNumber; }
    public void setNextNumber(Integer nextNumber) { this.nextNumber = nextNumber; }
    public Long getNextSortOrder() { return nextSortOrder; }
    public void setNextSortOrder(Long nextSortOrder) { this.nextSortOrder = nextSortOrder; }
}

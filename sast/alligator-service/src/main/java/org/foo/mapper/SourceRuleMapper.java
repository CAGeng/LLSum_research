package org.foo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.foo.model.db.DBSourceRule;

import java.util.List;

@Mapper
public interface SourceRuleMapper {
    List<DBSourceRule> findAllSourceRule();
    void addSourceRule(DBSourceRule dbSourceRule);
}

package org.foo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.foo.model.db.TriggerPoint;

import java.util.List;

@Mapper
public interface TriggerPointMapper {

    List<TriggerPoint> findAllTriggerPoint();

}

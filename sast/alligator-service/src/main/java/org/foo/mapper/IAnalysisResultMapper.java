package org.foo.mapper;

import org.apache.ibatis.annotations.*;
import org.apache.ibatis.type.JdbcType;
import org.foo.data.AnalysisResult;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface IAnalysisResultMapper {

    @Select("SELECT MAX(id) FROM analysis_results;")
    Long selectLastInsertId();

    @Insert("INSERT INTO analysis_results (result_data, vul_total_count, vul_data_reachable_count) " +
            "VALUES (#{resultData}, #{vulTotalCount}, #{vulDataReachableCount})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    @SelectKey(statement="SELECT LAST_INSERT_ID()", keyProperty="id", before=false, resultType=Long.class)
    void insertAnalysisResult(AnalysisResult analysisResult);

    @Select("SELECT * FROM analysis_results WHERE id = #{id}")
    AnalysisResult selectAnalysisResultById(@Param("id") Long id);

    @Update("UPDATE analysis_results " +
            "SET result_data = #{resultData}, vul_total_count = #{vulTotalCount}, vul_data_reachable_count = #{vulDataReachableCount} " +
            "WHERE id = #{id}")
    void updateAnalysisResult(AnalysisResult analysisResult);

    @Delete("DELETE FROM analysis_results WHERE id = #{id}")
    void deleteAnalysisResult(@Param("id") Long id);

    // 统计漏洞总数
    @Select("SELECT SUM(column_name) FROM analysis_results;")
    AnalysisResult getSumCount();
}

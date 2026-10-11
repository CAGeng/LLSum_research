package app.runner;

import basic.common.SootInit;
import basic.util.Pair;
import detect.VulFingerprintDetector;
import detect.vulexp.datastruct.RiskyFuncInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;

/**
 * @author 唐亦骁
 */
@ConditionalOnNotWebApplication
//@Component
@Slf4j
public class AutomationVulFingerprintRunner implements CommandLineRunner {

    @Autowired
    VulFingerprintDetector vulFingerPrintDetector;

    // mvn仓库地址
    private static final String mvnBasePath = "C:/Users/唐亦骁/.m2/repository/";

    // cve与purl的映射关系
    private static final HashMap<String, String> cve2Purl = new HashMap<>();

    // 触发点数据
    private static final HashMap<String, String> triggerData = new HashMap<>();

    // 所有riskyAPI
    private static final HashSet<String> allRiskyAPIs = new HashSet<>();

    // soot初始化失败的列表
    private static final List<String> sootFailedInit = new ArrayList<>();

    // 下载及解压jar包失败的列表
    private static final List<String> failedDownload = new ArrayList<>();

    // 没有分析出riskyAPI的列表
    private static final List<String> noRiskyAPI = new ArrayList<>();

    // 分析出riskyAPI的列表
    private static final List<String> riskyAPI = new ArrayList<>();

    // 分析出riskyAPI但插入失败的列表
    private static final List<String> failedInsert = new ArrayList<>();

    // 程序分析过程中出错的列表
    private static final List<String> analysisFailed = new ArrayList<>();


    @Override
    public void run(String... args) throws SQLException, ClassNotFoundException {
        // 先从数据库取触发点信息
        dataAcquisition();
        log.info("VulFingerPrintDetector is running");
        for (String cve : triggerData.keySet()) {
            System.out.println("--------开始提取【" + cve + "】的危险API--------");
            if (allRiskyAPIs.contains(cve)) {
                log.info("已经分析过");
                continue;
            }
            allRiskyAPIs.add(cve);

            String[] classList = triggerData.get(cve).split(";")[0].split(", ");
            String[] methodList = triggerData.get(cve).split(";")[1].split(", ");
            if (classList.length != methodList.length) {
                log.error(cve + "记录出错！");
                continue;
            }
            // 下载并解压jar包
            String projectName = cve2Purl.get(cve).substring(10);
            String[] projectList = projectName.split("[/@]");
            String projectPath = mvnBasePath + projectList[0].replace('.', '/') + "/" + projectList[1] + "/" + projectList[2] + "/";
            String currentProject = projectList[1] + "-" + projectList[2] + ".jar";
            // 下载成功
            if (downloadUnzipPackage(projectPath, currentProject, projectList)) {
                // 跑程序分析
                try {
                    SootInit.setSoot_inputClass(Collections.singletonList(projectPath));
                } catch (Exception e) {
                    log.error("加载class出现问题!!!!!");
                    sootFailedInit.add(cve);
                    continue;
                }
                for (int i = 0; i < classList.length; i ++) {
                    RiskyFuncInfo riskyFuncInfo = new RiskyFuncInfo(classList[i], methodList[i], null, null);
                    vulFingerPrintDetector.riskyFuncInfos.add(riskyFuncInfo);
                }
                try {
                    // 保存结果到数据库中
                    RiskyFuncInfo riskyFuncInfo = new RiskyFuncInfo("org.apache.coyote.http11.AbstractHttp11Processor", "prepareRequest", null, null);
                    vulFingerPrintDetector.riskyFuncInfos.add(riskyFuncInfo);
//                    List<Pair<String, Integer>> result = null;
                    List<Pair<String, Integer>> result = vulFingerPrintDetector.detect();
                    if (result.isEmpty()) {
                        noRiskyAPI.add(cve);
                    }
                    else {
                        HashSet<String> resultSet = new HashSet<>();
                        for (Pair<String, Integer> single : result) {
                            resultSet.add(single.getKey());
                        }
                        StringBuilder finalResult = new StringBuilder();
                        for (String s : resultSet) {
                            finalResult.append(s).append(";");
                        }
                        finalResult.deleteCharAt(finalResult.length() - 1);
                        System.out.println("提取的危险API为【" + finalResult + "】");
                        if (insertDatabase(cve, finalResult.toString())) {
                            riskyAPI.add(cve);
                        }
                        else {
                            failedInsert.add(cve);
                        }
                    }
                } catch (Exception e) {
                    log.error("程序分析过程中出错");
                    analysisFailed.add(cve);
                }
                vulFingerPrintDetector.riskyFuncInfos.clear();
            }
            else {
                failedDownload.add(cve);
            }
        }

        log.info("jar包下载失败共【" + failedDownload.size() + "】");
        log.info(failedDownload.toString());

        log.info("soot初始化失败共【" + sootFailedInit.size() + "】");
        log.info(sootFailedInit.toString());

        log.info("未分析出riskyAPI共【" + noRiskyAPI.size() + "】");
        log.info(noRiskyAPI.toString());

        log.info("插入失败的共【" + failedInsert.size() + "】");
        log.info(failedInsert.toString());

        log.info("分析过程中出错的共【" + analysisFailed.size() + "】");
        log.info(analysisFailed.toString());

        log.info("成功分析出riskyAPI的共【" + riskyAPI.size() + "】");
        log.info(riskyAPI.toString());
    }

    /** 从数据库中提取数据 **/
    private void dataAcquisition() throws ClassNotFoundException, SQLException {
        String driver = "com.mysql.cj.jdbc.Driver";
        Class.forName(driver);
        String jdbcUrl = "jdbc:mysql://10.176.37.18:13311/osschain_next";
        String user = "root";
        String password = "q3rbi_EPkGk6yhr8";
        Connection connection = DriverManager.getConnection(jdbcUrl, user, password);
        Statement statement = connection.createStatement();
        String sqlString;
        // 获取所有cve到purl的映射关系
        sqlString = "select a.source_id, b.pk_purl from t_origin_vulnerability as a join t_oss_id_purl_map as b WHERE a.pk_oss_id = b.pk_oss_id;";
        ResultSet resultSet = statement.executeQuery(sqlString);
        while (resultSet.next()) {
            if (!cve2Purl.containsKey(resultSet.getString("source_id"))) {
                cve2Purl.put(resultSet.getString("source_id"), resultSet.getString("pk_purl"));
            }
        }
        // 获取所有触发点信息
        jdbcUrl = "jdbc:mysql://localhost:3306/triggerpoint";
        password = "123456";
        connection = DriverManager.getConnection(jdbcUrl, user, password);
        statement = connection.createStatement();
        sqlString = "select cve_id, classes, methods from trigger_point where source_flag = 'ACCURATE';";
        resultSet = statement.executeQuery(sqlString);
        HashMap<String, String> temp = new HashMap<>();
        while (resultSet.next()) {
            temp.put(resultSet.getString("cve_id"), resultSet.getString("classes") + ";" + resultSet.getString("methods"));
        }
        // 筛选
        for (String cve : temp.keySet()) {
            if (cve2Purl.containsKey(cve)) {
                triggerData.put(cve, temp.get(cve));
            }
        }

        // 获取所有的riskyAPI信息
        sqlString = "select cve_id from risky_api;";
        resultSet = statement.executeQuery(sqlString);
        while (resultSet.next()) {
            allRiskyAPIs.add(resultSet.getString("cve_id"));
        }
        statement.close();
        connection.close();
    }

    /** 下载依赖jar包并解压 **/
    private boolean downloadUnzipPackage(String projectPath, String currentProject, String[] projectList) {
        // 路径不存在或依赖jar包不存在
        log.info("----------正在处理【" + currentProject + "】----------");
        if (!Files.exists(Paths.get(projectPath)) || !Files.exists(Paths.get(projectPath + "/" + currentProject))) {
            log.info("当前依赖jar包不存在，正在从maven仓库下载...");
            try {
                Process process = Runtime.getRuntime().exec("cmd /c mvn dependency:copy -Dartifact=" + projectList[0] + ":" + projectList[1] + ":" + projectList[2] + ":jar");
                // 网上copy，处理线程死锁
                final InputStream is1 = process.getInputStream();
                new Thread(() -> {
                    BufferedReader br = new BufferedReader(new InputStreamReader(is1));
                    try{
                        while(br.readLine() != null) ;
                    }
                    catch(Exception e) {
                        log.error("执行命令出错", e);
                    }
                }).start();
                InputStream is2 = process.getErrorStream();
                BufferedReader br2 = new BufferedReader(new InputStreamReader(is2));
                while(br2.readLine() != null){}

                int exeCode = process.waitFor();
                if (exeCode == 0) {
                    log.info("下载成功");
                }
                else {
                    log.warn("下载失败");
                    return false;
                }
            } catch (IOException | InterruptedException e) {
                log.error("执行命令出错", e);
                return false;
            }
        }
        try {
            Process process = Runtime.getRuntime().exec("cmd /c jar xf " + currentProject, null, new File(projectPath));
            int exeCode = process.waitFor();
            if (exeCode == 0) {
                log.info("解压成功");
                return true;
            }
            else {
                log.warn("解压jar包失败");
                return false;
            }
        } catch (IOException | InterruptedException e) {
            log.error("执行命令出错", e);
            return false;
        }
    }

    /** 将数据插入数据库 **/
    private boolean insertDatabase(String cve, String result) throws ClassNotFoundException {
        String driver = "com.mysql.cj.jdbc.Driver";
        Class.forName(driver);
        String jdbcUrl = "jdbc:mysql://localhost:3306/triggerpoint";
        String user = "root";
        String password = "123456";
        try {
            Connection connection = DriverManager.getConnection(jdbcUrl, user, password);
            Statement statement = connection.createStatement();
            String sqlString;
            sqlString = "insert into risky_api(cve_id, api) values ('" +cve + "', '" + result + "');";
            statement.executeUpdate(sqlString);
            statement.close();
            connection.close();
        } catch (Exception e) {
            log.error("插入数据出现错误");
            return false;
        }
        return true;
    }
}
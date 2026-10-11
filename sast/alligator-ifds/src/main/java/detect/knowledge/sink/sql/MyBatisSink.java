package detect.knowledge.sink.sql;

import app.configuration.GeneralConfig;
import basic.util.FileUtil;
import basic.util.StaticAnalyzeUtils.ClassUtils;
import basic.util.StaticAnalyzeUtils.MethodUtil;
import basic.util.StringUtil;
import detect.knowledge.sink.MethodSinks;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import soot.SootClass;
import soot.SootMethod;
import soot.tagkit.AnnotationArrayElem;
import soot.tagkit.AnnotationElem;
import soot.tagkit.AnnotationStringElem;
import soot.tagkit.AnnotationTag;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;


@Slf4j
//@Component
public class MyBatisSink implements MethodSinks {

    @Autowired
    GeneralConfig generalConfig;

    private List<SootMethod> mybatisDaoMethods = null;
    public HashMap<SootMethod, List<Integer>> DAOMethodsParams = null;

    @Override
    public List<SootMethod> sinkMethods() {
        if(mybatisDaoMethods == null) {
            buildKnowledge();
        }
        return mybatisDaoMethods;
    }

    @Override
    public List<SootMethod> SinkMethodsAfterForceSync() {
        buildKnowledge();
        return mybatisDaoMethods;
    }

    @Override
    public HashMap<SootMethod, List<Integer>> sinkMethodsParams() {
        if(DAOMethodsParams == null) {
            buildKnowledge();
        }
        return DAOMethodsParams;
    }

    @Override
    public String getTypeName() {
        return "sink_type_sql";
    }

    @Override
    public HashMap<String, Element> getConditional() {
        return new HashMap<String, Element>();
    }

    public void buildKnowledge() {
        mybatisDaoMethods = new LinkedList<>();
        DAOMethodsParams = new HashMap<>();
        List<SootClass> mapperClasses = ClassUtils.getClassesWithAnnotation("Lorg/apache/ibatis/annotations/Mapper;");
        for (SootClass mapperClass : mapperClasses) {
            HashMap<String, String> methodMapSQL = null;
            for(String dir : generalConfig.scannedMybatisXmlDirs) {
                for(File file : FileUtil.listFiles(new File(dir))) {
                    String xmlName = file.getName().substring(0, file.getName().lastIndexOf("."));
                    String className = mapperClass.getName().substring(mapperClass.getName().lastIndexOf(".")+1);
                    if(xmlName.equals(className)) {
                        log.warn("reading mybatis mapper xml: " + xmlName);
                        methodMapSQL = parseMapperXML(file.getPath());
                    }
                }
            }

            for (SootMethod sootMethod : mapperClass.getMethods()) {
                HashSet<AnnotationTag> methodAnnotations = MethodUtil.getMethodAnnotations(sootMethod);

                String sql = null;
                if(!methodAnnotations.isEmpty()) {
                    for(AnnotationTag annotationTag : methodAnnotations) {
                       String anno = annotationTag.getType();
                       if(StringUtil.containsIgnoreCase(MYBATIS_SQL_METHOD_ANNOTATIONS, anno)){
                           // 有方法注解的，认为是通过注解配置的sql语句
                           Collection<AnnotationElem> elems = annotationTag.getElems();
                           StringBuilder sb = new StringBuilder();
                           for(AnnotationElem elem : elems) {
                               if(elem instanceof AnnotationArrayElem) {
                                   for (AnnotationElem annotationElem : ((AnnotationArrayElem) elem).getValues()){
                                       if(annotationElem instanceof AnnotationStringElem) {
                                           sb.append(((AnnotationStringElem) annotationElem).getValue()).append(" ");
                                       }
                                   }
                               }
                           }
                           sql = sb.toString().trim();
                       }
                    }

                }
                if (sql == null && methodMapSQL != null) {
                    // 没有方法注解的，认为是通过xml配置的sql语句
                    sql = methodMapSQL.get(sootMethod.getName());
                }

                // 根据sql语句判断是否漏洞
                if(sql != null) {
                    if(sql.contains("${")) {
                        log.warn("mybatis sql injection: " + sootMethod);
                        mybatisDaoMethods.add(sootMethod);
                        List<Integer> params = new LinkedList<>();
                        for(int i = 0; i < sootMethod.getParameterCount(); i++) {
                            params.add(i);
                        }
                        DAOMethodsParams.put(sootMethod, params);
                    }
                }
            }
        }
    }

    public final List<String> MYBATIS_SQL_METHOD_ANNOTATIONS = Arrays.asList(
            "Lorg/apache/ibatis/annotations/Select;",
            "Lorg/apache/ibatis/annotations/Insert;",
            "Lorg/apache/ibatis/annotations/Update;",
            "Lorg/apache/ibatis/annotations/Delete;",
            "Lorg/apache/ibatis/annotations/SelectProvider;",
            "Lorg/apache/ibatis/annotations/InsertProvider;",
            "Lorg/apache/ibatis/annotations/UpdateProvider;",
            "Lorg/apache/ibatis/annotations/DeleteProvider;",
            "Lorg/apache/ibatis/annotations/ResultMap;",
            "Lorg/apache/ibatis/annotations/Results;",
            "Lorg/apache/ibatis/annotations/Result;",
            "Lorg/apache/ibatis/annotations/Param;",
            "Lorg/apache/ibatis/annotations/Options;"
    );

    /**
     * 解析 Mapper XML 文件
     *
     * 返回方法名到sql语句的映射， e.g. query -> select * from student2s where name ='${name}'
     */
    public HashMap<String, String> parseMapperXML(String path) {

        class MySAXHandler extends DefaultHandler {

            boolean isSelect = false;
            String id = "";
            StringBuilder sqlQuery = new StringBuilder();
            public HashMap<String, String> idMapSqlQuery = new HashMap<>();

            @Override
            public InputSource resolveEntity(String publicId, String systemId) throws SAXException {
                // 禁止加载外部实体，返回一个空的 InputSource
                return new InputSource(new StringReader(""));
            }

            /*
             * 解析 XML 元素开始
             */
            @Override
            public void startElement(String uri, String localName, String qName,
                                     Attributes attributes) throws SAXException {

                super.startElement(uri, localName, qName, attributes);

                if (qName.equalsIgnoreCase("select")) {
                    isSelect = true;
                    id = attributes.getValue("id");
                    sqlQuery.setLength(0);
                }
            }

            /*
             *解析 XML 元素结束
             */
            @Override
            public void endElement(String uri, String localName, String qName)
                    throws SAXException {
                if (qName.equalsIgnoreCase("select")) {
                    isSelect = false;
                    idMapSqlQuery.put(id, sqlQuery.toString().trim());
                }
            }

            @Override
            public void characters(char[] ch, int start, int length) throws SAXException {
                if (isSelect) {
                    sqlQuery.append(ch, start, length);
                }
            }

        }

        // 获取实例
        SAXParserFactory factory = SAXParserFactory.newInstance();
        try {
            SAXParser parser = factory.newSAXParser();
            MySAXHandler handler = new MySAXHandler();
            parser.parse(path, handler);

            return handler.idMapSqlQuery;

        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    public static void main(String[] args) {
        HashMap<String, String> map = new MyBatisSink().parseMapperXML("/Users/fortsun/Downloads/题目2：源码自动化审计与自动化评估验证/赛题材料/micro_service_seclab-main/src/main/resources/mappers/IStudent2Mapper.xml");
        System.out.println(map);

        new MyBatisSink().buildKnowledge();
    }
}

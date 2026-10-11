package detect.knowledge.common;

import java.util.LinkedList;
import java.util.List;

/**
 * 终止词StopWord定义分析时停止展开的策略，如类-field分析时对String等基础类不再做展开
 */
public class StopWord {
    public static List<String> STOPWORDS_Class = new LinkedList<>();
    static {
        STOPWORDS_Class.add("java.lang.String");
        STOPWORDS_Class.add("java.lang.Integer");
        STOPWORDS_Class.add("java.lang.Boolean");
        STOPWORDS_Class.add("java.lang.Float");
        STOPWORDS_Class.add("java.lang.Double");
        STOPWORDS_Class.add("java.lang.Long");
        STOPWORDS_Class.add("java.lang.Short");
        STOPWORDS_Class.add("java.lang.Byte");
        STOPWORDS_Class.add("java.lang.Character");
        STOPWORDS_Class.add("java.lang.Object");
        STOPWORDS_Class.add("java.lang.Class");
        STOPWORDS_Class.add("int");
    }
}

package app;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * User: luca
 * Date: 2023/10/22
 * Description:
 */
public class CommonTest {

    public static void main(String[] args) {



        ExpressionParser parser = new SpelExpressionParser();
        Expression exp = parser.parseExpression("((false || (false && false && false && false && false && false && false)) && !(false) && !(false) && 1==2)");
        Boolean message = (Boolean) exp.getValue();
        System.out.println(message);


        // C3F031B1-60B3-4D6A-97D6-0CEBA740CF09
//        String xml = "<And>\n" +
//                "                            <Or>\n" +
//                "                                <TaintFlagSet taintFlag=\"SYSTEMINFO\"/>\n" +
//                "                                <TaintFlagSet taintFlag=\"REGISTRY\"/>\n" +
//                "                                <TaintFlagSet taintFlag=\"ENVIRONMENT\"/>\n" +
//                "                            </Or>\n" +
//                "                            <Not>\n" +
//                "                                <TaintFlagSet taintFlag=\"VALIDATED_SYSTEM_INFORMATION_LEAK_EXTERNAL\"/>\n" +
//                "                            </Not>\n" +
//                "                        </And>";

//        String xml = "<And>\n" +
//                "                            <Not>\n" +
//                "                                <TaintFlagSet taintFlag=\"VALIDATED_PASSWORD_MANAGEMENT\"/>\n" +
//                "                            </Not>\n" +
//                "                            <And>\n" +
//                "                                <Not>\n" +
//                "                                    <TaintFlagSet taintFlag=\"WEAKCRYPTO\"/>\n" +
//                "                                </Not>\n" +
//                "                                <Not>\n" +
//                "                                    <TaintFlagSet taintFlag=\"DEOBFUSCATED\"/>\n" +
//                "                                </Not>\n" +
//                "                            </And>\n" +
//                "                        </And>";

        // 1DA9E2E8-D2C0-4E09-9555-3BC8B55492C9
//        String xml = "<And>\n" +
//                "                            <Or>\n" +
//                "                                <TaintFlagSet taintFlag=\"SYSTEMINFO\"/>\n" +
//                "                                <TaintFlagSet taintFlag=\"REGISTRY\"/>\n" +
//                "                                <TaintFlagSet taintFlag=\"ENVIRONMENT\"/>\n" +
//                "                            </Or>\n" +
//                "                            <Not>\n" +
//                "                                <TaintFlagSet taintFlag=\"VALIDATED_SYSTEM_INFORMATION_LEAK_INTERNAL\"/>\n" +
//                "                            </Not>\n" +
//                "                            <Not>\n" +
//                "                                <Or>\n" +
//                "                                    <IsType argument=\"0\">\n" +
//                "                                    <NamespaceName>\n" +
//                "                                    <Value>javax.servlet.jsp</Value>\n" +
//                "                                    </NamespaceName>\n" +
//                "                                    <ClassName>\n" +
//                "                                    <Value>JspWriter</Value>\n" +
//                "                                    </ClassName>\n" +
//                "                                    </IsType>\n" +
//                "                                    <IsType argument=\"0\">\n" +
//                "                                    <NamespaceName>\n" +
//                "                                    <Value>javax.faces.context</Value>\n" +
//                "                                    </NamespaceName>\n" +
//                "                                    <ClassName>\n" +
//                "                                    <Value>ResponseWriter</Value>\n" +
//                "                                    </ClassName>\n" +
//                "                                    </IsType>\n" +
//                "                                    <IsType argument=\"0\">\n" +
//                "                                    <NamespaceName>\n" +
//                "                                    <Value>javax.faces.context</Value>\n" +
//                "                                    </NamespaceName>\n" +
//                "                                    <ClassName>\n" +
//                "                                    <Value>ResponseWriterWrapper</Value>\n" +
//                "                                    </ClassName>\n" +
//                "                                    </IsType>\n" +
//                "                                </Or>\n" +
//                "                            </Not>\n" +
//                "                        </And>";

        // FFB9D1B2-B7E2-417D-904C-2E3FE86F1D79
//        String xml = "<And>\n" +
//                "                            <And>\n" +
//                "                                <TaintFlagSet taintFlag=\"XSS\"/>\n" +
//                "                                <Or>\n" +
//                "                                    <TaintFlagSet taintFlag=\"DATABASE\"/>\n" +
//                "                                    <TaintFlagSet taintFlag=\"LDAP\"/>\n" +
//                "                                    <TaintFlagSet taintFlag=\"NAMING\"/>\n" +
//                "                                    <TaintFlagSet taintFlag=\"XML\"/>\n" +
//                "                                    <TaintFlagSet taintFlag=\"OPEN_XML\"/>\n" +
//                "                                    <TaintFlagSet taintFlag=\"JSON\"/>\n" +
//                "                                    <TaintFlagSet taintFlag=\"WEBSERVICE\"/>\n" +
//                "                                </Or>\n" +
//                "                            </And>\n" +
//                "                            <Not>\n" +
//                "                                <TaintFlagSet taintFlag=\"POORVALIDATION\"/>\n" +
//                "                            </Not>\n" +
//                "                            <Not>\n" +
//                "                                <TaintFlagSet taintFlag=\"VALIDATED_CROSS_SITE_SCRIPTING_PERSISTENT\"/>\n" +
//                "                            </Not>\n" +
//                "                            <IsType argument=\"this\">\n" +
//                "                                <NamespaceName>\n" +
//                "                                    <Value>org.apache.tapestry</Value>\n" +
//                "                                </NamespaceName>\n" +
//                "                                <ClassName>\n" +
//                "                                    <Value>HTMLWriter</Value>\n" +
//                "                                </ClassName>\n" +
//                "                            </IsType>\n" +
//                "                        </And>";

        String xml = "<Conditional>\n" +
                "<Or>\n" +
                "\t<TaintFlagSet taintFlag=\"DEOBFUSCATED\"/>\n" +
                "\t<Not><TaintFlagSet taintFlag=\"VALIDATED_PASSWORD_MANAGEMENT\"/></Not>\n" +
                "</Or>\n" +
                "<And>\n" +
                "     <TaintFlagSet taintFlag=\"VALIDATED_PASSWORD_MANAGEMENT\"/>\n" +
                "</And>\n" +
                "<And>\n" +
                "     <TaintFlagSet taintFlag=\"VALIDATED_PASSWORD_MANAGEMENT\"/>\n" +
                "</And>\n" +
                "<And>\n" +
                "     <Not><TaintFlagSet taintFlag=\"VALIDATED_PASSWORD_MANAGEMENT\"/></Not>\n" +
                "</And>\n" +
                "</Conditional>";

        try {
            SAXReader reader = new SAXReader();
            Document document = reader.read(new ByteArrayInputStream(xml.getBytes()));

            Element root = document.getRootElement();

            List<Element> elements = root.elements();
            List<Element> elementList = orderElements(elements);
            StringBuilder result = new StringBuilder("");
            for (Element ele : elementList){
                String eleName = ele.getName();
                if (result.length() > 1) {
                    if(eleName.equals("Or")){
                        result.append(" || ");
                    }else{
                        result.append(" && ");
                    }
                }
                result.append(parseElement(ele));
            }

            String javaCode = result.toString();
            System.out.println("Generated Java Code1: " + javaCode);
            String javaCode2 = parseElements(elements);
            System.out.println("Generated Java Code2: " + javaCode2);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String convertXMLToJavaCondition(String xmlLogic) {
        xmlLogic = xmlLogic.replaceAll("<And>", " && (");
        xmlLogic = xmlLogic.replaceAll("<Or>", " || (");
        xmlLogic = xmlLogic.replaceAll("<Not>", "&& ! (");
        xmlLogic = xmlLogic.replaceAll("</And>", ")");
        xmlLogic = xmlLogic.replaceAll("</Or>", ")");
        xmlLogic = xmlLogic.replaceAll("</Not>", ")");
        xmlLogic = xmlLogic.replaceAll("<TaintFlagSet taintFlag=\"(.*?)\"/>", "taintFlagMatches(\"$1\")");
        return xmlLogic;
    }


    public static List<Element> orderElements(List<Element> elements){
        List<Element> elementListLeft = new LinkedList<>();
        List<Element> elementListRight = new LinkedList<>();
        Iterator<Element> iterator = elements.iterator();
        while (iterator.hasNext()){
            Element child = iterator.next();
            if (child.getName().equals("Or")){
                elementListRight.add(child);
            }else{
                elementListLeft.add(child);
            }
        }
        elementListLeft.addAll(elementListRight);
        return elementListLeft;
    }

    static List<String> taintFlags = new ArrayList<>();


    public static String parseElements(List<Element> elementList) {
        List<Element> elements = orderElements(elementList);
        StringBuilder result = new StringBuilder("(");
        for (Element child : elements) {
            String childName = child.getName();
            if (result.length() > 1) {
                if(childName.equals("Or")){
                    result.append(" || ");
                }else{
                    result.append(" && ");
                }
            }
            result.append(parseElements(child.elements()));
        }
        result.append(")");
        return result.toString();
    }

    public static String parseElement(Element element) {
        String nodeName = element.getName();
        if(nodeName.equals("And") || nodeName.equals("Or")){
            StringBuilder result = new StringBuilder("(");
            List<Element> elements = orderElements(element.elements());
            for (Element child : elements) {
                if (result.length() > 1) {
                    if(child.getName().equals("Or")){
                        result.append(" || ");
                    }else{
                        result.append(" && ");
                    }
                }
                result.append(parseElement(child));
            }
            result.append(")");
            return result.toString();
        }else if(nodeName.equals("Not")){
            StringBuilder result = new StringBuilder("!(");
            List<Element> elements = orderElements(element.elements());
            for (Element child : elements) {
                result.append(parseElement(child));
            }
            result.append(")");
            return result.toString();
        }else if(nodeName.equals("TaintFlagSet")){
            String taintFlag = element.attributeValue("taintFlag");
            if(taintFlags.contains(taintFlag)){
                return "true";
            }else{
                return "false";
            }
        }else{
            return "1==2";
        }
    }
}



package detect.knowledge.propogate;

import basic.reflect.LoadClass;
import detect.knowledge.common.StopWord;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.Type;
import soot.util.Chain;

import java.lang.reflect.Field;
import java.util.*;

public class ClassRefGraph {

    public boolean option_stopword = true;

    public class ClassNode {
        String claz;
        List<ClassNode> refClasses;
        List<ClassNode> refByClasses;

        public ClassNode(String clazName) {
            this.claz = clazName;
            refClasses = new LinkedList<>();
            refByClasses = new LinkedList<>();
        }

        public void addRefClass(ClassNode refNode) {
            refClasses.add(refNode);
            refNode.refByClasses.add(this);
        }

        public List<ClassNode> getAllRefNodesBFS() {
            List<ClassNode> allRefNodes = new LinkedList<>();
            Queue<ClassNode> queue = new LinkedList<>();
            queue.add(this);

            while (!queue.isEmpty()) {
                ClassNode currentNode = queue.poll();
                allRefNodes.add(currentNode);

                for (ClassNode refNode : currentNode.refClasses) {
                    if (!allRefNodes.contains(refNode) && !queue.contains(refNode)) {
                        queue.add(refNode);
                    }
                }
            }

            return allRefNodes;
        }

        public List<ClassNode> getAllRefByNodesBFS() {
            List<ClassNode> allRefByNodes = new LinkedList<>();
            Queue<ClassNode> queue = new LinkedList<>();
            queue.add(this);

            while (!queue.isEmpty()) {
                ClassNode currentNode = queue.poll();
                allRefByNodes.add(currentNode);

                for (ClassNode refByNode : currentNode.refByClasses) {
                    if (!allRefByNodes.contains(refByNode) && !queue.contains(refByNode)) {
                        queue.add(refByNode);
                    }
                }
            }

            return allRefByNodes;
        }
    }

    public HashMap<String, ClassNode> classMapNode = new HashMap<>();

    public ClassNode getOrCreateNode(String clazName) {
        ClassNode classNode = classMapNode.get(clazName);
        if(classNode == null) {
            classNode = new ClassNode(clazName);
            classMapNode.put(clazName, classNode);
        }
        return classNode;
    }

    public ClassRefGraph buildGraph() {
        LoadClass loadClass = LoadClass.getInstance();
        Chain<SootClass> classes = Scene.v().getClasses();
        Iterator<SootClass> iterator = classes.snapshotIterator();
        while (iterator.hasNext()) {
            SootClass sootClass = iterator.next();
            if(option_stopword) {
                if (StopWord.STOPWORDS_Class.contains(sootClass.getName())) {
                    continue;
                }
            }

            // 尽量用反射处理，主要考量：能看到范型
            Class<?> claz = loadClass.tryLoad(sootClass.getName());
            boolean success = false;
            if (claz != null) {
                try {
                    ClassNode classNode = getOrCreateNode(claz.getName());
                    Field[] fields = claz.getDeclaredFields();

                    // 遍历并打印字段信息
                    for (Field field : fields) {
                        String fieldType = field.getType().getName();
                        ClassNode fieldClassNode = getOrCreateNode(fieldType);
                        classNode.addRefClass(fieldClassNode);

                        // 处理范型
                        for (String genericType : loadClass.getUsedGenericTypes(field)) {
                            ClassNode genericTypeClassNode = getOrCreateNode(genericType);
                            classNode.addRefClass(genericTypeClassNode);
                        }
                    }
                    success = true;
                } catch (Exception | NoClassDefFoundError ignored){}
            }
            if(!success){
                ClassNode classNode = getOrCreateNode(sootClass.getName());
                for (SootField field : sootClass.getFields()) {
                    Type type = field.getType();
                    ClassNode fieldClassNode = getOrCreateNode(type.toString());
                    classNode.addRefClass(fieldClassNode);
                }
            }
        }
        return this;
    }

    public List<String> getAllRefType(String claz) {
        List<String> allRefType = new LinkedList<>();
        ClassNode classNode = classMapNode.get(claz);
        if(classNode != null) {
            List<ClassNode> allRefNodes = classNode.getAllRefNodesBFS();
            for(ClassNode refNode : allRefNodes) {
                allRefType.add(refNode.claz);
            }
        }
        return allRefType;
    }

    public List<String> getAllRefByType(String claz) {
        List<String> allRefByType = new LinkedList<>();
        ClassNode classNode = classMapNode.get(claz);
        if(classNode != null) {
            List<ClassNode> allRefByNodes = classNode.getAllRefByNodesBFS();
            for(ClassNode refByNode : allRefByNodes) {
                allRefByType.add(refByNode.claz);
            }
        }
        return allRefByType;
    }

}

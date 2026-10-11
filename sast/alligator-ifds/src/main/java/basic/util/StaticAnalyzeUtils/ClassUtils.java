package basic.util.StaticAnalyzeUtils;

import basic.util.Util;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.tagkit.AnnotationTag;
import soot.tagkit.VisibilityAnnotationTag;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * 静态分析基本能力
 */
public class ClassUtils {

    public static List<SootClass> getClassesWithAnnotation(String annotationTag){
        LinkedList<SootClass> ret = new LinkedList<>();
        for (SootClass sootClass : Scene.v().getClasses()) {
            HashSet<AnnotationTag> classAnnotations = getClassAnnotations(sootClass);

            boolean fl = false;
            for(AnnotationTag tag : classAnnotations) {
                if(tag.getType().equals(annotationTag)) {
                    fl = true;
                }
            }
            if (fl) ret.add(sootClass);
        }
        return ret;
    }

    public static HashSet<AnnotationTag> getClassAnnotations(SootClass sootClass){
        HashSet<AnnotationTag> ret = new HashSet<>();
        if(sootClass.getTags().size()==0){
            return ret;
        }

        VisibilityAnnotationTag visibilityAnnotationTag = (VisibilityAnnotationTag) sootClass.getTag("VisibilityAnnotationTag");
        if (visibilityAnnotationTag !=null){
            ret.addAll(visibilityAnnotationTag.getAnnotations());
        }
        return ret;
    }

    public static HashSet<SootClass> forceGetConcreteClass(SootClass sootClass, int num){
        // BFS search for concrete subclass , return num classes at most
        HashSet<SootClass> concreteClasses = new HashSet<>();
        Queue<SootClass> queue = new LinkedList<>();
        queue.add(sootClass);
        while(!queue.isEmpty() && num > 0){
            SootClass c = queue.poll();
            if(c.isConcrete()) {
                concreteClasses.add(c);
                num--;
            }
            else{
                queue.addAll(Scene.v().getActiveHierarchy().getDirectSubclassesOf(sootClass));
            }
        }
        return concreteClasses;
    }

    public static SootClass forceGetConcreteClassOnlyOne(SootClass sootClass){
        HashSet<SootClass> concreteClasses = forceGetConcreteClass(sootClass, 1);
        if(concreteClasses.size() > 0) return concreteClasses.iterator().next();
        return null;
    }

    public static HashSet<SootClass> getAllSupers(SootClass sootClass){
        HashSet<SootClass> res = new HashSet<>();
        getAllSuperClassAndInterfaces(sootClass, res);
        return res;
    }
    private static void getAllSuperClassAndInterfaces(SootClass sootClass, HashSet<SootClass> result){
        if(result.contains(sootClass)) return;
        result.add(sootClass);
        if(sootClass.isInterface()) {
            for(SootClass superClass : Scene.v().getActiveHierarchy().getSuperinterfacesOf(sootClass)) {
                getAllSuperClassAndInterfaces(superClass, result);
            }
        }
        else  {
            for(SootClass superClass : Scene.v().getActiveHierarchy().getSuperclassesOf(sootClass)) {
                getAllSuperClassAndInterfaces(superClass, result);
            }
        }
    }

    // 找除了java.类外的祖先类
    public static SootClass getAncestorClass_ignoreJava(SootClass sootClass){
        SootClass nowAncestor = sootClass;
        if(sootClass.isInterface()) {
            for(SootClass superClass : Scene.v().getActiveHierarchy().getSuperinterfacesOf(sootClass)) {
                if(superClass.isJavaLibraryClass()) continue;
                nowAncestor = getAncestorClass_ignoreJava(superClass);
            }
        }
        else  {
            for(SootClass superClass : Scene.v().getActiveHierarchy().getSuperclassesOf(sootClass)) {
                if(superClass.isJavaLibraryClass()) continue;
                nowAncestor = getAncestorClass_ignoreJava(superClass);
            }
        }
        return nowAncestor;
    }

    // 找祖先类和兄弟类
    public static List<SootClass> getInheritParentsAndBrothers(SootClass sootClass){
        SootClass ancestor = getAncestorClass_ignoreJava(sootClass);
        return new LinkedList<>(getAllSubs(ancestor));
    }

    public static HashSet<SootClass> getAllSubs(SootClass sootClass){
        HashSet<SootClass> res = new HashSet<>();
        getAllSubClassAndInterfaces(sootClass, res);
        return res;
    }
    private static void getAllSubClassAndInterfaces(SootClass sootClass, HashSet<SootClass> result){
        if(result.contains(sootClass)) return;
        result.add(sootClass);
        try {
            if(sootClass.isInterface()) {
                for(SootClass superClass : Scene.v().getActiveHierarchy().getSubinterfacesOf(sootClass)) {
                    getAllSubClassAndInterfaces(superClass, result);
                }
                for(SootClass superClass : Scene.v().getActiveHierarchy().getImplementersOf(sootClass)) {
                    getAllSubClassAndInterfaces(superClass, result);
                }
            }
            else  {
                for(SootClass superClass : Scene.v().getActiveHierarchy().getSubclassesOf(sootClass)) {
                    getAllSubClassAndInterfaces(superClass, result);
                }
            }
        } catch (Exception e) {
            /**
             * 情形
             * Caused by: java.lang.NullPointerException
             *         at soot.Hierarchy.getSubclassesOf(Hierarchy.java:209) ~[soot-4.3.0.jar!/:?]
             *         at basic.util.StaticAnalyzeUtils.ClassUtils.getAllSubClassAndInterfaces(ClassUtils.java:134) ~[classes!/:0.0.1-SNAPSHOT
             */
            return;
        }

    }

    public static List<SootMethod> getInitMethods(SootClass sootClass) {
        List<SootMethod> rets = new LinkedList<>();
        for(SootMethod sootMethod : sootClass.getMethods()) {
            if(sootMethod.getName().equals("<init>")) {
                rets.add(sootMethod);
            }
        }
        return rets;
    }

    public static HashSet<String> getAllSubMethodSigs(String methodSig){
        HashSet<SootMethod> subMethods = new HashSet<>();
        if(!Scene.v().containsMethod(methodSig)) { return new HashSet<>(); }
        SootMethod sootMethod = Scene.v().getMethod(methodSig);
        if (sootMethod == null)  return new HashSet<>();

        return Util.toMethodSigs(getAllSubMethods(sootMethod.getDeclaringClass(), sootMethod.getSubSignature()));
    }

    public static HashSet<SootMethod> getAllSubMethods(SootClass sootClass, String methodSubSig){
        HashSet<SootMethod> ret = new HashSet<>();
        for (SootClass subClass: ClassUtils.getAllSubs(sootClass)){
            SootMethod tmpMethod = subClass.getMethodUnsafe(methodSubSig);
            if (tmpMethod != null)
                ret.add(tmpMethod);
        }
        return ret;
    }
}

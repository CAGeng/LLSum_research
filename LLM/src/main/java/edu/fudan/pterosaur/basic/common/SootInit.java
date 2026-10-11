package edu.fudan.pterosaur.basic.common;

import edu.fudan.pterosaur.basic.util.FileUtil;
import soot.G;
import soot.Scene;
import soot.options.Options;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SootInit {

    public static void setSoot_inputClass(String inputPath) {
        init(Collections.singletonList(inputPath), false);
    }

    public static void setSoot_inputClass(List<String> inputPaths) {
        init(inputPaths, false);
    }

    public static void setSoot_inputClass(List<String> inputPaths, boolean thorough) {
        init(inputPaths, thorough);
    }

    private static void init(List<String> inputPaths, boolean thorough) {
        G.reset();

        Scene.v().getApplicationClasses().clear();
        Scene.v().getClasses().clear();
        Scene.v().getLibraryClasses().clear();
        Scene.v().releaseCallGraph();
        Scene.v().releaseActiveHierarchy();
        Scene.v().releaseReachableMethods();
        Scene.v().releaseFastHierarchy();

        List<String> paths = new ArrayList<>();
        for (String path : inputPaths){
            List<String> pathList = derivePath(path, thorough);
            paths.addAll(pathList);
        }

        Options.v().set_src_prec(Options.src_prec_class);
        Options.v().set_process_dir(paths);

        Options.v().set_allow_phantom_refs(true);
        Options.v().set_whole_program(true);
        Options.v().set_prepend_classpath(true);
        Options.v().set_keep_line_number(true);

        Options.v().set_output_format(Options.output_format_none);
        Options.v().set_ignore_resolving_levels(true);

        Options.v().set_drop_bodies_after_load(false);
        Options.v().set_no_bodies_for_excluded(true);
        Options.v().setPhaseOption("cg", "all-reachable:true");
        Options.v().setPhaseOption("cg.spark", "on");//SPARK生成的call graph更准确
        Scene.v().setSootClassPath(Scene.v().getSootClassPath() + File.pathSeparator + Config.rtDir
                + File.pathSeparator + Config.jceDir + File.pathSeparator + Config.dependency_path);

        Scene.v().loadNecessaryClasses();
        System.out.println("class path : " + Scene.v().getSootClassPath());
        System.out.println("total class count： " + Scene.v().getClasses().size());
        System.out.println("total application class count： " + Scene.v().getApplicationClasses().size());
    }

    private static List<String> derivePath(String rootPath, boolean thorough){
        File rootFile = new File(rootPath);
        if (!rootFile.exists()){
            return Collections.EMPTY_LIST;
        }
        List<String> paths = new ArrayList<>();
        paths.add(rootPath);
        if (thorough){
            if (rootFile.isDirectory()){
                List<String> additionalPath = FileUtil.getJarFilesInFolder(rootPath);
                paths.addAll(additionalPath);
            }else if (rootFile.isFile() && rootPath.endsWith(".jar")){

            }
        }
        return paths;
    }

}


package intelligent;

import app.configuration.GeneralConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
public class
IntelligentLogger {

    FileWriter writer, writer2, writer3, writerSink, writerUsedEntry = null;
    FileWriter writerRPO, writerRPODiff, writerCriticalMethod, writerRPODebug, writerQueryLLM = null;
    FileWriter writerCTDiff, writerPredict, writerCondition = null;
    FileWriter writerCGOpt, writerEmptyMethod = null;
    FileWriter writerSinkWrapper = null;
    FileWriter writerDebugCFG = null;
    FileWriter recordWriter = null;
    public int conLLMSummary = 0;
    public int conLLMSummaryFailed = 0;

    @Autowired
    GeneralConfig generalConfig;

    public void log(String s) {
        log.info(s);
    }

    public void logFile(String s) {
        try {
            writer.write(s + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void logFile2(String s) {
        try {
            writer2.write(s + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void logFile3(String s) {
        try {
            writer3.write(s + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void logFileSink(String s) {
        try {
            writerSink.write(s + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void logFileRPO(String s) {
        try {
            writerRPO.write(s + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void logFileRPODiff(String s) {
        try {
            writerRPODiff.write(s + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void logFileQueryLLM(String s) {
        try {
            writerQueryLLM.write(s + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void logCriticalMethod(String s) {
        try {
            writerCriticalMethod.write(s + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void logRPODebug(String s) {
        try {
            writerRPODebug.write(s + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void logCondition(String s) {
        try {
            writerCondition.write(s + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void logDebugCFG(String s) {
        try {
            writerDebugCFG.write(s + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void logLLMExplain(String s) {
        try {
            recordWriter.write(s + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void logUsedEntry(String s) {
        try {
            writerUsedEntry.write(s + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void logCTDiff(String s) {
        try {
            writerCTDiff.write(s + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void logPredict(String s) {
        try {
            writerPredict.write(s + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void logCGOpt(String s) {
        try {
            writerCGOpt.write(s + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void logEmptyMethod(String s) {
        try {
            writerEmptyMethod.write(s + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void logSinkWrapper(String s) {
        try {
            writerSinkWrapper.write(s + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @PostConstruct
    public void init() {
        try {
            String path = generalConfig.sootOutputPath;
            File directory = new File(path, "/logs");

            // 检查目录是否存在
            if (!directory.exists()) {
                // 目录不存在，创建它
                directory.mkdirs();
            }
            writer = new FileWriter(new File(generalConfig.getSootOutputPath(), "/logs/record.txt"));
            writer2 = new FileWriter(new File(generalConfig.getSootOutputPath(), "/logs/record2.txt"));
            writer3 = new FileWriter(new File(generalConfig.getSootOutputPath(), "/logs/entries.txt"));
            recordWriter = new FileWriter(new File(generalConfig.getSootOutputPath(), "/logs/LLMExplain.txt"));
            writerSink = new FileWriter(new File(generalConfig.getSootOutputPath(), "/logs/sink.txt"));
            writerRPO = new FileWriter(new File(generalConfig.getSootOutputPath(), "/logs/RPO.txt"));
            writerRPODiff = new FileWriter(new File(generalConfig.getSootOutputPath(), "/logs/RPODiff.txt"));
            writerUsedEntry = new FileWriter(new File(generalConfig.getSootOutputPath(), "/logs/usedEntry.txt"));
            writerQueryLLM = new FileWriter(new File(generalConfig.getSootOutputPath(), "/logs/queryLLM.txt"));
            writerCriticalMethod = new FileWriter(new File(path, "/logs/criticalMethod.txt"));
            writerRPODebug = new FileWriter(new File(path, "/logs/RPODebug.txt"));
            writerDebugCFG = new FileWriter(new File(path, "/logs/debugCFG.txt"));
            writerCTDiff = new FileWriter(new File(path, "/logs/CTDiff.txt"));
            writerPredict = new FileWriter(new File(path, "/logs/predict.txt"));
            writerEmptyMethod = new FileWriter(new File(path, "/logs/emptyMethod.txt"));
            writerCondition = new FileWriter(new File(path, "/logs/condition.txt"));
            writerSinkWrapper = new FileWriter(new File(path, "/logs/sinkWrapper.txt"));
            Path path2 =  Paths.get(path, "logs", "cg");
            if (!Files.exists(path2)) {
                try {
                    Files.createDirectories(path2);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            writerCGOpt = new FileWriter(new File(path, "/logs/cg/CGOpt.txt"));
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    @PreDestroy
    public void onDestroy() throws Exception {
        try {
            writer.close();
            writer2.close();
            writer3.close();
            recordWriter.close();
            writerSink.close();
            writerUsedEntry.close();
            writerRPO.close();
            writerRPODiff.close();
            writerQueryLLM.close();
            writerCriticalMethod.close();
            writerRPODebug.close();
            writerDebugCFG.close();
            writerCTDiff.close();
            writerPredict.close();
            writerCGOpt.close();
            writerEmptyMethod.close();
            writerCondition.close();
            writerSinkWrapper.close();
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }

    }
}

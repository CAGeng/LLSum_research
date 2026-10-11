package intelligent;

import app.context.SpringContextUtils;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

@Component
public class GptRpcProxy {

    XmlRpcClient client;

    public GptRpcProxy() {
        // 设置 XML-RPC 服务器的地址
        String serverUrl = "http://localhost:8000";
        XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        try {
            config.setServerURL(new URL(serverUrl));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        // 创建 XmlRpcClient
        client = new XmlRpcClient();
        client.setConfig(config);
    }

    public String quick_dfg(String sourceCode) {
        try {
            // 调用远程方法
            Object[] params = new Object[]{sourceCode}; // 传递给远程方法的参数
            String result = (String) client.execute("gptapi_dfg", params);

            // 处理远程调用的结果
            System.out.println("Result: " + result);
            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }

    public String quick_dfg_v2(String sourceCode, List<Integer> csTaints, List<String> conditionDiffs) {
        try {
            // 调用远程方法
            Object[] params = new Object[]{sourceCode, csTaints, conditionDiffs}; // 传递给远程方法的参数
            // gptapi_conditional_sum这个接口的csTaints保存的是差异分析结果，含义"这些参数可能被污染或不污染，要关注"
            String result = (String) client.execute("gptapi_conditional_sum", params);

            // 处理远程调用的结果
            System.out.println("Result: " + result);
            return result;

        } catch (Exception e) {
            e.printStackTrace();
            IntelligentLogger intelligentLogger = SpringContextUtils.getBean(IntelligentLogger.class);
            intelligentLogger.conLLMSummaryFailed += 1;
            return "error";
        }
    }

    public String ai_point_to(String sourceCode) {
        try {
            // 调用远程方法
            Object[] params = new Object[]{sourceCode}; // 传递给远程方法的参数
            String result = (String) client.execute("gptapi_pointto", params);

            // 处理远程调用的结果
            System.out.println("Result: " + result);
            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }

    public String ai_rank_exp(String vuldesc, String down, String up, String sourceCode, String riskyFunction, String callStack) {
        try {
            // 调用远程方法
            Object[] params = new Object[]{vuldesc, down, up, sourceCode, riskyFunction, callStack}; // 传递给远程方法的参数
            String result = (String) client.execute("gptapi_rankexp", params);

            // 处理远程调用的结果
            System.out.println("Result: " + result);
            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }
}

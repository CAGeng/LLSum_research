package ai;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.junit.jupiter.api.Test;

import java.net.URL;

public class GPTApiTest {

    @Test
    void testGPTApi() throws Exception {
        try {
            // 设置 XML-RPC 服务器的地址
            String serverUrl = "http://localhost:8000";
            XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
            config.setServerURL(new URL(serverUrl));

            // 创建 XmlRpcClient
            XmlRpcClient client = new XmlRpcClient();
            client.setConfig(config);

            // 调用远程方法
            Object[] params = new Object[]{"hello"}; // 传递给远程方法的参数
            String result = (String) client.execute("gptapi_dfg", params);

            // 处理远程调用的结果
            System.out.println("Result: " + result);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

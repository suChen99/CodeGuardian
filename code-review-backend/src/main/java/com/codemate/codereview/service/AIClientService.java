package com.codemate.codereview.service;


import com.codemate.codereview.model.AiAnalyzeResult;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.List;

@Slf4j
@Service
public class AIClientService {

//    private final PriorityOrdered priorityOrdered;
    // 从配置文件读取Python 服务的地址
    @Value("${ai.service.url: http://127.0.0.1:8000}")
    private String aiServiceUrl;

    /**
     * 大模型推理可能要几十秒，必须把读超时加长
     * 默认OkHttp读超时约10秒
     */

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(5,TimeUnit.SECONDS)
            .readTimeout(120,TimeUnit.SECONDS)
            .writeTimeout(30,TimeUnit.SECONDS)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

//    public AIClientService(PriorityOrdered priorityOrdered) {
//        this.priorityOrdered = priorityOrdered;
//    }

    /**
     * 调用Python AI 服务，审查代码 diff
     * @param commitId 提交ID
     * @param author 提交者
     * @param fileName 文件名
     * @param diffText 代码变更内容
     * @return 审查结果 JSON
     */
    public AiAnalyzeResult analyzeCode(String commitId,String author,String fileName,String diffText,List<String> changedFiles){
        try{
            // 1、构造请求体
            Map<String,Object> body = new HashMap<>();
            body.put("commit_id",commitId);
            body.put("author",author);
            body.put("file_name",fileName);
            body.put("diff_text",diffText);
            body.put("changed_files",changedFiles == null ? java.util.List.of() : changedFiles);

            String json = objectMapper.writeValueAsString(body);

            // 2、创建HTTP请求
            Request request = new Request.Builder()
                    .url(buildAnalyzeUrl())
                    .post(RequestBody.create(json,MediaType.parse("application/json")))
                    .build();
            // 3、发送请求并获取响应
            log.info("\uD83D\uDCE4 正在调用 Python AI 服务: {} , 文件：{}",aiServiceUrl,fileName);
            try(Response response = httpClient.newCall(request).execute()){
                ResponseBody responseBoby = response.body();
               if (responseBoby == null) {
                   return AiAnalyzeResult.fail("AI 服务返回空 body");
               }
               String text = responseBoby.string();
               if (response.isSuccessful()) {
                   log.info("AI 审查完成，响应长度：{} 字符",text.length());
                   return AiAnalyzeResult.ok(text);
               }
               log.error("AI 服务返回错误，{} - {}",response.code(),text);
               return AiAnalyzeResult.fail("HTTP" + response.code());
            }
        }catch (IOException e) {
            // 含连接拒绝、超时、JSON 序列化失败
            log.error("调用AI服务异常：{}",e.getMessage());
            return AiAnalyzeResult.fail(e.getMessage());
        }
    }

    // 避免配置写成http://localhost:8000/ 时出现双斜杠
    private String buildAnalyzeUrl(){
        String base = aiServiceUrl.endsWith("/")
                ? aiServiceUrl.substring(0,aiServiceUrl.length() - 1)
                : aiServiceUrl;
        return base + "/analyze";

    }
}

package com.xly.codeforge.judge.codesandbox.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.xly.codeforge.common.common.ErrorCode;
import com.xly.codeforge.common.exception.BusinessException;
import com.xly.codeforge.judge.codesandbox.CodeSandbox;
import com.xly.codeforge.model.judge.ExecuteCodeRequest;
import com.xly.codeforge.model.judge.ExecuteCodeResponse;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public class RemoteCodeSandbox implements CodeSandbox {

    /**
     * 公开的客户端标识，用来告诉服务端「我是谁」。服务端拿它去查对应的共享密钥，
     * 必须与沙箱 `auth.clients[].access-key` 逐字一致，否则一律 403 Invalid access key。
     */
    private static final String ACCESS_KEY = env("SANDBOX_ACCESS_KEY", "code-forge-client");

    /**
     * 共享密钥（HMAC-SHA256），只在客户端与服务端之间共享，不经过网络传输。
     * 必须与沙箱 `sandbox.auth.clients[].secret-key` 逐字一致 —— 只改一侧会让判题全部落到 403。
     */
    private static final String SECRET_KEY = env("SANDBOX_SECRET_KEY", "aB3dEfGhI7KlMn0pQrStUvWxYz123456");

    /**
     * 沙箱执行端点。换成另一台主机/端口时走环境变量，不必改代码重新打包。
     */
    private static final String URL = env("SANDBOX_URL", "http://localhost:8090/executeCode");

    /**
     * 取环境变量，缺失或空白时回落默认值。
     *
     * <p>本类由 {@code CodeSandboxFactory} 直接 {@code new} 出来、不受 Spring 管理，
     * 因此不能用 {@code @Value}；用环境变量则能让沙箱地址与密钥在部署期下发。</p>
     */
    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        System.out.println("远程代码沙箱");
        String jsonStr = JSONUtil.toJsonStr(executeCodeRequest);

        // 生成签名参数
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString();
        String sign = sign(jsonStr, timestamp, nonce, SECRET_KEY);

        HttpResponse httpResponse = HttpRequest.post(URL)
                .header("X-Access-Key", ACCESS_KEY)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Sign", sign)
                .body(jsonStr)
                .execute();

        String responseStr = httpResponse.body();
        if (responseStr == null || httpResponse.getStatus() != 200) {
            throw new BusinessException(ErrorCode.API_REQUEST_ERROR, "调用远程沙箱失败！状态码: " + httpResponse.getStatus());
        }
        ExecuteCodeResponse executeCodeResponse = JSONUtil.toBean(responseStr, ExecuteCodeResponse.class);
        return executeCodeResponse;
    }

    private String sign(String body, String timestamp, String nonce, String secretKey) {
    String signContent = body + "\n" + timestamp + "\n" + nonce;
    try {
        // 初始化 HMAC-SHA256
        HMac hmac = new HMac(new SHA256Digest());
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        hmac.init(new KeyParameter(keyBytes));

        // 输入待签名字符串
        byte[] inputBytes = signContent.getBytes(StandardCharsets.UTF_8);
        hmac.update(inputBytes, 0, inputBytes.length);

        // 生成签名结果
        byte[] signBytes = new byte[hmac.getMacSize()];
        hmac.doFinal(signBytes, 0);

        return Base64.getEncoder().encodeToString(signBytes);
    } catch (Exception e) {
        throw new RuntimeException("签名生成失败", e);
    }
}
}
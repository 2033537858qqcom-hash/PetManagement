package com.petcare.grooming.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Alipay OpenAPI client for personal-developer apps (RSA2). */
public final class AlipayOpenClient {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final PaymentProperties.AlipayConfig config;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public AlipayOpenClient(PaymentProperties.AlipayConfig config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
    }

    public boolean configured() {
        return notBlank(config.getAppId()) && notBlank(config.getPrivateKey()) && notBlank(config.getAlipayPublicKey());
    }

    public JsonNode execute(String method, Map<String, Object> biz) {
        try {
            String bizContent = mapper.writeValueAsString(biz);
            TreeMap<String, String> params = new TreeMap<>();
            params.put("app_id", config.getAppId().trim());
            params.put("method", method);
            params.put("format", "JSON");
            params.put("charset", "UTF-8");
            params.put("sign_type", "RSA2");
            params.put("timestamp", LocalDateTime.now().format(TIME));
            params.put("version", "1.0");
            if (notBlank(config.getNotifyUrl())) params.put("notify_url", config.getNotifyUrl().trim());
            params.put("biz_content", bizContent);
            params.put("sign", sign(params));
            String body = params.entrySet().stream()
                .map(e -> url(e.getKey()) + "=" + url(e.getValue()))
                .collect(Collectors.joining("&"));
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.getGatewayUrl().trim()))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = mapper.readTree(response.body());
            String nodeName = method.replace('.', '_') + "_response";
            JsonNode payload = root.has(nodeName) ? root.get(nodeName) : root;
            if (payload == null || payload.isMissingNode()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "支付宝未返回有效结果");
            }
            String code = payload.path("code").asText();
            if (!"10000".equals(code)) {
                String msg = payload.path("sub_msg").asText(payload.path("msg").asText("支付宝接口调用失败"));
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, msg);
            }
            return payload;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "支付宝个人开发者接口调用失败: " + ex.getMessage());
        }
    }

    public boolean verifyNotify(Map<String, String> params) {
        if (params == null || !params.containsKey("sign")) return false;
        String sign = params.get("sign");
        TreeMap<String, String> sorted = new TreeMap<>();
        for (var e : params.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            if ("sign".equals(e.getKey()) || "sign_type".equals(e.getKey())) continue;
            if (e.getValue().isBlank()) continue;
            sorted.put(e.getKey(), e.getValue());
        }
        String content = sorted.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("&"));
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey());
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(sign));
        } catch (Exception ex) {
            return false;
        }
    }

    public static BigDecimal money(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.hasNonNull(field) && !node.get(field).asText().isBlank()) {
                return new BigDecimal(node.get(field).asText());
            }
        }
        return null;
    }

    private String sign(TreeMap<String, String> params) throws Exception {
        String content = params.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("&"));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey());
        signature.update(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private PrivateKey privateKey() throws Exception {
        byte[] decoded = Base64.getDecoder().decode(stripKey(config.getPrivateKey()));
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private PublicKey publicKey() throws Exception {
        byte[] decoded = Base64.getDecoder().decode(stripKey(config.getAlipayPublicKey()));
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
    }

    private static String stripKey(String key) {
        return key.replaceAll("-----BEGIN [^-]+-----", "")
            .replaceAll("-----END [^-]+-----", "")
            .replaceAll("\\s", "");
    }

    private static boolean notBlank(String value) { return value != null && !value.isBlank(); }
    private static String url(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}

package com.petcare.grooming.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pet-grooming.payment")
public class PaymentProperties {
    private boolean sandboxEnabled = true;
    private int timeoutMinutes = 15;
    private String notifyBaseUrl = "http://127.0.0.1:8000/api/payment/notify";
    private AlipayConfig alipay = new AlipayConfig();

    public static class AlipayConfig {
        private String appId = "";
        private String privateKey = "";
        private String alipayPublicKey = "";
        private String gatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
        private String notifyUrl = "";

        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getPrivateKey() { return privateKey; }
        public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
        public String getAlipayPublicKey() { return alipayPublicKey; }
        public void setAlipayPublicKey(String alipayPublicKey) { this.alipayPublicKey = alipayPublicKey; }
        public String getGatewayUrl() { return gatewayUrl; }
        public void setGatewayUrl(String gatewayUrl) { this.gatewayUrl = gatewayUrl; }
        public String getNotifyUrl() { return notifyUrl; }
        public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }
    }

    public boolean isSandboxEnabled() { return sandboxEnabled; }
    public void setSandboxEnabled(boolean sandboxEnabled) { this.sandboxEnabled = sandboxEnabled; }
    public int getTimeoutMinutes() { return timeoutMinutes; }
    public void setTimeoutMinutes(int timeoutMinutes) { this.timeoutMinutes = timeoutMinutes; }
    public String getNotifyBaseUrl() { return notifyBaseUrl; }
    public void setNotifyBaseUrl(String notifyBaseUrl) { this.notifyBaseUrl = notifyBaseUrl; }
    public AlipayConfig getAlipay() { return alipay; }
    public void setAlipay(AlipayConfig alipay) { this.alipay = alipay; }
}

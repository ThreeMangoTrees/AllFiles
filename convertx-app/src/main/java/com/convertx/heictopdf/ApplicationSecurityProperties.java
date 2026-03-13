package com.convertx.heictopdf;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class ApplicationSecurityProperties {

    private boolean requireAuth = true;
    private final Basic basic = new Basic();
    private final RateLimit rateLimit = new RateLimit();
    private final Antivirus antivirus = new Antivirus();

    public boolean isRequireAuth() {
        return requireAuth;
    }

    public void setRequireAuth(boolean requireAuth) {
        this.requireAuth = requireAuth;
    }

    public Basic getBasic() {
        return basic;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public Antivirus getAntivirus() {
        return antivirus;
    }

    public static class Basic {
        private String username = "fileoperationsxxx-admin";
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class RateLimit {
        private boolean enabled = true;
        private int requestsPerMinute = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRequestsPerMinute() {
            return requestsPerMinute;
        }

        public void setRequestsPerMinute(int requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
        }
    }

    public static class Antivirus {
        private boolean enabled;
        private String command = "clamscan";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }
    }
}

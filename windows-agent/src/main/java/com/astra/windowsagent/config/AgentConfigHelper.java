package com.astra.windowsagent.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.UUID;

@Slf4j
@Component
public class AgentConfigHelper {

    @Value("${astra.backend.url:http://localhost:8080}")
    private String defaultBackendUrl;

    @Value("${agent.device-id:}")
    private String defaultDeviceId;

    @Value("${agent.hostname:}")
    private String defaultHostname;

    @Value("${agent.token:}")
    private String defaultToken;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String resolvedBackendUrl;
    private String resolvedDeviceId;
    private String resolvedDeviceToken;
    private String resolvedHostname;
    private String resolvedHardwareId;
    private String resolvedIpAddress;
    private String resolvedMacAddress;

    private File activeIdentityFile;

    @PostConstruct
    public void init() {
        // 1. Determine Local Identity File Location (ProgramData > C:\Astra\Agent > Local)
        activeIdentityFile = resolveIdentityFile();

        // 2. Resolve Hostname
        String envHost = System.getenv("ASTRA_HOSTNAME");
        if (envHost == null || envHost.isBlank()) {
            envHost = System.getenv("COMPUTERNAME");
        }
        if (envHost == null || envHost.isBlank()) {
            envHost = defaultHostname;
        }
        if (envHost == null || envHost.isBlank()) {
            try {
                envHost = InetAddress.getLocalHost().getHostName();
            } catch (Exception ignored) {
                envHost = "Windows-Endpoint-" + UUID.randomUUID().toString().substring(0, 6);
            }
        }
        this.resolvedHostname = envHost.trim();

        // 3. Resolve Network IP & MAC Address
        resolveNetworkDetails();

        // 4. Resolve Stable Hardware ID
        resolveHardwareIdentifier();

        // 5. Load Persistent Identity from device.json if it exists
        loadIdentityFromFile();

        // 6. Precedence: Environment Var > Explicit Spring property / CLI arg > device.json > default
        String envBackend = System.getenv("ASTRA_BACKEND_URL");
        if (envBackend != null && !envBackend.isBlank()) {
            this.resolvedBackendUrl = envBackend.trim();
        } else if (defaultBackendUrl != null && !defaultBackendUrl.isBlank() && !defaultBackendUrl.equals("http://localhost:8080")) {
            this.resolvedBackendUrl = defaultBackendUrl.trim();
        } else if (this.resolvedBackendUrl == null || this.resolvedBackendUrl.isBlank()) {
            this.resolvedBackendUrl = defaultBackendUrl != null && !defaultBackendUrl.isBlank() ? defaultBackendUrl.trim() : "http://localhost:8080";
        }

        String envDeviceId = System.getenv("ASTRA_DEVICE_ID");
        if (envDeviceId != null && !envDeviceId.isBlank()) {
            this.resolvedDeviceId = envDeviceId.trim();
        }

        String envToken = System.getenv("ASTRA_AGENT_TOKEN");
        if (envToken != null && !envToken.isBlank()) {
            this.resolvedDeviceToken = envToken.trim();
        }

        log.info("[ASTRA-CONFIG] Initialized Endpoint Agent:");
        log.info("  ├─ Backend URL   : {}", this.resolvedBackendUrl);
        log.info("  ├─ Hostname      : {}", this.resolvedHostname);
        log.info("  ├─ Local LAN IP  : {}", this.resolvedIpAddress);
        log.info("  ├─ Hardware ID   : {}", this.resolvedHardwareId);
        log.info("  ├─ Device UUID   : {}", this.resolvedDeviceId != null ? this.resolvedDeviceId : "(Pending First Registration)");
        log.info("  └─ Identity File : {}", activeIdentityFile.getAbsolutePath());
    }

    private File resolveIdentityFile() {
        File programDataDir = new File("C:\\ProgramData\\Astra\\agent");
        if (programDataDir.exists() || programDataDir.mkdirs()) {
            return new File(programDataDir, "device.json");
        }
        File astraDir = new File("C:\\Astra\\Agent");
        if (astraDir.exists() || astraDir.mkdirs()) {
            return new File(astraDir, "device.json");
        }
        return new File("device.json");
    }

    private void loadIdentityFromFile() {
        if (activeIdentityFile != null && activeIdentityFile.exists()) {
            try {
                String content = Files.readString(activeIdentityFile.toPath(), StandardCharsets.UTF_8);
                JsonNode json = objectMapper.readTree(content);
                if (json.hasNonNull("deviceId")) {
                    this.resolvedDeviceId = json.get("deviceId").asText().trim();
                }
                if (json.hasNonNull("deviceToken")) {
                    this.resolvedDeviceToken = json.get("deviceToken").asText().trim();
                }
                if (json.hasNonNull("backendUrl")) {
                    this.resolvedBackendUrl = json.get("backendUrl").asText().trim();
                }
                log.info("[ASTRA-CONFIG] Loaded persistent identity from disk (UUID: {})", this.resolvedDeviceId);
            } catch (Exception e) {
                log.warn("[ASTRA-CONFIG] Could not parse identity file {}: {}", activeIdentityFile.getAbsolutePath(), e.getMessage());
            }
        }
    }

    public synchronized void saveIdentity(String deviceId, String deviceToken, String backendUrl) {
        if (deviceId != null && !deviceId.isBlank()) this.resolvedDeviceId = deviceId.trim();
        if (deviceToken != null && !deviceToken.isBlank()) this.resolvedDeviceToken = deviceToken.trim();
        if (backendUrl != null && !backendUrl.isBlank()) this.resolvedBackendUrl = backendUrl.trim();

        try {
            if (activeIdentityFile.getParentFile() != null && !activeIdentityFile.getParentFile().exists()) {
                activeIdentityFile.getParentFile().mkdirs();
            }

            ObjectNode root = objectMapper.createObjectNode();
            root.put("deviceId", this.resolvedDeviceId != null ? this.resolvedDeviceId : "");
            root.put("deviceToken", this.resolvedDeviceToken != null ? this.resolvedDeviceToken : "");
            root.put("backendUrl", this.resolvedBackendUrl != null ? this.resolvedBackendUrl : "");
            root.put("hostname", this.resolvedHostname != null ? this.resolvedHostname : "");
            root.put("hardwareId", this.resolvedHardwareId != null ? this.resolvedHardwareId : "");
            root.put("registeredAt", java.time.LocalDateTime.now().toString());

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(activeIdentityFile, root);
            log.info("[ASTRA-CONFIG] Persisted identity to {}: DeviceID={}", activeIdentityFile.getAbsolutePath(), this.resolvedDeviceId);
        } catch (Exception e) {
            log.error("[ASTRA-CONFIG] Failed to persist identity to file {}: {}", activeIdentityFile.getAbsolutePath(), e.getMessage());
        }
    }

    private void resolveNetworkDetails() {
        String bestIp = "127.0.0.1";
        String bestMac = "00:00:00:00:00:00";
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp() || ni.isVirtual()) continue;

                String dName = ni.getDisplayName() != null ? ni.getDisplayName().toLowerCase() : "";
                String name = ni.getName() != null ? ni.getName().toLowerCase() : "";
                if (dName.contains("virtual") || dName.contains("vbox") || dName.contains("vmware") || 
                    dName.contains("hyper-v") || dName.contains("wsl") || name.startsWith("veth")) {
                    continue; // Skip virtual and host-only adapters
                }

                byte[] mac = ni.getHardwareAddress();
                String currentMac = null;
                if (mac != null && mac.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < mac.length; i++) {
                        sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? ":" : ""));
                    }
                    currentMac = sb.toString();
                }

                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    String hostAddr = addr.getHostAddress();
                    if (!addr.isLoopbackAddress() && hostAddr.indexOf(':') == -1 && !hostAddr.startsWith("169.254.") && !hostAddr.startsWith("192.168.56.")) {
                        bestIp = hostAddr;
                        if (currentMac != null) bestMac = currentMac;
                        break;
                    }
                }
                if (!bestIp.equals("127.0.0.1")) break;
            }
        } catch (Exception ignored) {}

        this.resolvedIpAddress = bestIp;
        this.resolvedMacAddress = bestMac;
    }

    private void resolveHardwareIdentifier() {
        try {
            String rawSeed = this.resolvedHostname + "|" + System.getProperty("os.arch") + "|" +
                    System.getProperty("user.name") + "|" + this.resolvedMacAddress;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(rawSeed.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            this.resolvedHardwareId = "HWID-" + sb.toString().toUpperCase();
        } catch (Exception e) {
            this.resolvedHardwareId = "HWID-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }

    public String getDeviceId() {
        return resolvedDeviceId;
    }

    public String getDeviceToken() {
        return resolvedDeviceToken;
    }

    public String getBackendUrl() {
        String url = resolvedBackendUrl != null && !resolvedBackendUrl.isBlank() ? resolvedBackendUrl : defaultBackendUrl;
        if (url == null || url.isBlank()) {
            return "http://localhost:8080";
        }
        url = url.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1).trim();
        }
        return url;
    }

    public java.util.List<String> getBackendUrls() {
        java.util.List<String> list = new java.util.ArrayList<>();
        String primary = getBackendUrl();
        if (primary != null && !primary.isBlank()) {
            list.add(primary);
        }
        // Only include fallback discovery endpoints during initial unauthenticated discovery
        if (!isRegistered()) {
            String local = "http://localhost:8080";
            if (!list.contains(local)) {
                list.add(local);
            }
            String cloud = "https://aegisx-backend-2k67.onrender.com";
            if (!list.contains(cloud)) {
                list.add(cloud);
            }
        }
        return list;
    }

    public String getHostname() {
        return resolvedHostname != null ? resolvedHostname : "Endpoint-Node";
    }

    public String getHardwareId() {
        return resolvedHardwareId;
    }

    public String getIpAddress() {
        return resolvedIpAddress != null ? resolvedIpAddress : "127.0.0.1";
    }

    public String getMacAddress() {
        return resolvedMacAddress != null ? resolvedMacAddress : "00:00:00:00:00:00";
    }

    public boolean isRegistered() {
        return resolvedDeviceId != null && !resolvedDeviceId.isBlank() && !resolvedDeviceId.equals("6e9bed8e-41ee-4ad1-9304-f8dd9ecb5846");
    }
}

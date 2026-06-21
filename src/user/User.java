package user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 用户模型 */
public class User {

    private byte userId;
    private String username;
    private String passwordHash;
    private String homeDirectory;

    public User(byte userId, String username, String rawPassword) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = hashPassword(rawPassword);
        this.homeDirectory = "/home/" + username;
    }

    public User(byte userId, String username, String passwordHash, String homeDirectory) {
        this.userId = userId; this.username = username;
        this.passwordHash = passwordHash; this.homeDirectory = homeDirectory;
    }

    public boolean checkPassword(String raw) { return passwordHash.equals(hashPassword(raw)); }

    public static String hashPassword(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(raw.hashCode());
        }
    }

    public byte getUserId() { return userId; }
    public void setUserId(byte id) { this.userId = id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getHomeDirectory() { return homeDirectory; }
}

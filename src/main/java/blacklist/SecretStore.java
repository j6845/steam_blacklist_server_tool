package blacklist;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Stores the Steam API key encrypted in the preferences, never in plain text.
 *
 * <p>Windows: DPAPI for the current user (via PowerShell, Java has no API for it), so only the same
 * Windows account can decrypt it. Other systems: AES-GCM with a random key in a file only the user can read.
 */
final class SecretStore {

    private static final String PREF = "apiKeyEnc";
    /** Plain-text key written by versions up to 1.4.0, migrated on first load. */
    private static final String LEGACY_PREF = "apiKey";
    private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

    private SecretStore() {
    }

    /** Returns the stored key, or "" if none is stored or it can't be decrypted (e.g. another Windows user). */
    static String load(Preferences prefs) {
        String stored = prefs.get(PREF, null);
        if (stored == null) {
            String legacy = prefs.get(LEGACY_PREF, "");
            if (!legacy.isEmpty()) {
                try {
                    save(prefs, legacy);
                } catch (IOException e) {
                    return legacy;
                }
            }
            return legacy;
        }
        try {
            if (stored.startsWith("dpapi:")) {
                return powershell(DPAPI_DECRYPT, stored.substring(6));
            }
            if (stored.startsWith("aes:")) {
                return aesDecrypt(stored.substring(4));
            }
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            // fall through: the user simply enters the key again
        }
        return "";
    }

    /** Encrypts and stores the key; an empty key removes it. Also removes a leftover plain-text key. */
    static void save(Preferences prefs, String secret) throws IOException {
        if (secret.isEmpty()) {
            prefs.remove(PREF);
        } else if (WINDOWS) {
            prefs.put(PREF, "dpapi:" + powershell(DPAPI_ENCRYPT, secret));
        } else {
            try {
                prefs.put(PREF, "aes:" + aesEncrypt(secret));
            } catch (GeneralSecurityException e) {
                throw new IOException("Verschlüsselung fehlgeschlagen: " + e.getMessage(), e);
            }
        }
        prefs.remove(LEGACY_PREF);
    }

    // ------------------------------------------------------------ Windows: DPAPI

    private static final String DPAPI_ENCRYPT = "Add-Type -AssemblyName System.Security; "
            + "$in = [Console]::In.ReadToEnd(); "
            + "[Convert]::ToBase64String([Security.Cryptography.ProtectedData]::Protect("
            + "[Text.Encoding]::UTF8.GetBytes($in), $null, 'CurrentUser'))";
    private static final String DPAPI_DECRYPT = "Add-Type -AssemblyName System.Security; "
            + "$in = [Console]::In.ReadToEnd().Trim(); "
            + "[Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect("
            + "[Convert]::FromBase64String($in), $null, 'CurrentUser'))";

    /** Runs the script with {@code input} on stdin, so the secret never appears on a command line. */
    private static String powershell(String script, String input) throws IOException {
        String exe = System.getenv().getOrDefault("SystemRoot", "C:\\Windows")
                + "\\System32\\WindowsPowerShell\\v1.0\\powershell.exe";
        Process p = new ProcessBuilder(exe, "-NoProfile", "-NonInteractive", "-Command", script).start();
        try (OutputStream in = p.getOutputStream()) {
            in.write(input.getBytes(StandardCharsets.UTF_8));
        }
        String out = readAll(p.getInputStream()).trim();
        String err = readAll(p.getErrorStream()).trim();
        try {
            if (!p.waitFor(20, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("PowerShell antwortet nicht");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Abgebrochen", e);
        }
        if (p.exitValue() != 0 || out.isEmpty()) {
            throw new IOException("DPAPI fehlgeschlagen: " + err);
        }
        return out;
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        in.transferTo(buf);
        return buf.toString(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------ other systems: AES-GCM + key file

    private static Path keyFile() {
        return Path.of(System.getProperty("user.home"), ".steam_blacklist_tool", "secret.key");
    }

    private static byte[] aesKey() throws IOException {
        Path file = keyFile();
        if (Files.exists(file)) {
            return Files.readAllBytes(file);
        }
        Files.createDirectories(file.getParent());
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        Files.write(file, key);
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        }
        return key;
    }

    private static String aesEncrypt(String secret) throws IOException, GeneralSecurityException {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey(), "AES"), new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(secret.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
    }

    private static String aesDecrypt(String data) throws IOException, GeneralSecurityException {
        byte[] all = Base64.getDecoder().decode(data);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey(), "AES"), new GCMParameterSpec(128, all, 0, 12));
        return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
    }
}

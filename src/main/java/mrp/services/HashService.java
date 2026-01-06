package mrp.services;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Service für Passwort-Hashing
 * 
 * Verwendet SHA-256 für die Hash-Generierung. Passwörter werden nie als Klartext gespeichert
 */
public class HashService {

    // Hasht einen String mit SHA-256 und gibt den Hex-String zurück
    public static String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
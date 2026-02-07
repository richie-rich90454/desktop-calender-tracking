package ai;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

/*
 * AES-256-GCM encryption utility for API key security.
 *
 * Responsibilities:
 * - Encrypt API keys with password-based derivation
 * - Decrypt stored API keys when needed
 * - Generate secure random passwords
 * - Validate encrypted data format
 *
 * Java data types used:
 * - String
 * - byte[]
 * - SecretKey
 * - Cipher
 *
 * Java technologies involved:
 * - javax.crypto (AES-256-GCM, PBKDF2)
 * - Base64 encoding
 * - SecureRandom for cryptographic randomness
 *
 * Design intent:
 * API keys are NEVER stored in plain text.
 * All encryption uses password-based key derivation with salt.
 * Each encryption generates unique IV for semantic security.
 */
public class EncryptionUtil{
    private static final String ALGORITHM="AES/GCM/NoPadding";
    private static final int KEY_LENGTH=256;
    private static final int SALT_LENGTH=16;
    private static final int IV_LENGTH=12;
    private static final int TAG_LENGTH=128;
    private static final int ITERATIONS=65536;
    private EncryptionUtil(){
        
    }
    /**
     * Encrypts data using AES-256-GCM with a password.
     * 
     * @param data The data to encrypt
     * @param password The password for encryption
     * @return Base64 encoded string containing salt+IV+ciphertext
     * @throws Exception if encryption fails
     */
    public static String encrypt(String data, String password) throws Exception{
        if (data==null||data.isEmpty()){
            return "";
        }
        SecureRandom random=new SecureRandom();
        byte[] salt=new byte[SALT_LENGTH];
        byte[] iv=new byte[IV_LENGTH];
        random.nextBytes(salt);
        random.nextBytes(iv);
        SecretKey key=deriveKey(password, salt);
        Cipher cipher=Cipher.getInstance(ALGORITHM);
        GCMParameterSpec gcmSpec=new GCMParameterSpec(TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
        byte[] ciphertext=cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        byte[] combined=new byte[salt.length+iv.length+ciphertext.length];
        System.arraycopy(salt, 0, combined, 0, salt.length);
        System.arraycopy(iv, 0, combined, salt.length, iv.length);
        System.arraycopy(ciphertext, 0, combined, salt.length+iv.length, ciphertext.length);
        return Base64.getEncoder().encodeToString(combined);
    }
    /**
     * Decrypts data using AES-256-GCM with a password.
     * 
     * @param encryptedData Base64 encoded string containing salt+IV+ciphertext
     * @param password The password for decryption
     * @return Decrypted data
     * @throws Exception if decryption fails
     */
    public static String decrypt(String encryptedData, String password) throws Exception{
        if (encryptedData==null||encryptedData.isEmpty()){
            return "";
        }
        byte[] combined=Base64.getDecoder().decode(encryptedData);
        byte[] salt=new byte[SALT_LENGTH];
        byte[] iv=new byte[IV_LENGTH];
        byte[] ciphertext=new byte[combined.length - SALT_LENGTH - IV_LENGTH];
        System.arraycopy(combined, 0, salt, 0, SALT_LENGTH);
        System.arraycopy(combined, SALT_LENGTH, iv, 0, IV_LENGTH);
        System.arraycopy(combined, SALT_LENGTH+IV_LENGTH, ciphertext, 0, ciphertext.length);
        SecretKey key=deriveKey(password, salt);
        Cipher cipher=Cipher.getInstance(ALGORITHM);
        GCMParameterSpec gcmSpec=new GCMParameterSpec(TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
        byte[] plaintext=cipher.doFinal(ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }
    /**
     * Derives a secret key from a password and salt using PBKDF2.
     */
    private static SecretKey deriveKey(String password, byte[] salt) throws Exception{
        KeySpec spec=new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        SecretKeyFactory factory=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes=factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }
    /**
     * Generates a random password of specified length.
     */
    public static String generateRandomPassword(int length){
        String chars="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";
        SecureRandom random=new SecureRandom();
        StringBuilder sb=new StringBuilder(length);
        for (int i=0;i<length;i++){
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    /**
     * Checks if a string appears to be encrypted data.
     */
    public static boolean isEncrypted(String data){
        if (data==null||data.isEmpty()){
            return false;
        }
        try{
            byte[] decoded=Base64.getDecoder().decode(data);
            return decoded.length>=(SALT_LENGTH+IV_LENGTH+16);
        }
        catch (IllegalArgumentException e){
            return false;
        }
    }
}
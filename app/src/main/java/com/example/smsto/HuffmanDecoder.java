package com.example.smsto;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class HuffmanDecoder {
    private static final String TAG = "HuffmanDecoder";
    private static final Pattern BASE64_PATTERN = Pattern.compile("^[A-Za-z0-9+/=]*$");

    public String decode(String map, byte[] encoded) {
        if (map == null || map.isEmpty() || encoded == null || encoded.length == 0) {
            Log.e(TAG, "Invalid input: map or encoded data is null/empty");
            throw new IllegalArgumentException("Map or encoded data is null or empty");
        }

        try {
            Log.d(TAG, "Deserializing code map");
            Map<String, Character> codeMap = new HashMap<>();
            int validBits = deserializeCodeMap(map, codeMap);
            if (codeMap.isEmpty()) {
                throw new IllegalArgumentException("Code map is empty after deserialization");
            }

            // Validate validBits against encoded length
            int maxBits = encoded.length * 8;
            if (validBits > maxBits) {
                Log.e(TAG, "Valid bits (" + validBits + ") exceeds encoded data length (" + maxBits + ")");
                throw new IllegalArgumentException("Invalid bit count exceeds encoded data");
            }

            Log.d(TAG, "Converting bytes to bits");
            String bits = bytesToBits(encoded, validBits);
            if (bits.isEmpty()) {
                throw new IllegalArgumentException("No valid bits extracted from encoded data");
            }
            Log.d(TAG, "Bits to decode: " + bits);

            Log.d(TAG, "Decoding bits");
            StringBuilder decoded = new StringBuilder();
            StringBuilder currentCode = new StringBuilder();
            for (int i = 0; i < bits.length(); i++) {
                currentCode.append(bits.charAt(i));
                Character c = codeMap.get(currentCode.toString());
                if (c != null) {
                    decoded.append(c);
                    Log.d(TAG, "Decoded code: " + currentCode + " -> " + c);
                    currentCode.setLength(0);
                } else if (i == bits.length() - 1 && currentCode.length() > 0) {
                    Log.e(TAG, "Incomplete code in encoded data: " + currentCode);
                    throw new IllegalArgumentException("Incomplete code in encoded data: " + currentCode);
                }
            }

            String result = decoded.toString();
            if (result.isEmpty()) {
                throw new IllegalArgumentException("Decoded result is empty");
            }
            Log.d(TAG, "Decoded string length: " + result.length() + ", sample: " + result.substring(0, Math.min(50, result.length())));

            if (!BASE64_PATTERN.matcher(result).matches()) {
                String invalidChar = findInvalidBase64Char(result);
                Log.e(TAG, "Decoded string is not valid Base64: " + result.substring(0, Math.min(100, result.length())) + ", first invalid char: " + invalidChar);
                throw new IllegalArgumentException("Decoded string is not valid Base64, first invalid char: " + invalidChar);
            }

            Log.d(TAG, "Successfully decoded " + result.length() + " characters");
            return result;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Decoding failed: " + e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected decoding error: " + e.getMessage(), e);
            throw new IllegalArgumentException("Decoding failed: " + e.getMessage());
        }
    }

    private int deserializeCodeMap(String map, Map<String, Character> codeMap) {
        try {
            String[] parts = map.split("\\|", 2);
            if (parts.length != 2) {
                Log.e(TAG, "Invalid code map format: missing bit count");
                throw new IllegalArgumentException("Invalid code map format");
            }

            int validBits;
            try {
                validBits = Integer.parseInt(parts[0]);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid bit count in code map: " + parts[0]);
                throw new IllegalArgumentException("Invalid bit count");
            }

            String[] pairs = parts[1].split("(?<!\\\\);");
            for (String pair : pairs) {
                if (pair.isEmpty()) continue;
                String[] pairParts = pair.split("(?<!\\\\):", 2);
                if (pairParts.length != 2) {
                    Log.e(TAG, "Invalid code map pair: " + pair);
                    continue;
                }
                String character = decodeChar(pairParts[0]);
                String code = pairParts[1];
                if (character.length() != 1) {
                    Log.e(TAG, "Invalid character in code map: " + character);
                    continue;
                }
                char c = character.charAt(0);
                if (!BASE64_PATTERN.matcher(String.valueOf(c)).matches()) {
                    Log.w(TAG, "Non-Base64 character in code map: " + c);
                }
                codeMap.put(code, c);
                Log.d(TAG, "Code map entry: " + code + " -> " + c);
            }
            Log.d(TAG, "Deserialized code map with " + codeMap.size() + " entries, valid bits: " + validBits);
            return validBits;
        } catch (Exception e) {
            Log.e(TAG, "Failed to deserialize code map: " + e.getMessage(), e);
            throw new IllegalArgumentException("Failed to deserialize code map: " + e.getMessage());
        }
    }

    private String bytesToBits(byte[] bytes, int validBits) {
        try {
            StringBuilder bits = new StringBuilder();
            int bitsRead = 0;
            for (int i = 0; i < bytes.length && bitsRead < validBits; i++) {
                byte b = bytes[i];
                int bitsToRead = Math.min(8, validBits - bitsRead);
                for (int j = 7; j >= 8 - bitsToRead; j--) {
                    bits.append((b & (1 << j)) != 0 ? '1' : '0');
                    bitsRead++;
                }
            }
            String result = bits.toString();
            if (result.length() != validBits) {
                Log.w(TAG, "Expected " + validBits + " bits, got " + result.length());
            }
            Log.d(TAG, "Converted " + validBits + " valid bits, actual bits: " + result.length());
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Failed to convert bytes to bits: " + e.getMessage(), e);
            return "";
        }
    }

    private String decodeChar(String encoded) {
        return encoded.replace("\\:", ":").replace("\\;", ";").replace("\\|", "|").replace("\\\\", "\\");
    }

    private String findInvalidBase64Char(String str) {
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '+' || c == '/' || c == '=')) {
                return "char '" + c + "' at position " + i;
            }
        }
        return "unknown";
    }
}
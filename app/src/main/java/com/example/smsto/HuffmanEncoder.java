package com.example.smsto;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.regex.Pattern;

public class HuffmanEncoder {

    private static final String TAG = "HuffmanEncoder";
    private static final Pattern BASE64_PATTERN = Pattern.compile("^[A-Za-z0-9+/=]*$");
    private Map<Character, String> codeMap = new HashMap<>();
    private StringBuilder encodedData;
    private int validBits;

    public byte[] encode(String input) {
        if (input == null || input.isEmpty()) {
            Log.e(TAG, "Input string is null or empty");
            return null;
        }
        if (!BASE64_PATTERN.matcher(input).matches()) {
            Log.e(TAG, "Input is not valid Base64: " + input.substring(0, Math.min(100, input.length())));
            return null;
        }

        try {
            Log.d(TAG, "Input length: " + input.length() + ", sample: " + input.substring(0, Math.min(50, input.length())));
            codeMap.clear();
            encodedData = new StringBuilder();
            validBits = 0;
            buildHuffmanTree(input);

            Log.d(TAG, "Encoding input string");
            for (char c : input.toCharArray()) {
                String code = codeMap.get(c);
                if (code == null) {
                    throw new IllegalStateException("No Huffman code for character: " + c);
                }
                encodedData.append(code);
                validBits += code.length();
            }
            Log.d(TAG, "Encoded bits: " + validBits + ", sequence: " + encodedData.toString().substring(0, Math.min(100, encodedData.length())));

            Log.d(TAG, "Converting encoded bits to bytes");
            return bitsToBytes(encodedData.toString());
        } catch (Exception e) {
            Log.e(TAG, "Huffman encoding failed: " + e.getMessage(), e);
            return null;
        }
    }

    public String serializeCodeMap() {
        if (codeMap.isEmpty()) {
            Log.e(TAG, "Code map is empty");
            return "";
        }

        try {
            StringBuilder mapBuilder = new StringBuilder();
            mapBuilder.append(validBits).append("|");
            for (Map.Entry<Character, String> entry : codeMap.entrySet()) {
                mapBuilder.append(encodeChar(entry.getKey())).append(":").append(entry.getValue()).append(";");
            }
            String result = mapBuilder.toString();
            Log.d(TAG, "Serialized code map: " + result);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Failed to serialize code map: " + e.getMessage(), e);
            return "";
        }
    }

    private void buildHuffmanTree(String input) {
        Map<Character, Integer> freq = new HashMap<>();
        for (char c : input.toCharArray()) {
            freq.put(c, freq.getOrDefault(c, 0) + 1);
        }

        if (freq.isEmpty()) {
            Log.e(TAG, "Frequency map is empty");
            return;
        }

        PriorityQueue<Node> pq = new PriorityQueue<>((a, b) -> a.freq - b.freq);
        for (Map.Entry<Character, Integer> entry : freq.entrySet()) {
            pq.offer(new Node(entry.getKey(), entry.getValue()));
        }

        while (pq.size() > 1) {
            Node left = pq.poll();
            Node right = pq.poll();
            Node parent = new Node('\0', left.freq + right.freq);
            parent.left = left;
            parent.right = right;
            pq.offer(parent);
        }

        if (!pq.isEmpty()) {
            generateCodes(pq.poll(), "");
        }
    }

    private void generateCodes(Node node, String code) {
        if (node == null) {
            return;
        }
        if (node.character != '\0') {
            if (!BASE64_PATTERN.matcher(String.valueOf(node.character)).matches()) {
                Log.w(TAG, "Non-Base64 character in code map: " + node.character);
            }
            codeMap.put(node.character, code);
            Log.d(TAG, "Code for '" + node.character + "': " + code);
        }
        generateCodes(node.left, code + "0");
        generateCodes(node.right, code + "1");
    }

    private byte[] bitsToBytes(String bits) {
        if (bits == null || bits.isEmpty()) {
            Log.e(TAG, "Bits string is null or empty");
            return new byte[0];
        }

        try {
            int byteCount = (validBits + 7) / 8;
            byte[] bytes = new byte[byteCount];
            for (int i = 0; i < validBits; i++) {
                if (bits.charAt(i) == '1') {
                    bytes[i / 8] |= (1 << (7 - (i % 8)));
                }
            }
            // Clear unused bits in the last byte
            if (validBits % 8 != 0) {
                int bitsInLastByte = validBits % 8;
                bytes[byteCount - 1] &= (byte) ((1 << bitsInLastByte) - 1);
            }
            Log.d(TAG, "Encoded bits: " + validBits + ", bytes: " + byteCount + ", hex: " + bytesToHex(bytes));
            return bytes;
        } catch (Exception e) {
            Log.e(TAG, "Failed to convert bits to bytes: " + e.getMessage(), e);
            return new byte[0];
        }
    }

    private String encodeChar(char c) {
        if (c == ':') {
            return "\\:";
        } else if (c == ';') {
            return "\\;";
        } else if (c == '|') {
            return "\\|";
        } else if (c == '\\') {
            return "\\\\";
        }
        return String.valueOf(c);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }

    private static class Node {
        char character;
        int freq;
        Node left, right;

        Node(char character, int freq) {
            this.character = character;
            this.freq = freq;
        }
    }
}
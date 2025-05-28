package com.example.smsto;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ClipDescription;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;

public class DecodeActivity extends AppCompatActivity {

    private static final int CREATE_FILE_REQUEST_CODE = 100;
    private static final String TAG = "DecodeActivity";

    private EditText smsInput;
    private ImageView imageView;
    private Button decodeBtn, pasteBtn, switchBtn, saveBtn;
    private ProgressBar progressBar;
    private Bitmap decodedBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_decode);

        try {
            smsInput = findViewById(R.id.sms_input);
            imageView = findViewById(R.id.decoded_image);
            decodeBtn = findViewById(R.id.decode_button);
            pasteBtn = findViewById(R.id.paste_button);
            switchBtn = findViewById(R.id.btn_switch_to_encoder);
            saveBtn = findViewById(R.id.save_button);
            progressBar = findViewById(R.id.progress_bar);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize views: " + e.getMessage(), e);
            Toast.makeText(this, "Initialization error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        decodeBtn.setOnClickListener(v -> decodeImage());
        pasteBtn.setOnClickListener(v -> pasteFromClipboard());
        switchBtn.setOnClickListener(v -> {
            Intent intent = new Intent(DecodeActivity.this, MainActivity.class);
            startActivity(intent);
        });
        saveBtn.setOnClickListener(v -> saveImage());
        saveBtn.setEnabled(false);
    }

    private void decodeImage() {
        String smsText = smsInput.getText().toString().trim().replaceAll("\\n", "");
        if (smsText.isEmpty()) {
            Toast.makeText(this, "No text to decode!", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        saveBtn.setEnabled(false);

        new Thread(() -> {
            try {
                Log.d(TAG, "Starting decompression, input length: " + smsText.length() + ", sample: " + smsText.substring(0, Math.min(50, smsText.length())));
                String base64Image = decompressString(smsText);
                if (base64Image == null || base64Image.isEmpty()) {
                    Log.e(TAG, "Decompressed text is null or empty");
                    runOnUiThread(() -> Toast.makeText(this, "Invalid text format", Toast.LENGTH_SHORT).show());
                    return;
                }
                Log.d(TAG, "Decompressed Base64 length: " + base64Image.length() + ", sample: " + base64Image.substring(0, Math.min(50, base64Image.length())));

                Log.d(TAG, "Decoding Base64 to image bytes");
                byte[] imageBytes;
                try {
                    imageBytes = Base64.decode(base64Image, Base64.NO_WRAP);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Base64 decoding failed: " + e.getMessage() + ", input: " + base64Image.substring(0, Math.min(100, base64Image.length())));
                    throw new IllegalArgumentException("Bad Base-64");
                }
                if (imageBytes.length == 0) {
                    throw new IllegalArgumentException("Base64 decoding produced no bytes");
                }

                Log.d(TAG, "Creating bitmap");
                decodedBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                if (decodedBitmap == null) {
                    throw new IllegalStateException("Failed to create bitmap from decoded bytes");
                }

                runOnUiThread(() -> {
                    imageView.setImageBitmap(decodedBitmap);
                    saveBtn.setEnabled(true);
                    Toast.makeText(this, "Decoded successfully!", Toast.LENGTH_SHORT).show();
                });
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Decoding error: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                Log.e(TAG, "Decoding failed: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(this, "Decoding failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                runOnUiThread(() -> progressBar.setVisibility(View.GONE));
            }
        }).start();
    }

    private void saveImage() {
        if (decodedBitmap == null) {
            Toast.makeText(this, "No image to save!", Toast.LENGTH_SHORT).show();
            return;
        }

        String filename = "decoded_image_" + System.currentTimeMillis() + ".png";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        startActivityForResult(intent, CREATE_FILE_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CREATE_FILE_REQUEST_CODE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                decodedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                Toast.makeText(this, "Image saved successfully!", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Log.e(TAG, "Failed to save image: " + e.getMessage(), e);
                Toast.makeText(this, "Failed to save image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void pasteFromClipboard() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                if (item != null && item.getText() != null) {
                    String pasteData = item.getText().toString().replaceAll("\\n", "");
                    smsInput.setText(pasteData);
                    Toast.makeText(this, "Pasted from clipboard!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "No text in clipboard!", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Clipboard is empty or contains invalid data!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to paste from clipboard: " + e.getMessage(), e);
            Toast.makeText(this, "Clipboard error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String decompressString(String compressedStr) {
        if (compressedStr == null || compressedStr.isEmpty()) {
            Log.e(TAG, "Compressed string is null or empty");
            return null;
        }
        try {
            byte[] compressedBytes = Base64.decode(compressedStr, Base64.NO_WRAP);
            GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressedBytes));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int n;
            while ((n = gis.read(buffer)) >= 0) {
                baos.write(buffer, 0, n);
            }
            gis.close();
            String result = new String(baos.toByteArray(), "UTF-8");
            Log.d(TAG, "Decompressed string length: " + result.length());
            return result;
        } catch (IOException e) {
            Log.e(TAG, "Decompression failed: " + e.getMessage(), e);
            return null;
        }
    }
}
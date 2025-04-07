package com.example.rimagine.ui.Photo;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.rimagine.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.button.MaterialButton;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PhotoFragment extends Fragment {

    private ImageView photoImageView;
    private FloatingActionButton galleryFab;
    private FloatingActionButton cameraFab;
    private MaterialButton processButton;
    private Uri imageUri;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openCamera();
                } else {
                    Toast.makeText(requireContext(), "Camera permission is required", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && imageUri != null) {
                    showImage(imageUri);
                }
            });

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri uri) {
                    if (uri != null) {
                        imageUri = uri;  // Store the gallery image URI
                        showImage(uri);
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_photo_layout, container, false);

        photoImageView = view.findViewById(R.id.photoImageView);
        galleryFab = view.findViewById(R.id.galleryFab);
        cameraFab = view.findViewById(R.id.cameraFab);
        processButton = view.findViewById(R.id.processButton);

        // Initially disable the process button until an image is selected
        processButton.setEnabled(false);
        processButton.setAlpha(0.6f);

        setupClickListeners();
        animateButtons();

        return view;
    }

    private void setupClickListeners() {
        galleryFab.setOnClickListener(v -> {
            animateButtonClick(v);
            openGallery();
        });
        
        cameraFab.setOnClickListener(v -> {
            animateButtonClick(v);
            checkCameraPermissionAndOpenCamera();
        });
        
        processButton.setOnClickListener(v -> {
            animateButtonClick(v);
            processImage();
        });
    }

    private void animateButtons() {
        galleryFab.setScaleX(0f);
        galleryFab.setScaleY(0f);
        galleryFab.setAlpha(0f);
        
        cameraFab.setScaleX(0f);
        cameraFab.setScaleY(0f);
        cameraFab.setAlpha(0f);

        galleryFab.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(300)
                .setStartDelay(300)
                .setInterpolator(new OvershootInterpolator())
                .start();

        cameraFab.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(300)
                .setStartDelay(400)
                .setInterpolator(new OvershootInterpolator())
                .start();
    }

    private void animateButtonClick(View view) {
        view.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction(() -> {
                    view.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start();
                })
                .start();
    }

    private void showImage(Uri uri) {
        // Fade out current image
        photoImageView.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction(() -> {
                    // Set new image and fade it in
                    photoImageView.setImageURI(uri);
                    photoImageView.animate()
                            .alpha(1f)
                            .setDuration(150)
                            .start();
                })
                .start();

        // Enable and animate process button
        processButton.setEnabled(true);
        processButton.animate()
                .alpha(1f)
                .setDuration(200)
                .start();
    }

    private void openGallery() {
        galleryLauncher.launch("image/*");
    }

    private void checkCameraPermissionAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From Camera");

        imageUri = requireContext().getContentResolver()
                .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        cameraLauncher.launch(imageUri);
    }

    private void processImage() {
        if (imageUri == null) {
            Toast.makeText(requireContext(), "Please select an image first", Toast.LENGTH_SHORT).show();
            return;
        }

        // Animate the process button
        ObjectAnimator rotationAnimator = ObjectAnimator.ofFloat(
                processButton.getIcon(), "level", 0f, 10000f);
        rotationAnimator.setDuration(1000);
        rotationAnimator.start();

        // Show processing toast
        Toast.makeText(requireContext(), "Processing image...", Toast.LENGTH_SHORT).show();

        // Run the Python script in a background thread
        new Thread(() -> {
            try {
                // Copy the image to our app's files directory
                String imagePath = copyImageToAppFiles(imageUri);
                if (imagePath == null) {
                    throw new IOException("Could not copy image file");
                }
                
                // Get the path to the Python script in assets
                String pythonScriptPath = requireContext().getFilesDir() + "/predict.py";
                String modelPath = requireContext().getFilesDir() + "/model.pt";
                
                // Copy the Python script and model from assets to files directory
                copyAssetToFile("predict.py", pythonScriptPath);
                copyAssetToFile("model.pt", modelPath);
                
                // Make the Python script executable
                new File(pythonScriptPath).setExecutable(true);
                
                // Execute the Python script
                ProcessBuilder processBuilder = new ProcessBuilder("python", pythonScriptPath, imagePath);
                processBuilder.redirectErrorStream(true);
                Process process = processBuilder.start();
                
                // Read the output
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                
                // Wait for the process to complete
                int exitCode = process.waitFor();
                
                // Parse the JSON output
                JSONObject result = new JSONObject(output.toString());
                
                // Show the result on the main thread
                requireActivity().runOnUiThread(() -> {
                    try {
                        if (exitCode == 0 && "success".equals(result.getString("status"))) {
                            // Get the output image path
                            String outputImagePath = result.getString("output_image");
                            
                            // Load and display the output image
                            File outputImage = new File(outputImagePath);
                            if (outputImage.exists()) {
                                // Fade out current image
                                photoImageView.animate()
                                        .alpha(0f)
                                        .setDuration(150)
                                        .withEndAction(() -> {
                                            // Set new image and fade it in
                                            photoImageView.setImageURI(Uri.fromFile(outputImage));
                                            photoImageView.animate()
                                                    .alpha(1f)
                                                    .setDuration(150)
                                                    .start();
                                        })
                                        .start();
                                
                                // Show prediction details
                                StringBuilder predictionText = new StringBuilder("Predictions:\n");
                                JSONArray predictions = result.getJSONArray("predictions");
                                for (int i = 0; i < predictions.length(); i++) {
                                    JSONObject pred = predictions.getJSONObject(i);
                                    try {
                                        predictionText.append(String.format("%s (%.2f%%)\n", 
                                            pred.getString("class"),
                                            pred.getDouble("confidence") * 100));
                                    } catch (JSONException e) {
                                        predictionText.append("Error reading prediction details\n");
                                    }
                                }
                                Toast.makeText(requireContext(), predictionText.toString(), Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(requireContext(), "Error: Output image not found", Toast.LENGTH_LONG).show();
                            }
                        } else {
                            String errorMessage = "Error: Unknown error occurred";
                            try {
                                errorMessage = "Error: " + result.getString("message");
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(requireContext(), 
                            "Error parsing JSON response: " + e.getMessage(), 
                            Toast.LENGTH_LONG).show();
                    }
                });
                
            } catch (Exception e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), 
                        "Error processing image: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /**
     * Copies the image from the given Uri to the app's files directory
     * @return The path to the copied image file, or null if copying failed
     */
    private String copyImageToAppFiles(Uri sourceUri) {
        try {
            // Create a unique filename for the image
            String timestamp = String.valueOf(System.currentTimeMillis());
            String filename = "image_" + timestamp + ".jpg";
            File destFile = new File(requireContext().getFilesDir(), filename);
            
            // Copy the image data
            InputStream in = requireContext().getContentResolver().openInputStream(sourceUri);
            if (in == null) {
                return null;
            }
            
            OutputStream out = new FileOutputStream(destFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            
            in.close();
            out.close();
            
            return destFile.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Gets the real file path from a Uri
     */
    private String getRealPathFromUri(Uri uri) {
        String[] projection = {MediaStore.Images.Media.DATA};
        android.database.Cursor cursor = requireContext().getContentResolver().query(uri, projection, null, null, null);
        
        if (cursor == null) {
            return null;
        }
        
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String path = cursor.getString(column_index);
        cursor.close();
        
        return path;
    }

    private void copyAssetToFile(String assetName, String filePath) throws IOException {
        InputStream in = requireContext().getAssets().open(assetName);
        File outFile = new File(filePath);
        FileOutputStream out = new FileOutputStream(outFile);
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        in.close();
        out.close();
    }
}


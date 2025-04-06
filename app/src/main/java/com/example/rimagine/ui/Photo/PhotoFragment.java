package com.example.rimagine.ui.Photo;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.google.android.material.button.MaterialButton;

public class PhotoFragment extends Fragment {

    private ImageView photoImageView;
    private MaterialButton galleryButton;
    private MaterialButton cameraButton;
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
                    photoImageView.setImageURI(imageUri);
                    processButton.setEnabled(true);
                }
            });

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri uri) {
                    if (uri != null) {
                        photoImageView.setImageURI(uri);
                        processButton.setEnabled(true);
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_photo_layout, container, false);

        photoImageView = view.findViewById(R.id.photoImageView);
        galleryButton = view.findViewById(R.id.galleryButton);
        cameraButton = view.findViewById(R.id.cameraButton);
        processButton = view.findViewById(R.id.processButton);

        // Initially disable the process button until an image is selected
        processButton.setEnabled(false);

        galleryButton.setOnClickListener(v -> openGallery());
        cameraButton.setOnClickListener(v -> checkCameraPermissionAndOpenCamera());
        processButton.setOnClickListener(v -> processImage());

        return view;
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
        // This will be implemented in the future for model processing
        Toast.makeText(requireContext(), "Processing image...", Toast.LENGTH_SHORT).show();
    }
}


package com.example.rimagine.ui.Photo;

import android.Manifest;
import android.animation.ObjectAnimator;
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
        // Animate the process button
        ObjectAnimator rotationAnimator = ObjectAnimator.ofFloat(
                processButton.getIcon(), "level", 0f, 10000f);
        rotationAnimator.setDuration(1000);
        rotationAnimator.start();

        // Show processing toast
        Toast.makeText(requireContext(), "Processing image...", Toast.LENGTH_SHORT).show();
    }
}


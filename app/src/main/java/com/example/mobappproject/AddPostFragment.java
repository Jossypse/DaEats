package com.example.mobappproject;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import android.util.Base64;
import android.widget.ImageView;
import android.widget.ImageButton;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.fragment.app.Fragment;
import android.app.TimePickerDialog;
import java.util.Calendar;
import java.util.Locale;

public class AddPostFragment extends Fragment implements LocationPickerFragment.OnLocationPickedListener {
    private static final int PICK_IMAGES_REQUEST = 1;
    private static final int PICK_PLACE_REQUEST = 2;
    private List<Uri> selectedImageUris = new ArrayList<>();
    private RecyclerView rvImagePreview;
    private ImageAdapter imageAdapter;
    private EditText etName, etAddress, etDescription, etContactNumber, etOpenTime, etCloseTime;
    private Button btnSelectImages, btnSubmitPost;
    private Button btnPickLocation;
    private ImageButton btnClearImage;
    private Double selectedLat = null, selectedLng = null;
    private String openTime = "", closeTime = "";
    private String userId; // Add this field to store the current user's ID
    private com.google.android.material.button.MaterialButtonToggleGroup rgStoreType;
    private String storeType = null;

    private static final int REQUEST_PERMISSION_READ_IMAGES = 100;
    private AlertDialog progressDialog;

    private void showProgressDialog(String message) {
        if (progressDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_progress, null);
            builder.setView(dialogView);
            builder.setCancelable(false);
            progressDialog = builder.create();
        }
        TextView tvProgress = progressDialog.findViewById(R.id.tvProgress);
        if (tvProgress != null) tvProgress.setText(message);
        if (!progressDialog.isShowing()) progressDialog.show();
    }

    private void hideProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_READ_IMAGES) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchGalleryIntent();
            } else {
                Toast.makeText(getContext(), "Permission denied to access images", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_add_post, container, false);

        rvImagePreview = view.findViewById(R.id.rvImagePreview);
        imageAdapter = new ImageAdapter(selectedImageUris);
        rvImagePreview.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rvImagePreview.setAdapter(imageAdapter);
        etName = view.findViewById(R.id.etName);
        etAddress = view.findViewById(R.id.etAddress);
        etDescription = view.findViewById(R.id.etDescription);
        etContactNumber = view.findViewById(R.id.etContactNumber);
        etOpenTime = view.findViewById(R.id.etOpenTime);
        etCloseTime = view.findViewById(R.id.etCloseTime);
        btnSelectImages = view.findViewById(R.id.btnSelectImages);
        btnSubmitPost = view.findViewById(R.id.btnSubmitPost);
        btnPickLocation = view.findViewById(R.id.btnPickLocation);
        btnSelectImages.setOnClickListener(v -> openImagePicker());
        btnSubmitPost.setOnClickListener(v -> submitPost());
        btnPickLocation.setOnClickListener(v -> launchLocationPickerDialog());
        etOpenTime.setOnClickListener(v -> showTimePicker(etOpenTime, true));
        etCloseTime.setOnClickListener(v -> showTimePicker(etCloseTime, false));
        rgStoreType = view.findViewById(R.id.rgStoreType);
        rgStoreType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.rbCafe) storeType = "Cafe";
                else if (checkedId == R.id.rbRestaurant) storeType = "Restaurant";
                else if (checkedId == R.id.rbBoth) storeType = "Both";
            } else if (rgStoreType.getCheckedButtonId() == -1) {
                storeType = null;
            }
        });

        // Initialize Places SDK if not already
        if (!Places.isInitialized()) {
            Places.initialize(requireContext().getApplicationContext(), "AIzaSyAMAVWWep42bXcd6ButIVnJlvwsnIS54po");
        }

        // Fetch userId from SharedPreferences
        android.content.Context context = getContext();
        if (context != null) {
            android.content.SharedPreferences prefs = context.getSharedPreferences("user_session", android.content.Context.MODE_PRIVATE);
            userId = prefs.getString("userId", null);
        }
        return view;
    }

    private void showImagePreview(Bitmap bitmap) {
        if (bitmap != null) {
            // This method is no longer used for single image preview, but kept for consistency
            // The ImageAdapter handles the preview of multiple images.
        }
    }

    private void clearSelectedImage() {
        // This method is no longer used for single image clearing.
    }

    private void openImagePicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQUEST_PERMISSION_READ_IMAGES);
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION_READ_IMAGES);
                return;
            }
        }
        launchGalleryIntent();
    }

    private void launchGalleryIntent() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Select Pictures"), PICK_IMAGES_REQUEST);
    }

    private void launchLocationPickerDialog() {
        LocationPickerFragment picker = new LocationPickerFragment();
        picker.setListener(this);
        picker.show(getParentFragmentManager(), "location_picker");
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGES_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            selectedImageUris.clear();
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri imageUri = data.getClipData().getItemAt(i).getUri();
                    selectedImageUris.add(imageUri);
                }
            } else if (data.getData() != null) {
                selectedImageUris.add(data.getData());
            }
            imageAdapter.notifyDataSetChanged();
        } else if (requestCode == PICK_PLACE_REQUEST) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                Place place = Autocomplete.getPlaceFromIntent(data);
                etAddress.setText(place.getAddress());
                if (place.getLatLng() != null) {
                    selectedLat = place.getLatLng().latitude;
                    selectedLng = place.getLatLng().longitude;
                }
            } else if (resultCode == AutocompleteActivity.RESULT_ERROR) {
                // etAddress.setText("Failed to pick location"); // Removed as per edit hint
            } else if (resultCode == Activity.RESULT_CANCELED) {
                // User canceled
            }
        }
    }

    private void showTimePicker(EditText target, boolean isOpen) {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        // Use spinner style for TimePickerDialog
        TimePickerDialog timePickerDialog = new TimePickerDialog(
            getContext(),
            android.R.style.Theme_Holo_Light_Dialog_NoActionBar,
            (view, hourOfDay, minuteOfHour) -> {
                String amPm = hourOfDay < 12 ? "AM" : "PM";
                int hour12 = hourOfDay % 12;
                if (hour12 == 0) hour12 = 12;
                String time = String.format(Locale.getDefault(), "%02d:%02d %s", hour12, minuteOfHour, amPm);
                target.setText(time);
                if (isOpen) openTime = time; else closeTime = time;
            },
            hour, minute, false // false = 12-hour format
        );
        timePickerDialog.show();
    }

    private void submitPost() {
        String name = etName.getText().toString().trim();
        String address = etAddress.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String contactNumber = etContactNumber.getText().toString().trim();
        String openTimeVal = etOpenTime.getText().toString().trim();
        String closeTimeVal = etCloseTime.getText().toString().trim();
        String typeVal = storeType;

        if (name.isEmpty() || address.isEmpty() || description.isEmpty() || contactNumber.isEmpty() || openTimeVal.isEmpty() || closeTimeVal.isEmpty() || selectedImageUris.isEmpty() || selectedLat == null || selectedLng == null || typeVal == null) {
            Toast.makeText(getContext(), "Please fill all fields, select at least one image, pick a location, and select a type", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmitPost.setEnabled(false);
        showProgressDialog("Uploading post...");
        encodeAndUploadPost(name, address, description, contactNumber, openTimeVal, closeTimeVal, selectedImageUris, typeVal);
    }

    private void encodeAndUploadPost(String name, String address, String description, String contactNumber, String openTime, String closeTime, List<Uri> imageUris, String type) {
        try {
            List<String> base64Images = new ArrayList<>();
            for (Uri imageUri : imageUris) {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(requireActivity().getContentResolver(), imageUri);
                Bitmap resized = Bitmap.createScaledBitmap(bitmap, 400, 400, true);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                resized.compress(Bitmap.CompressFormat.JPEG, 60, baos);
                byte[] imageBytes = baos.toByteArray();
                String base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT);
                base64Images.add(base64Image);
            }
            DatabaseReference postsRef = FirebaseDatabase.getInstance().getReference("posts");
            String uniqueId = postsRef.push().getKey();
            String postId = (userId != null ? userId + "-" + uniqueId : uniqueId); // Ensure userId is included
            Map<String, Object> postMap = new HashMap<>();
            postMap.put("name", name);
            postMap.put("address", address);
            postMap.put("description", description);
            postMap.put("contactNumber", contactNumber);
            postMap.put("openTime", openTime);
            postMap.put("closeTime", closeTime);
            postMap.put("imagesBase64", base64Images);
            postMap.put("timestamp", System.currentTimeMillis());
            postMap.put("latitude", selectedLat);
            postMap.put("longitude", selectedLng);
            postMap.put("userId", userId); // Optionally store userId in the post data
            postMap.put("type", type);
            postMap.put("likes", 0); // Initialize likes to 0
            postsRef.child(postId).setValue(postMap)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Post uploaded successfully", Toast.LENGTH_SHORT).show();
                    etName.setText("");
                    etAddress.setText("");
                    etDescription.setText("");
                    etContactNumber.setText("");
                    etOpenTime.setText("");
                    etCloseTime.setText("");
                    selectedImageUris.clear();
                    imageAdapter.notifyDataSetChanged();
                    selectedLat = null;
                    selectedLng = null;
                    rgStoreType.clearChecked();
                    storeType = null;
                    btnSubmitPost.setEnabled(true);
                    hideProgressDialog();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to upload post", Toast.LENGTH_SHORT).show();
                    btnSubmitPost.setEnabled(true);
                    hideProgressDialog();
                });
        } catch (Exception e) {
            Toast.makeText(getContext(), "Failed to process images", Toast.LENGTH_SHORT).show();
            btnSubmitPost.setEnabled(true);
            hideProgressDialog();
        }
    }

    @Override
    public void onLocationPicked(String address, double lat, double lng) {
        etAddress.setText(address);
        selectedLat = lat;
        selectedLng = lng;
    }
} 
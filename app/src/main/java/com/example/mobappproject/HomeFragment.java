package com.example.mobappproject;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.app.AlertDialog;
import androidx.viewpager2.widget.ViewPager2;
import android.widget.LinearLayout;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import androidx.annotation.Nullable;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.ImageButton;

public class HomeFragment extends Fragment {
    private RecyclerView rvPosts;
    private PostAdapter postAdapter;
    private List<Post> postList = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvEmpty;
    private FusedLocationProviderClient fusedLocationClient;
    private FrameLayout popupOverlayContainer;
    private View popupView; // To keep reference for removal

    // Add the click listener interface here
    public interface OnPostClickListener {
        void onPostClick(Post post);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        rvPosts = view.findViewById(R.id.rvPosts);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        popupOverlayContainer = view.findViewById(R.id.popupOverlayContainer);
        postAdapter = new PostAdapter(postList, this::showPostPopup);
        rvPosts.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rvPosts.setAdapter(postAdapter);
        swipeRefreshLayout.setOnRefreshListener(this::loadPosts);
        loadPosts();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    postAdapter.userLocation = location;
                    postAdapter.notifyDataSetChanged();
                }
            });
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                    if (location != null) {
                        postAdapter.userLocation = location;
                        postAdapter.notifyDataSetChanged();
                    }
                });
            }
        }
    }

    private void loadPosts() {
        swipeRefreshLayout.setRefreshing(true);
        DatabaseReference postsRef = FirebaseDatabase.getInstance().getReference("posts");
        postsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                postList.clear();
                for (DataSnapshot postSnap : snapshot.getChildren()) {
                    Post post = postSnap.getValue(Post.class);
                    postList.add(post);
                }
                postAdapter.notifyDataSetChanged();
                swipeRefreshLayout.setRefreshing(false);
                tvEmpty.setVisibility(postList.isEmpty() ? View.VISIBLE : View.GONE);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    // Function to show popup overlay with post details
    private void showPostPopup(Post post) {
        if (getContext() == null || popupOverlayContainer == null) return;
        LayoutInflater inflater = LayoutInflater.from(getContext());
        popupView = inflater.inflate(R.layout.test, popupOverlayContainer, false);
        // Populate popupView with post data
        ViewPager2 imageSlider = popupView.findViewById(R.id.image_slider);
        LinearLayout indicatorLayout = popupView.findViewById(R.id.image_slider_indicator);
        TextView placeTitle = popupView.findViewById(R.id.place_title);
        TextView placeDescription = popupView.findViewById(R.id.place_description);
        TextView locationText = popupView.findViewById(R.id.location_text);
        // Set up image slider and indicators
        if (post.imagesBase64 != null && !post.imagesBase64.isEmpty()) {
            ImageSliderAdapter sliderAdapter = new ImageSliderAdapter(post.imagesBase64);
            imageSlider.setAdapter(sliderAdapter);
            imageSlider.setVisibility(View.VISIBLE);
            indicatorLayout.setVisibility(post.imagesBase64.size() > 1 ? View.VISIBLE : View.GONE);
            // Create indicators
            indicatorLayout.removeAllViews();
            int count = post.imagesBase64.size();
            for (int i = 0; i < count; i++) {
                View dot = new View(getContext());
                int size = (int) (imageSlider.getResources().getDisplayMetrics().density * 8);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
                params.setMargins(size/2, 0, size/2, 0);
                dot.setLayoutParams(params);
                dot.setBackgroundResource(i == 0 ? R.drawable.circle_filled : R.drawable.circle_empty);
                indicatorLayout.addView(dot);
            }
            // Listen for page changes to update indicators
            imageSlider.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    for (int i = 0; i < indicatorLayout.getChildCount(); i++) {
                        indicatorLayout.getChildAt(i).setBackgroundResource(i == position ? R.drawable.circle_filled : R.drawable.circle_empty);
                    }
                }
            });
        } else {
            imageSlider.setVisibility(View.GONE);
            indicatorLayout.setVisibility(View.GONE);
        }
        placeTitle.setText(post.name);
        placeDescription.setText(post.description);
        locationText.setText(post.address);
        // Add close on background tap
        popupOverlayContainer.setOnClickListener(v -> hidePopup());
        // Prevent click-through on popupView
        popupView.setOnClickListener(v -> {});
        // Add a visually improved close button
        Button closeBtn = new Button(getContext());
        closeBtn.setText("Close");
        closeBtn.setBackgroundResource(R.drawable.rounded_button); // Use your rounded_button drawable
        closeBtn.setTextColor(getResources().getColor(android.R.color.white));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeParams.topMargin = (int) (getResources().getDisplayMetrics().density * 8);
        closeParams.gravity = android.view.Gravity.END;
        closeBtn.setLayoutParams(closeParams);
        ((LinearLayout) popupView.findViewById(R.id.place_title).getParent()).addView(closeBtn);
        closeBtn.setOnClickListener(v -> hidePopup());
        // Show popup
        popupOverlayContainer.removeAllViews();
        popupOverlayContainer.addView(popupView);
        popupOverlayContainer.setVisibility(View.VISIBLE);

        ImageButton btnLike = popupView.findViewById(R.id.btn_like);
        // Like button logic: toggle heart icon
        final boolean[] liked = {false};
        btnLike.setOnClickListener(v -> {
            liked[0] = !liked[0];
            if (liked[0]) {
                btnLike.setColorFilter(getResources().getColor(android.R.color.holo_red_dark));
                // Optionally show a toast or animation
            } else {
                btnLike.setColorFilter(getResources().getColor(android.R.color.darker_gray));
            }
        });
        btnLike.setColorFilter(getResources().getColor(android.R.color.darker_gray));
    }
    private void hidePopup() {
        if (popupOverlayContainer != null) {
            popupOverlayContainer.setVisibility(View.GONE);
            popupOverlayContainer.removeAllViews();
        }
    }

    // Post model
    public static class Post {
        public String name, address, description;
        public List<String> imagesBase64;
        public Double latitude, longitude;
        public Post() {}
    }

    // Adapter
    public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {
        private List<Post> posts;
        private Location userLocation;
        private FusedLocationProviderClient fusedLocationClient;
        private OnPostClickListener onPostClickListener;
        public PostAdapter(List<Post> posts, OnPostClickListener listener) {
            this.posts = posts;
            this.onPostClickListener = listener;
        }
        @NonNull
        @Override
        public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post, parent, false);
            return new PostViewHolder(v);
        }
        @Override
        public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
            Post post = posts.get(position);
            holder.tvName.setText(post.name);
            holder.tvAddress.setText(post.address);
            holder.tvDescription.setText(post.description);
            // Set the first image only
            if (post.imagesBase64 != null && !post.imagesBase64.isEmpty()) {
                String base64 = post.imagesBase64.get(0);
                if (base64 != null && !base64.isEmpty()) {
                    try {
                        byte[] imageBytes = Base64.decode(base64, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                        holder.ivPostImage.setImageBitmap(bitmap);
                    } catch (Exception e) {
                        holder.ivPostImage.setImageResource(android.R.color.darker_gray);
                    }
                } else {
                    holder.ivPostImage.setImageResource(android.R.color.darker_gray);
                }
            } else {
                holder.ivPostImage.setImageResource(android.R.color.darker_gray);
            }
            // Distance and Directions
            if (post.latitude != null && post.longitude != null && userLocation != null) {
                float[] results = new float[1];
                Location.distanceBetween(userLocation.getLatitude(), userLocation.getLongitude(), post.latitude, post.longitude, results);
                float distanceMeters = results[0];
                String distanceStr = String.format("%.1f km", distanceMeters / 1000);
                holder.tvDistance.setText("Distance: " + distanceStr);
                holder.btnGetDirections.setVisibility(View.VISIBLE);
            } else {
                holder.tvDistance.setText("");
                holder.btnGetDirections.setVisibility(View.GONE);
            }
            holder.btnGetDirections.setOnClickListener(v -> {
                if (post.latitude != null && post.longitude != null) {
                    Uri gmmIntentUri = Uri.parse("google.navigation:q=" + post.latitude + "," + post.longitude);
                    Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                    mapIntent.setPackage("com.google.android.apps.maps");
                    v.getContext().startActivity(mapIntent);
                }
            });
            holder.itemView.setOnClickListener(v -> {
                if (onPostClickListener != null) onPostClickListener.onPostClick(post);
            });
        }
        @Override
        public int getItemCount() { return posts.size(); }
        class PostViewHolder extends RecyclerView.ViewHolder {
            ImageView ivPostImage;
            TextView tvName, tvAddress, tvDescription;
            TextView tvDistance;
            View btnGetDirections;
            public PostViewHolder(@NonNull View itemView) {
                super(itemView);
                ivPostImage = itemView.findViewById(R.id.ivPostImage);
                tvName = itemView.findViewById(R.id.tvPostName);
                tvAddress = itemView.findViewById(R.id.tvPostAddress);
                tvDescription = itemView.findViewById(R.id.tvPostDescription);
                tvDistance = itemView.findViewById(R.id.tvPostDistance);
                btnGetDirections = itemView.findViewById(R.id.btnGetDirections);
            }
        }
    }

    // ImageSliderAdapter for ViewPager2
    class ImageSliderAdapter extends RecyclerView.Adapter<ImageSliderAdapter.ImageViewHolder> {
        private List<String> imagesBase64;
        public ImageSliderAdapter(List<String> imagesBase64) { this.imagesBase64 = imagesBase64; }
        @NonNull
        @Override
        public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView imageView = new ImageView(parent.getContext());
            imageView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return new ImageViewHolder(imageView);
        }
        @Override
        public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
            String base64 = imagesBase64.get(position);
            if (base64 != null && !base64.isEmpty()) {
                try {
                    byte[] imageBytes = Base64.decode(base64, Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                    holder.imageView.setImageBitmap(bitmap);
                } catch (Exception e) {
                    holder.imageView.setImageResource(android.R.color.darker_gray);
                }
            } else {
                holder.imageView.setImageResource(android.R.color.darker_gray);
            }
        }
        @Override
        public int getItemCount() { return imagesBase64.size(); }
        class ImageViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            public ImageViewHolder(@NonNull View itemView) {
                super(itemView);
                imageView = (ImageView) itemView;
            }
        }
    }

    private void showImageDialog(Bitmap bitmap) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        ImageView imageView = new ImageView(getContext());
        imageView.setImageBitmap(bitmap);
        builder.setView(imageView);
        builder.setPositiveButton("Close", null);
        builder.show();
    }
} 
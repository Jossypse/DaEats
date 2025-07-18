package com.example.mobappproject;

import android.graphics.Typeface;
import android.os.Bundle;
import androidx.fragment.app.Fragment;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
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
import com.google.android.material.tabs.TabLayout;
import android.widget.LinearLayout;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import androidx.annotation.Nullable;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.ImageButton;
import android.webkit.WebView;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.Toast;
import java.util.Collections;
import com.google.android.material.textfield.TextInputEditText;
import android.text.Editable;
import android.text.TextWatcher;

public class HomeFragment extends Fragment {
    private RecyclerView rvPosts;
    private PostAdapter postAdapter;
    private List<Post> postList = new ArrayList<>();
    private List<Post> filteredPostList = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvEmpty;
    private FusedLocationProviderClient fusedLocationClient;
    private FrameLayout popupOverlayContainer;
    private View popupView; // To keep reference for removal

    // Add the click listener interface here
    public interface OnPostClickListener {
        void onPostClick(Post post);
    }

    private com.google.android.material.button.MaterialButton btnAll, btnRestaurants, btnCafe;
    private com.google.android.material.button.MaterialButton selectedFilterButton;

    private static final String TYPE_BOTH = "Both";
    private static final String TYPE_CAFE = "Cafe";
    private static final String TYPE_RESTAURANT = "Restaurant";

    private View topLikesInclude;
    private ImageView topLikesImage;
    private TextView topLikesName, topLikesAddress, topLikesRating;

    private RecyclerView rvTopLikes;
    private TopLikesAdapter topLikesAdapter;
    private List<Post> topLikedPosts = new ArrayList<>();

    private TextInputEditText searchBar;
    private String currentSearchQuery = "";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        rvPosts = view.findViewById(R.id.rvPosts);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        popupOverlayContainer = view.findViewById(R.id.popupOverlayContainer);
        postAdapter = new PostAdapter(filteredPostList, this::showPostPopup);
        rvPosts.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rvPosts.setAdapter(postAdapter);
        swipeRefreshLayout.setOnRefreshListener(this::loadPosts);
        
        // Top Likes RecyclerView
        rvTopLikes = view.findViewById(R.id.rvTopLikes);
        topLikesAdapter = new TopLikesAdapter(topLikedPosts, this::showPostPopup);
        rvTopLikes.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        rvTopLikes.setAdapter(topLikesAdapter);
        rvTopLikes.setVisibility(View.GONE);
        
        loadPosts();

        // Set greeting and address from SharedPreferences
        TextView greetingText = view.findViewById(R.id.greeting_text);
        TextView locationText = view.findViewById(R.id.location_text);
        android.content.Context context = getContext();
        if (context != null) {
            android.content.SharedPreferences prefs = context.getSharedPreferences("user_session", android.content.Context.MODE_PRIVATE);
            String firstName = prefs.getString("firstName", "");
            String userId = prefs.getString("userId", "");
            String address = prefs.getString("address", "");
            if (!firstName.isEmpty() && !userId.isEmpty()) {
                greetingText.setText("Howdy \uD83D\uDC4B " + firstName );
            }
            if (!address.isEmpty()) {
                locationText.setText(address);
            }
        }

        // Setup filter buttons
        btnAll = view.findViewById(R.id.btnAll);
        btnRestaurants = view.findViewById(R.id.btnRestaurants);
        btnCafe = view.findViewById(R.id.btnCafe);
        selectedFilterButton = btnAll;
        highlightSelectedFilter(btnAll);
        btnAll.setOnClickListener(v -> {
            filterPostsByType(TYPE_BOTH);
            highlightSelectedFilter(btnAll);
            filterPostsBySearch(currentSearchQuery); // Apply search filter after type filter
        });
        btnRestaurants.setOnClickListener(v -> {
            filterPostsByType(TYPE_RESTAURANT);
            highlightSelectedFilter(btnRestaurants);
            filterPostsBySearch(currentSearchQuery);
        });
        btnCafe.setOnClickListener(v -> {
            filterPostsByType(TYPE_CAFE);
            highlightSelectedFilter(btnCafe);
            filterPostsBySearch(currentSearchQuery);
        });

        // Search bar logic
        searchBar = view.findViewById(R.id.search_bar);
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString();
                filterPostsBySearch(currentSearchQuery);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

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
                    sortPostsByProximity(location);
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
                        sortPostsByProximity(location);
                        postAdapter.notifyDataSetChanged();
                    }
                });
            }
        }
    }

    private void loadPosts() {
        swipeRefreshLayout.setRefreshing(true);
        DatabaseReference postsRef = FirebaseDatabase.getInstance().getReference("posts");
        postsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                postList.clear();
                for (DataSnapshot postSnap : snapshot.getChildren()) {
                    Post post = postSnap.getValue(Post.class);
                    if (post != null) {
                        post.id = postSnap.getKey(); // Set the top-level ID
                        postList.add(post);
                    }
                }
                filterPostsByType(TYPE_BOTH); // Show all by default
                filterPostsBySearch(currentSearchQuery); // Apply search filter if any
                swipeRefreshLayout.setRefreshing(false);
                tvEmpty.setVisibility(filteredPostList.isEmpty() ? View.VISIBLE : View.GONE);
                displayTopLikedPosts(); // Show all top liked posts
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void filterPostsByType(String type) {
        filteredPostList.clear();
        for (Post post : postList) {
            if (post.type != null) {
                String postType = post.type.trim();
                if (type.equalsIgnoreCase(TYPE_BOTH)) {
                    filteredPostList.add(post);
                } else if (type.equalsIgnoreCase(postType) || TYPE_BOTH.equalsIgnoreCase(postType)) {
                    filteredPostList.add(post);
                }
            }
        }
        sortPostsByProximity(postAdapter.userLocation);
        postAdapter.notifyDataSetChanged();
        tvEmpty.setVisibility(filteredPostList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void sortPostsByProximity(Location userLocation) {
        if (userLocation == null) return;
        Collections.sort(filteredPostList, (post1, post2) -> {
            double dist1 = getDistanceToUser(post1, userLocation);
            double dist2 = getDistanceToUser(post2, userLocation);
            return Double.compare(dist1, dist2);
        });
    }

    private double getDistanceToUser(Post post, Location userLocation) {
        if (post.latitude != null && post.longitude != null) {
            float[] results = new float[1];
            Location.distanceBetween(
                userLocation.getLatitude(), userLocation.getLongitude(),
                post.latitude, post.longitude, results
            );
            return results[0];
        }
        // If no coordinates, return a large value to push it to the end
        return Double.MAX_VALUE;
    }

    private void highlightSelectedFilter(com.google.android.material.button.MaterialButton selectedButton) {
        if (btnAll != null && btnRestaurants != null && btnCafe != null) {
            btnAll.setStrokeWidth(2);
            btnAll.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
            btnRestaurants.setStrokeWidth(2);
            btnRestaurants.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
            btnCafe.setStrokeWidth(2);
            btnCafe.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
            btnAll.setStrokeColorResource(R.color.white);
            btnRestaurants.setStrokeColorResource(R.color.white);
            btnCafe.setStrokeColorResource(R.color.white);
            btnAll.setBackgroundColor(getResources().getColor(android.R.color.transparent));
            btnRestaurants.setBackgroundColor(getResources().getColor(android.R.color.transparent));
            btnCafe.setBackgroundColor(getResources().getColor(android.R.color.transparent));
        }
        selectedButton.setStrokeWidth(4);
        selectedButton.setStrokeColorResource(R.color.nav_default);
        selectedButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        selectedButton.setBackgroundColor(getResources().getColor(R.color.nav_highlight));
        selectedFilterButton = selectedButton;
    }

    private void displayTopLikedPosts() {
        topLikedPosts.clear();
        for (Post post : postList) {
            if (post.likes != null && post.likes > 0) {
                topLikedPosts.add(post);
            }
        }
        // Sort descending by likes
        Collections.sort(topLikedPosts, (a, b) -> b.likes.compareTo(a.likes));
        if (!topLikedPosts.isEmpty()) {
            rvTopLikes.setVisibility(View.VISIBLE);
            topLikesAdapter.notifyDataSetChanged();
        } else {
            rvTopLikes.setVisibility(View.GONE);
        }
    }

    // Function to show popup overlay with post details
    private void showPostPopup(Post post) {
        if (getContext() == null || popupOverlayContainer == null) return;
        LayoutInflater inflater = LayoutInflater.from(getContext());
        popupView = inflater.inflate(R.layout.viewstablish, popupOverlayContainer, false);
        // Populate popupView with post data
        ViewPager2 imageSlider = popupView.findViewById(R.id.image_slider);
        TabLayout indicatorLayout = popupView.findViewById(R.id.image_slider_indicator);
        TextView placeTitle = popupView.findViewById(R.id.place_title);
        TextView placeDescription = popupView.findViewById(R.id.place_description);
        TextView locationText = popupView.findViewById(R.id.location_text);
        TextView hoursText = popupView.findViewById(R.id.hours_text);
        TextView phoneText = popupView.findViewById(R.id.phone_text);
        TextView travelTimeText = popupView.findViewById(R.id.travel_time);
        TextView likesCountText = popupView.findViewById(R.id.liked_count); // Add this line to get the likes count view
        TextView ratingScoreTextView = popupView.findViewById(R.id.rating_score);
        // Travel mode cycling setup
        final String[] modes = {"car", "motorcycle", "walk", "bike"};
        final double[] speeds = {666.67, 500.0, 80.0, 250.0}; // meters/min: car~40km/h, motorcycle~30km/h, walk~5km/h, bike~15km/h
        final int[] modeIndex = {0};
        // Helper to update travel time display
        Runnable updateTravelTime = () -> {
            if (post.latitude != null && post.longitude != null && postAdapter.userLocation != null) {
                float[] results = new float[1];
                Location.distanceBetween(postAdapter.userLocation.getLatitude(), postAdapter.userLocation.getLongitude(), post.latitude, post.longitude, results);
                float distanceMeters = results[0];
                int minutes = (int) Math.round(distanceMeters / speeds[modeIndex[0]]);
                if (minutes < 1) minutes = 1;
                String distanceStr = String.format("%.1f km", distanceMeters / 1000);
                String modeLabel = modes[modeIndex[0]];
                travelTimeText.setText(distanceStr + " • " + minutes + " min by " + modeLabel);
            } else {
                travelTimeText.setText("N/A");
            }
        };
        updateTravelTime.run();
        travelTimeText.setOnClickListener(v -> {
            modeIndex[0] = (modeIndex[0] + 1) % modes.length;
            updateTravelTime.run();
        });
        // Set up image slider and indicators
        if (post.imagesBase64 != null && !post.imagesBase64.isEmpty()) {
            ImageSliderAdapter sliderAdapter = new ImageSliderAdapter(post.imagesBase64);
            imageSlider.setAdapter(sliderAdapter);
            imageSlider.setVisibility(View.VISIBLE);
            indicatorLayout.setVisibility(post.imagesBase64.size() > 1 ? View.VISIBLE : View.GONE);
            // Create indicators using TabLayout
            indicatorLayout.removeAllTabs();
            int count = post.imagesBase64.size();
            for (int i = 0; i < count; i++) {
                indicatorLayout.addTab(indicatorLayout.newTab());
            }
            // Listen for page changes to update indicators
            imageSlider.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    indicatorLayout.selectTab(indicatorLayout.getTabAt(position));
                }
            });
            // Optional: allow clicking tabs to change page
            indicatorLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    imageSlider.setCurrentItem(tab.getPosition());
                }
                @Override public void onTabUnselected(TabLayout.Tab tab) {}
                @Override public void onTabReselected(TabLayout.Tab tab) {}
            });
        } else {
            imageSlider.setVisibility(View.GONE);
            indicatorLayout.setVisibility(View.GONE);
        }
        placeTitle.setText(post.name);
        placeDescription.setText(post.description);
        locationText.setText(post.address);
        hoursText.setText("Hours: " + (post.openTime != null ? post.openTime : "?") + " - " + (post.closeTime != null ? post.closeTime : "?"));
        phoneText.setText("Phone: " + (post.contactNumber != null ? post.contactNumber : "?"));
        // Calculate travel time if possible
        if (post.latitude != null && post.longitude != null && postAdapter.userLocation != null) {
            float[] results = new float[1];
            Location.distanceBetween(postAdapter.userLocation.getLatitude(), postAdapter.userLocation.getLongitude(), post.latitude, post.longitude, results);
            float distanceMeters = results[0];
            // Assume average driving speed 40km/h (666.67 m/min)
            int minutes = (int) Math.round(distanceMeters / 666.67);
            if (minutes < 1) minutes = 1;
            travelTimeText.setText(minutes + " min by car");
        } else {
            travelTimeText.setText("N/A");
        }
        // Add close on background tap
        popupOverlayContainer.setOnClickListener(v -> hidePopup());
        // Prevent click-through on popupView
        popupView.setOnClickListener(v -> {});


        Button closeBtn = popupView.findViewById(R.id.close_btn);
        closeBtn.setOnClickListener(v -> hidePopup());
        // Show popup
        popupOverlayContainer.removeAllViews();
        popupOverlayContainer.addView(popupView);
        popupOverlayContainer.setVisibility(View.VISIBLE);

        ImageButton btnLike = popupView.findViewById(R.id.btn_like);
        // Like button logic: check if liked, update UI, and save/remove like in Firebase
        SharedPreferences prefs = getContext().getSharedPreferences("user_session", Context.MODE_PRIVATE);
        String userId = prefs.getString("userId", null);
        DatabaseReference likeRef = FirebaseDatabase.getInstance().getReference("likes");
        DatabaseReference postRef = FirebaseDatabase.getInstance().getReference("posts").child(post.id);
        if (userId != null) {
            DatabaseReference userLikeRef = likeRef.child(userId).child(post.id);
            userLikeRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    boolean liked = snapshot.exists();
                    btnLike.setColorFilter(getResources().getColor(liked ? android.R.color.holo_red_dark : android.R.color.darker_gray));
                    btnLike.setTag(liked);
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });
            btnLike.setOnClickListener(v -> {
                Object tag = btnLike.getTag();
                boolean liked = tag instanceof Boolean && (Boolean) tag;
                if (liked) {
                    userLikeRef.removeValue();
                    btnLike.setColorFilter(getResources().getColor(android.R.color.darker_gray));
                    btnLike.setTag(false);
                    // Decrement likes in post
                    postRef.child("likes").runTransaction(new com.google.firebase.database.Transaction.Handler() {
                        @Override
                        public com.google.firebase.database.Transaction.Result doTransaction(@NonNull com.google.firebase.database.MutableData currentData) {
                            Integer currentLikes = currentData.getValue(Integer.class);
                            if (currentLikes == null || currentLikes <= 0) {
                                currentData.setValue(0);
                            } else {
                                currentData.setValue(currentLikes - 1);
                            }
                            return com.google.firebase.database.Transaction.success(currentData);
                        }
                        @Override
                        public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                            Integer likes = currentData.getValue(Integer.class);
                            if (likesCountText != null) {
                                likesCountText.setText(likes != null ? String.valueOf(likes) : "0");
                            }
                        }
                    });
                } else {
                    userLikeRef.setValue(true);
                    btnLike.setColorFilter(getResources().getColor(android.R.color.holo_red_dark));
                    btnLike.setTag(true);
                    // Increment likes in post
                    postRef.child("likes").runTransaction(new com.google.firebase.database.Transaction.Handler() {
                        @Override
                        public com.google.firebase.database.Transaction.Result doTransaction(@NonNull com.google.firebase.database.MutableData currentData) {
                            Integer currentLikes = currentData.getValue(Integer.class);
                            if (currentLikes == null) {
                                currentData.setValue(1);
                            } else {
                                currentData.setValue(currentLikes + 1);
                            }
                            return com.google.firebase.database.Transaction.success(currentData);
                        }
                        @Override
                        public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                            Integer likes = currentData.getValue(Integer.class);
                            if (likesCountText != null) {
                                likesCountText.setText(likes != null ? String.valueOf(likes) : "0");
                            }
                        }
                    });
                }
            });
        } else {
            btnLike.setOnClickListener(v -> Toast.makeText(getContext(), "Please log in to like posts", Toast.LENGTH_SHORT).show());
        }

        // Set initial likes count
        postRef.child("likes").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Integer likes = snapshot.getValue(Integer.class);
                if (likesCountText != null) {
                    likesCountText.setText(likes != null ? String.valueOf(likes) : "0");
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        // Wire up Get Directions button in the popup
        Button getDirectionsBtn = popupView.findViewById(R.id.get_directions_btn);
        if (post.latitude != null && post.longitude != null) {
            getDirectionsBtn.setEnabled(true);
            getDirectionsBtn.setOnClickListener(v -> showDirectionsPopup(post.latitude, post.longitude));
        } else {
            getDirectionsBtn.setEnabled(false);
        }


        Button show = popupView.findViewById(R.id.btn_write_review);
        show.setOnClickListener(v -> showAddReviewDialog(post));

        // --- Add this block to display reviews ---
        RecyclerView reviewsRecycler = popupView.findViewById(R.id.reviews_recycler);
        reviewsRecycler.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        List<Review> reviewList = new ArrayList<>();
        List<String> reviewerIds = new ArrayList<>();
        ReviewAdapter reviewAdapter = new ReviewAdapter(reviewList, reviewerIds);
        reviewsRecycler.setAdapter(reviewAdapter);
        // Load reviews from Firebase for this post
        DatabaseReference reviewsRef = FirebaseDatabase.getInstance().getReference("reviews").child(post.id);
        reviewsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                reviewList.clear();
                reviewerIds.clear();
                int totalStars = 0;
                int reviewCount = 0;
                for (DataSnapshot child : snapshot.getChildren()) {
                    Review review = child.getValue(Review.class);
                    if (review != null) {
                        reviewList.add(review);
                        reviewerIds.add(child.getKey()); // userId is the key
                        // Only count reviews for this post.id (should always be true in this structure)
                        totalStars += review.star;
                        reviewCount++;
                    }
                }
                // Calculate and display average rating if there are reviews
                if (reviewCount > 0) {
                    double average = (double) totalStars / reviewCount;
                    ratingScoreTextView.setText(String.format("%.1f", average));
                    // Upload the final rating to the post in Firebase
                    DatabaseReference postRatingRef = FirebaseDatabase.getInstance().getReference("posts").child(post.id).child("rating");
                    postRatingRef.setValue(average);
                } else {
                    ratingScoreTextView.setText("0.0");
                    // If no reviews, set rating to 0.0 in Firebase
                    DatabaseReference postRatingRef = FirebaseDatabase.getInstance().getReference("posts").child(post.id).child("rating");
                    postRatingRef.setValue(0.0);
                }
                reviewAdapter.notifyDataSetChanged();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Optionally handle error
            }
        });
        // --- End reviews block ---
    }

    private void showDirectionsPopup(double destLat, double destLng) {
        if (getContext() == null || popupOverlayContainer == null) return;
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View directionsView = inflater.inflate(R.layout.popup_directions, popupOverlayContainer, false);
        WebView webView = directionsView.findViewById(R.id.webview_directions);
        Button closeBtn = directionsView.findViewById(R.id.btn_close_directions);
        webView.getSettings().setJavaScriptEnabled(true);
        // Get user's current location if available
        double userLat = 14.0; // fallback
        double userLng = 122.0;
        if (postAdapter.userLocation != null) {
            userLat = postAdapter.userLocation.getLatitude();
            userLng = postAdapter.userLocation.getLongitude();
        }
        String url = "https://www.google.com/maps/dir/?api=1&origin=" + userLat + "," + userLng +
                "&destination=" + destLat + "," + destLng + "&travelmode=driving";
        webView.loadUrl(url);
        closeBtn.setOnClickListener(v -> hidePopup());
        // Show popup
        popupOverlayContainer.removeAllViews();
        popupOverlayContainer.addView(directionsView);
        popupOverlayContainer.setVisibility(View.VISIBLE);
    }

    private void showAddReviewDialog(Post post) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_review, null);
        RatingBar ratingBar = dialogView.findViewById(R.id.rating_bar);
        EditText editComment = dialogView.findViewById(R.id.edit_comment);

        // Custom title
        TextView title = new TextView(getContext());
        title.setText("Write a Review");
        title.setPadding(32, 32, 32, 16);
        title.setGravity(Gravity.CENTER);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(getContext(), android.R.color.black));
        new AlertDialog.Builder(getContext())
            .setCustomTitle(title)
            .setView(dialogView)
            .setPositiveButton("Submit", (dialog, which) -> {
                int star = (int) ratingBar.getRating();
                String comment = editComment.getText().toString().trim();
                if (star > 0 && !comment.isEmpty()) {
                    saveReviewToFirebase(post, star, comment);
                } else {
                    Toast.makeText(getContext(), "Please provide a rating and Review", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void saveReviewToFirebase(Post post, int star, String comment) {
        // Get user ID from SharedPreferences
        SharedPreferences prefs = getContext().getSharedPreferences("user_session", Context.MODE_PRIVATE);
        String userId = prefs.getString("userId", null);
        if (userId == null) {
            Toast.makeText(getContext(), "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }
        // Use post.id as unique identifier for the post
        String postId = post.id;
        if (postId == null || postId.isEmpty()) {
            Toast.makeText(getContext(), "Post ID missing", Toast.LENGTH_SHORT).show();
            return;
        }
        com.example.mobappproject.Review review = new com.example.mobappproject.Review(comment, star);
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("reviews")
            .child(postId)
            .child(userId);
        ref.setValue(review)
            .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Review submitted!", Toast.LENGTH_SHORT).show())
            .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to submit review", Toast.LENGTH_SHORT).show());
    }

    // Helper to get postId (assumes Post has a unique name or add an id field to Post)
    private String getPostIdForPost(Post post) {
        // If Post has an id field, use it. Otherwise, fallback to name (not recommended for real apps)
        // return post.id;
        return post.name != null ? post.name.replaceAll("\\s+", "_").toLowerCase() : "unknown_post";
    }

    private void hidePopup() {
        if (popupOverlayContainer != null) {
            popupOverlayContainer.setVisibility(View.GONE);
            popupOverlayContainer.removeAllViews();
        }
    }

    // This method should be called after filterPostsByType or when search changes
    private void filterPostsBySearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            postAdapter.posts = new ArrayList<>(filteredPostList);
            postAdapter.notifyDataSetChanged();
            tvEmpty.setVisibility(filteredPostList.isEmpty() ? View.VISIBLE : View.GONE);
            return;
        }
        String lowerQuery = query.toLowerCase();
        List<Post> searchFiltered = new ArrayList<>();
        Double queryNumber = null;
        try {
            queryNumber = Double.parseDouble(query.trim());
        } catch (NumberFormatException ignored) {}
        for (Post post : filteredPostList) {
            boolean matchesName = post.name != null && post.name.toLowerCase().contains(lowerQuery);
            boolean matchesAddress = post.address != null && post.address.toLowerCase().contains(lowerQuery);
            boolean matchesDistance = false;
            if (post.latitude != null && post.longitude != null && postAdapter.userLocation != null) {
                float[] results = new float[1];
                Location.distanceBetween(postAdapter.userLocation.getLatitude(), postAdapter.userLocation.getLongitude(), post.latitude, post.longitude, results);
                float distanceKm = results[0] / 1000f;
                String distanceStr = String.format("%.1f", distanceKm);
                // If query is a number, match within ±3km
                if (queryNumber != null) {
                    if (Math.abs(distanceKm - queryNumber) <= 3.0) {
                        matchesDistance = true;
                    }
                } else {
                    // Otherwise, match if the distance string contains the query
                    matchesDistance = distanceStr.contains(lowerQuery);
                }
            }
            if (matchesName || matchesAddress || matchesDistance) {
                searchFiltered.add(post);
            }
        }
        // Update adapter with filtered list
        postAdapter.posts = searchFiltered;
        postAdapter.notifyDataSetChanged();
        tvEmpty.setVisibility(searchFiltered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // Post model
    public static class Post {
        public String id;
        public String name, address, description;
        public List<String> imagesBase64;
        public Double latitude, longitude;
        public String contactNumber, openTime, closeTime;
        public String type; // Add this field for filtering
        public Integer likes; // Add this field for likes
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

            } else {
                holder.tvDistance.setText("");

            }

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

            public PostViewHolder(@NonNull View itemView) {
                super(itemView);
                ivPostImage = itemView.findViewById(R.id.ivPostImage);
                tvName = itemView.findViewById(R.id.tvPostName);
                tvAddress = itemView.findViewById(R.id.tvPostAddress);
                tvDistance = itemView.findViewById(R.id.tvPostDistance);

            }
        }
    }

    // ImageSliderAdapter for ViewPager2
    static class ImageSliderAdapter extends RecyclerView.Adapter<ImageSliderAdapter.ImageViewHolder> {
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

    // Adapter for Top Liked Posts
    public class TopLikesAdapter extends RecyclerView.Adapter<TopLikesAdapter.TopLikesViewHolder> {
        private List<Post> posts;
        private OnPostClickListener onPostClickListener;
        public TopLikesAdapter(List<Post> posts, OnPostClickListener listener) {
            this.posts = posts;
            this.onPostClickListener = listener;
        }
        @NonNull
        @Override
        public TopLikesViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.toplikes, parent, false);
            return new TopLikesViewHolder(v);
        }
        @Override
        public void onBindViewHolder(@NonNull TopLikesViewHolder holder, int position) {
            Post post = posts.get(position);
            holder.name.setText(post.name);
            holder.address.setText(post.address);
            holder.rating.setText("\u2764 " + post.likes);
            // Set image
            if (post.imagesBase64 != null && !post.imagesBase64.isEmpty()) {
                String base64 = post.imagesBase64.get(0);
                if (base64 != null && !base64.isEmpty()) {
                    try {
                        byte[] imageBytes = Base64.decode(base64, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                        holder.image.setImageBitmap(bitmap);
                    } catch (Exception e) {
                        holder.image.setImageResource(android.R.color.darker_gray);
                    }
                } else {
                    holder.image.setImageResource(android.R.color.darker_gray);
                }
            } else {
                holder.image.setImageResource(android.R.color.darker_gray);
            }
            holder.itemView.setOnClickListener(v -> {
                if (onPostClickListener != null) onPostClickListener.onPostClick(post);
            });
        }
        @Override
        public int getItemCount() { return posts.size(); }
        class TopLikesViewHolder extends RecyclerView.ViewHolder {
            ImageView image;
            TextView name, address, rating;
            public TopLikesViewHolder(@NonNull View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.image);
                name = itemView.findViewById(R.id.name);
                address = itemView.findViewById(R.id.address);
                rating = itemView.findViewById(R.id.cafe_rating);
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
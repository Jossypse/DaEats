package com.example.mobappproject;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import android.widget.ImageView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.RatingBar;
import android.app.AlertDialog;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import android.view.Gravity;
import android.graphics.Typeface;
import android.widget.Toast;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.widget.FrameLayout;

public class LikedFragment extends Fragment {
    private RecyclerView rvLikedPosts;
    private LikedPostsAdapter likedPostsAdapter;
    private List<Post> allLikedPosts = new ArrayList<>();
    private List<Post> filteredLikedPosts = new ArrayList<>();
    private Button btnCafe, btnRestaurant;
    private TextView tvEmptyLiked;
    private String selectedType = "Cafe";
    private String userId;

    private FrameLayout popupOverlayContainer;
    private View popupView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_liked, container, false);

        btnCafe = view.findViewById(R.id.btnCafe);
        btnRestaurant = view.findViewById(R.id.btnRestaurant);
        rvLikedPosts = view.findViewById(R.id.rvLikedPosts);
        tvEmptyLiked = view.findViewById(R.id.tvEmptyLiked);

        likedPostsAdapter = new LikedPostsAdapter(filteredLikedPosts);
        rvLikedPosts.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        rvLikedPosts.setAdapter(likedPostsAdapter);

        // Get userId from SharedPreferences
        SharedPreferences prefs = requireContext().getSharedPreferences("user_session", Context.MODE_PRIVATE);
        userId = prefs.getString("userId", null);

        // Default: Cafe selected
        setFilterSelected(btnCafe, btnRestaurant);
        btnCafe.setOnClickListener(v -> {
            setFilterSelected(btnCafe, btnRestaurant);
            selectedType = "Cafe";
            filterAndDisplay();
        });
        btnRestaurant.setOnClickListener(v -> {
            setFilterSelected(btnRestaurant, btnCafe);
            selectedType = "Restaurant";
            filterAndDisplay();
        });

        loadLikedPosts();

        popupOverlayContainer = new FrameLayout(requireContext());
        popupOverlayContainer.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ((ViewGroup) view).addView(popupOverlayContainer);
        popupOverlayContainer.setVisibility(View.GONE);

        likedPostsAdapter.setOnPostClickListener(this::showPostPopup);

        return view;
    }

    private void setFilterSelected(Button selected, Button unselected) {
        selected.setSelected(true);
        selected.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        selected.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.nav_highlight));
        unselected.setSelected(false);
        unselected.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
        unselected.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.white));
    }

    private void loadLikedPosts() {
        if (userId == null) return;
        DatabaseReference likesRef = FirebaseDatabase.getInstance().getReference("likes").child(userId);
        likesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Set<String> likedPostIds = new HashSet<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    likedPostIds.add(child.getKey());
                }
                fetchLikedPostsDetails(likedPostIds);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void fetchLikedPostsDetails(Set<String> likedPostIds) {
        allLikedPosts.clear();
        if (likedPostIds.isEmpty()) {
            filterAndDisplay();
            return;
        }
        DatabaseReference postsRef = FirebaseDatabase.getInstance().getReference("posts");
        postsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot postSnap : snapshot.getChildren()) {
                    if (likedPostIds.contains(postSnap.getKey())) {
                        Post post = postSnap.getValue(Post.class);
                        if (post != null) {
                            post.id = postSnap.getKey();
                            allLikedPosts.add(post);
                        }
                    }
                }
                filterAndDisplay();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void filterAndDisplay() {
        filteredLikedPosts.clear();
        for (Post post : allLikedPosts) {
            if (post.type != null && post.type.equalsIgnoreCase(selectedType)) {
                filteredLikedPosts.add(post);
            }
        }
        likedPostsAdapter.notifyDataSetChanged();
        tvEmptyLiked.setVisibility(filteredLikedPosts.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // Post model (copy from HomeFragment)
    public static class Post {
        public String id;
        public String name, address, description;
        public List<String> imagesBase64;
        public Double latitude, longitude;
        public String contactNumber, openTime, closeTime;
        public String type;
        public Integer likes;
        public Post() {}
    }

    // Adapter for liked posts using toplikes.xml
    public class LikedPostsAdapter extends RecyclerView.Adapter<LikedPostsAdapter.LikedPostViewHolder> {
        private List<Post> posts;
        private OnPostClickListener onPostClickListener;

        public LikedPostsAdapter(List<Post> posts) { this.posts = posts; }

        public void setOnPostClickListener(OnPostClickListener listener) {
            this.onPostClickListener = listener;
        }

        @NonNull
        @Override
        public LikedPostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.toplikes, parent, false);
            return new LikedPostViewHolder(v);
        }
        @Override
        public void onBindViewHolder(@NonNull LikedPostViewHolder holder, int position) {
            Post post = posts.get(position);
            holder.name.setText(post.name);
            holder.address.setText(post.address);
            holder.rating.setText("\u2764 " + (post.likes != null ? post.likes : 0));
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
        class LikedPostViewHolder extends RecyclerView.ViewHolder {
            ImageView image;
            TextView name, address, rating;
            public LikedPostViewHolder(@NonNull View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.image);
                name = itemView.findViewById(R.id.name);
                address = itemView.findViewById(R.id.address);
                rating = itemView.findViewById(R.id.cafe_rating);
            }
        }
    }

    // Add interface for click listener
    public interface OnPostClickListener {
        void onPostClick(Post post);
    }

    // Add showPostPopup method
    private void showPostPopup(Post post) {
        if (getContext() == null || popupOverlayContainer == null) return;
        LayoutInflater inflater = LayoutInflater.from(getContext());
        popupView = inflater.inflate(R.layout.viewstablish, popupOverlayContainer, false);
        // Populate popupView with post data (similar to HomeFragment)
        ViewPager2 imageSlider = popupView.findViewById(R.id.image_slider);
        TabLayout indicatorLayout = popupView.findViewById(R.id.image_slider_indicator);
        TextView placeTitle = popupView.findViewById(R.id.place_title);
        TextView placeDescription = popupView.findViewById(R.id.place_description);
        TextView locationText = popupView.findViewById(R.id.location_text);
        TextView hoursText = popupView.findViewById(R.id.hours_text);
        TextView phoneText = popupView.findViewById(R.id.phone_text);
        TextView travelTimeText = popupView.findViewById(R.id.travel_time);
        TextView likesCountText = popupView.findViewById(R.id.liked_count);
        TextView ratingScoreTextView = popupView.findViewById(R.id.rating_score);
        // Images
        if (post.imagesBase64 != null && !post.imagesBase64.isEmpty()) {
            HomeFragment.ImageSliderAdapter sliderAdapter = new HomeFragment.ImageSliderAdapter(post.imagesBase64);
            imageSlider.setAdapter(sliderAdapter);
            imageSlider.setVisibility(View.VISIBLE);
            indicatorLayout.setVisibility(post.imagesBase64.size() > 1 ? View.VISIBLE : View.GONE);
            indicatorLayout.removeAllTabs();
            int count = post.imagesBase64.size();
            for (int i = 0; i < count; i++) {
                indicatorLayout.addTab(indicatorLayout.newTab());
            }
            imageSlider.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    indicatorLayout.selectTab(indicatorLayout.getTabAt(position));
                }
            });
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
        travelTimeText.setText("N/A"); // No user location in LikedFragment
        // Likes count
        DatabaseReference postRef = FirebaseDatabase.getInstance().getReference("posts").child(post.id);
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
        // Like/unlike button
        ImageButton btnLike = popupView.findViewById(R.id.btn_like);
        SharedPreferences prefs = getContext().getSharedPreferences("user_session", Context.MODE_PRIVATE);
        String userId = prefs.getString("userId", null);
        DatabaseReference likeRef = FirebaseDatabase.getInstance().getReference("likes");
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
                            // Remove from UI
                            loadLikedPosts();
                            hidePopup();
                        }
                    });
                } else {
                    userLikeRef.setValue(true);
                    btnLike.setColorFilter(getResources().getColor(android.R.color.holo_red_dark));
                    btnLike.setTag(true);
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
        // --- Customer Reviews block copied from HomeFragment ---
        RecyclerView reviewsRecycler = popupView.findViewById(R.id.reviews_recycler);
        reviewsRecycler.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        List<Review> reviewList = new ArrayList<>();
        List<String> reviewerIds = new ArrayList<>();
        ReviewAdapter reviewAdapter = new ReviewAdapter(reviewList, reviewerIds);
        reviewsRecycler.setAdapter(reviewAdapter);
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
                        totalStars += review.star;
                        reviewCount++;
                    }
                }
                // Calculate and display average rating if there are reviews
                if (reviewCount > 0) {
                    double average = (double) totalStars / reviewCount;
                    if (ratingScoreTextView != null) ratingScoreTextView.setText(String.format("%.1f", average));
                    // Upload the final rating to the post in Firebase
                    DatabaseReference postRatingRef = FirebaseDatabase.getInstance().getReference("posts").child(post.id).child("rating");
                    postRatingRef.setValue(average);
                } else {
                    if (ratingScoreTextView != null) ratingScoreTextView.setText("0.0");
                    DatabaseReference postRatingRef = FirebaseDatabase.getInstance().getReference("posts").child(post.id).child("rating");
                    postRatingRef.setValue(0.0);
                }
                reviewAdapter.notifyDataSetChanged();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
        // Add review button
        Button show = popupView.findViewById(R.id.btn_write_review);
        show.setOnClickListener(v -> showAddReviewDialog(post));
        // Close button
        Button closeBtn = popupView.findViewById(R.id.close_btn);
        closeBtn.setOnClickListener(v -> hidePopup());
        // Show popup
        popupOverlayContainer.removeAllViews();
        popupOverlayContainer.addView(popupView);
        popupOverlayContainer.setVisibility(View.VISIBLE);
        // Dismiss on background tap
        popupOverlayContainer.setOnClickListener(v -> hidePopup());
        popupView.setOnClickListener(v -> {});
    }

    private void hidePopup() {
        if (popupOverlayContainer != null) {
            popupOverlayContainer.setVisibility(View.GONE);
            popupOverlayContainer.removeAllViews();
        }
    }

    // Add showAddReviewDialog and ReviewAdapter as in HomeFragment
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
        SharedPreferences prefs = getContext().getSharedPreferences("user_session", Context.MODE_PRIVATE);
        String userId = prefs.getString("userId", null);
        if (userId == null) {
            Toast.makeText(getContext(), "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }
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

    // Adapter for reviews using add_review_dialog.xml
    public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder> {
        private List<Review> reviews;
        private List<String> reviewerIds;

        public ReviewAdapter(List<Review> reviews, List<String> reviewerIds) {
            this.reviews = reviews;
            this.reviewerIds = reviewerIds;
        }

        @NonNull
        @Override
        public ReviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_review, parent, false);
            return new ReviewViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ReviewViewHolder holder, int position) {
            Review review = reviews.get(position);
            holder.commentText.setText(review.comment);
            holder.starText.setText(review.star + "⭐    ");
            String userId = reviewerIds.get(position);
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("User").child(userId);
            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String firstName = snapshot.child("firstName").getValue(String.class);
                    String lastName = snapshot.child("lastName").getValue(String.class);
                    if (firstName != null && lastName != null) {
                        holder.reviewerName.setText(firstName + " " + lastName);
                    } else {
                        holder.reviewerName.setText("Unknown");
                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    holder.reviewerName.setText("Unknown");
                }
            });
        }

        @Override
        public int getItemCount() { return reviews.size(); }

        class ReviewViewHolder extends RecyclerView.ViewHolder {
            TextView reviewerName, starText, commentText;
            public ReviewViewHolder(@NonNull View itemView) {
                super(itemView);
                reviewerName = itemView.findViewById(R.id.reviewer_name);
                starText = itemView.findViewById(R.id.star_text);
                commentText = itemView.findViewById(R.id.comment_text);
            }
        }
    }
} 
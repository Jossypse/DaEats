package com.example.mobappproject;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Button;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;
import androidx.annotation.NonNull;
import android.widget.ImageView;
import android.widget.ImageButton;

public class ProfileFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        Context context = getContext();
        SharedPreferences prefs = context.getSharedPreferences("user_session", Context.MODE_PRIVATE);
        String userId = prefs.getString("userId", "");
        String firstName = prefs.getString("firstName", "");
        String lastName = prefs.getString("lastName", "");
        String address = prefs.getString("address", "");

        TextView tvName = view.findViewById(R.id.tvName);
        TextView tvAddress = view.findViewById(R.id.tvAddress);
        tvName.setText(firstName + " " + lastName);
        tvAddress.setText(address);

        RecyclerView rvUserImages = view.findViewById(R.id.rvUserImages);
        rvUserImages.setLayoutManager(new GridLayoutManager(context, 1));
        List<HomeFragment.Post> userPosts = new ArrayList<>();
        UserPostsAdapter[] adapter = new UserPostsAdapter[1];
        adapter[0] = new UserPostsAdapter(userPosts, post -> confirmDeletePost(post, userId, adapter[0]));
        rvUserImages.setAdapter(adapter[0]);

        // Fetch posts from Firebase and filter by userId
        DatabaseReference postsRef = FirebaseDatabase.getInstance().getReference("posts");
        postsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                userPosts.clear();
                for (DataSnapshot postSnap : snapshot.getChildren()) {
                    String postUserId = postSnap.child("userId").getValue(String.class);
                    if (userId.equals(postUserId)) {
                        HomeFragment.Post post = postSnap.getValue(HomeFragment.Post.class);
                        if (post != null) {
                            post.id = postSnap.getKey();
                            userPosts.add(post);
                        }
                    }
                }
                adapter[0].notifyDataSetChanged();
            }
            @Override
            public void onCancelled(DatabaseError error) {}
        });

        Button logoutBtn = view.findViewById(R.id.btnLogout);
        logoutBtn.setOnClickListener(v -> {
            prefs.edit().clear().apply();
            Intent intent = new Intent(context, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        return view;
    }

    // Adapter for user posts
    public static class UserPostsAdapter extends RecyclerView.Adapter<UserPostsAdapter.PostViewHolder> {
        private List<HomeFragment.Post> posts;
        private OnDeleteClickListener onDeleteClickListener;
        public interface OnDeleteClickListener {
            void onDeleteClick(HomeFragment.Post post);
        }
        public UserPostsAdapter(List<HomeFragment.Post> posts, OnDeleteClickListener listener) {
            this.posts = posts;
            this.onDeleteClickListener = listener;
        }
        @NonNull
        @Override
        public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_post, parent, false);
            return new PostViewHolder(view);
        }
        @Override
        public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
            HomeFragment.Post post = posts.get(position);
            holder.tvName.setText(post.name);
            holder.tvAddress.setText(post.address);
            holder.tvDescription.setText(post.description);
            holder.tvType.setText(post.type);
            // Set the first image only
            if (post.imagesBase64 != null && !post.imagesBase64.isEmpty()) {
                String base64 = post.imagesBase64.get(0);
                if (base64 != null && !base64.isEmpty()) {
                    try {
                        byte[] imageBytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
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
            holder.btnDelete.setOnClickListener(v -> {
                if (onDeleteClickListener != null) onDeleteClickListener.onDeleteClick(post);
            });
        }
        @Override
        public int getItemCount() { return posts.size(); }
        static class PostViewHolder extends RecyclerView.ViewHolder {
            ImageView ivPostImage;
            TextView tvName, tvAddress, tvDescription, tvType;
            ImageButton btnDelete;
            public PostViewHolder(@NonNull View itemView) {
                super(itemView);
                ivPostImage = itemView.findViewById(R.id.ivUserPostImage);
                tvName = itemView.findViewById(R.id.tvUserPostName);
                tvAddress = itemView.findViewById(R.id.tvUserPostAddress);
                tvDescription = itemView.findViewById(R.id.tvUserPostDescription);
                tvType = itemView.findViewById(R.id.tvUserPostType);
                btnDelete = itemView.findViewById(R.id.btnDeleteUserPost);
            }
        }
    }

    // Confirm and delete post
    private void confirmDeletePost(HomeFragment.Post post, String userId, UserPostsAdapter adapter) {
        new android.app.AlertDialog.Builder(getContext())
            .setTitle("Delete Post")
            .setMessage("Are you sure you want to delete this post?")
            .setPositiveButton("Delete", (dialog, which) -> deletePostFromFirebase(post, userId, adapter))
            .setNegativeButton("Cancel", null)
            .show();
    }

    // Delete post from Firebase and all traces
    private void deletePostFromFirebase(HomeFragment.Post post, String userId, UserPostsAdapter adapter) {
        DatabaseReference postsRef = FirebaseDatabase.getInstance().getReference("posts");
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("User").child(userId);
        DatabaseReference likesRef = FirebaseDatabase.getInstance().getReference("likes");
        // Remove post from posts
        postsRef.child(post.id).removeValue();
        // Remove post from user's posts list
        userRef.child("posts").orderByValue().equalTo(post.id).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot snap : snapshot.getChildren()) {
                    snap.getRef().removeValue();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
        // Remove all likes for this post
        likesRef.orderByKey().startAt(userId + "--").endAt(userId + "--\uf8ff").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot snap : snapshot.getChildren()) {
                    if (snap.getKey() != null && snap.getKey().endsWith("--" + post.id)) {
                        snap.getRef().removeValue();
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
        // Remove from adapter
        adapter.posts.remove(post);
        adapter.notifyDataSetChanged();
    }
} 
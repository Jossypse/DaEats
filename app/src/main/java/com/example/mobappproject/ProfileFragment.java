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
        rvUserImages.setLayoutManager(new GridLayoutManager(context, 3));
        UserImagesAdapter adapter = new UserImagesAdapter();
        rvUserImages.setAdapter(adapter);

        // Fetch posts from Firebase and filter by userId
        DatabaseReference postsRef = FirebaseDatabase.getInstance().getReference("posts");
        postsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<String> userImages = new ArrayList<>();
                for (DataSnapshot postSnap : snapshot.getChildren()) {
                    String postUserId = postSnap.child("userId").getValue(String.class);
                    if (userId.equals(postUserId)) {
                        List<String> images = new ArrayList<>();
                        for (DataSnapshot imgSnap : postSnap.child("imagesBase64").getChildren()) {
                            String img = imgSnap.getValue(String.class);
                            if (img != null && !img.isEmpty()) {
                                images.add(img);
                            }
                        }
                        userImages.addAll(images);
                    }
                }
                adapter.setImages(userImages);
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

    // Adapter for user images
    public static class UserImagesAdapter extends RecyclerView.Adapter<UserImagesAdapter.ImageViewHolder> {
        private List<String> images = new ArrayList<>();
        public void setImages(List<String> images) {
            this.images = images;
            notifyDataSetChanged();
        }
        @NonNull
        @Override
        public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image_preview, parent, false);
            return new ImageViewHolder(view);
        }
        @Override
        public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
            String base64 = images.get(position);
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
        public int getItemCount() {
            return images.size();
        }
        static class ImageViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            public ImageViewHolder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.ivPreviewImage);
            }
        }
    }
} 
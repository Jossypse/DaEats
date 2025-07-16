package com.example.mobappproject;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder> {
    private List<Review> reviews;
    private List<String> reviewerIds; // Add this to store userIds for each review

    public ReviewAdapter(List<Review> reviews, List<String> reviewerIds) {
        this.reviews = reviews;
        this.reviewerIds = reviewerIds;
    }

    @NonNull
    @Override
    public ReviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_review, parent, false);
        return new ReviewViewHolder(view);
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
    public int getItemCount() {
        return reviews.size();
    }

    static class ReviewViewHolder extends RecyclerView.ViewHolder {
        TextView commentText, starText, reviewerName;
        ReviewViewHolder(View itemView) {
            super(itemView);
            commentText = itemView.findViewById(R.id.comment_text);
            starText = itemView.findViewById(R.id.star_text);
            reviewerName = itemView.findViewById(R.id.reviewer_name);
        }
    }
} 
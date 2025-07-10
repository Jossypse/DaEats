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

public class HomeFragment extends Fragment {
    private RecyclerView rvPosts;
    private PostAdapter postAdapter;
    private List<Post> postList = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvEmpty;
    private FusedLocationProviderClient fusedLocationClient;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        rvPosts = view.findViewById(R.id.rvPosts);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        postAdapter = new PostAdapter(postList);
        rvPosts.setLayoutManager(new LinearLayoutManager(getContext()));
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
        public PostAdapter(List<Post> posts) { this.posts = posts; }
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
            if (post.imagesBase64 != null && !post.imagesBase64.isEmpty()) {
                ImageSliderAdapter sliderAdapter = new ImageSliderAdapter(post.imagesBase64);
                holder.vpImages.setAdapter(sliderAdapter);
                setupPageIndicator(holder.llPageIndicator, post.imagesBase64.size(), holder.vpImages);
            } else {
                holder.vpImages.setAdapter(new ImageSliderAdapter(new ArrayList<>()));
                holder.llPageIndicator.removeAllViews();
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
        }
        @Override
        public int getItemCount() { return posts.size(); }
        class PostViewHolder extends RecyclerView.ViewHolder {
            ViewPager2 vpImages;
            LinearLayout llPageIndicator;
            TextView tvName, tvAddress, tvDescription;
            TextView tvDistance;
            View btnGetDirections;
            public PostViewHolder(@NonNull View itemView) {
                super(itemView);
                vpImages = itemView.findViewById(R.id.vpPostImages);
                llPageIndicator = itemView.findViewById(R.id.llPageIndicator);
                tvName = itemView.findViewById(R.id.tvPostName);
                tvAddress = itemView.findViewById(R.id.tvPostAddress);
                tvDescription = itemView.findViewById(R.id.tvPostDescription);
                tvDistance = itemView.findViewById(R.id.tvPostDistance);
                btnGetDirections = itemView.findViewById(R.id.btnGetDirections);
            }
        }
        // Page indicator
        private void setupPageIndicator(LinearLayout indicatorLayout, int count, ViewPager2 viewPager) {
            indicatorLayout.removeAllViews();
            ImageView[] dots = new ImageView[count];
            for (int i = 0; i < count; i++) {
                dots[i] = new ImageView(indicatorLayout.getContext());
                dots[i].setImageResource(android.R.drawable.presence_invisible);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(20, 20);
                params.setMargins(4, 0, 4, 0);
                indicatorLayout.addView(dots[i], params);
            }
            if (count > 0) dots[0].setImageResource(android.R.drawable.presence_online);
            viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    for (int i = 0; i < count; i++) {
                        dots[i].setImageResource(android.R.drawable.presence_invisible);
                    }
                    dots[position].setImageResource(android.R.drawable.presence_online);
                }
            });
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
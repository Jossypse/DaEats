package com.example.mobappproject;

import android.app.Dialog;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class LocationPickerFragment extends DialogFragment implements OnMapReadyCallback {
    private GoogleMap mMap;
    private LatLng selectedLatLng;
    private String selectedAddress;
    private Button btnConfirm;
    private FusedLocationProviderClient fusedLocationClient;
    private ImageView ivMapPin;

    // Landmark data class
    private static class Landmark {
        String name;
        double lat, lng;
        Landmark(String name, double lat, double lng) {
            this.name = name;
            this.lat = lat;
            this.lng = lng;
        }
    }

    // List of popular landmarks in Daet
    private static final Landmark[] LANDMARKS = new Landmark[] {
        new Landmark("Camarines Norte State College", 14.1128, 122.9552),
        new Landmark("SM City Daet", 14.1161, 122.9557),
        new Landmark("Bagasbas Beach", 14.1442, 122.9822),
        new Landmark("Daet Municipal Hall", 14.1122, 122.9557),
        new Landmark("Our Lady of the Most Holy Trinity Cathedral", 14.1126, 122.9547)
    };

    public interface OnLocationPickedListener {
        void onLocationPicked(String address, double lat, double lng);
    }
    private OnLocationPickedListener listener;

    public void setListener(OnLocationPickedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // Listener should be set explicitly from the caller
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_location_picker, container, false);
        btnConfirm = view.findViewById(R.id.btnConfirmLocation);
        ivMapPin = view.findViewById(R.id.ivMapPin);
        Button btnCurrentLocation = view.findViewById(R.id.btnCurrentLocation);
        btnCurrentLocation.setOnClickListener(v -> moveToCurrentLocation());
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map_container);
        if (mapFragment == null) {
            mapFragment = SupportMapFragment.newInstance();
            getChildFragmentManager().beginTransaction().replace(R.id.map_container, mapFragment).commit();
        }
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Toast.makeText(getContext(), "Map fragment is null", Toast.LENGTH_SHORT).show();
        }

        // Set confirm button logic here to ensure btnConfirm is not null
        btnConfirm.setOnClickListener(v -> {
            if (selectedLatLng != null) {
                String addressToSend = selectedAddress;
                if (addressToSend == null || addressToSend.trim().isEmpty()) {
                    addressToSend = "Dropped Pin";
                }
                if (listener != null) {
                    listener.onLocationPicked(addressToSend, selectedLatLng.latitude, selectedLatLng.longitude);
                }
                dismiss();
            } else {
                Toast.makeText(getContext(), "Please select a location", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        // Add landmark markers
        for (Landmark lm : LANDMARKS) {
            mMap.addMarker(new com.google.android.gms.maps.model.MarkerOptions()
                .position(new LatLng(lm.lat, lm.lng))
                .title(lm.name)
            );
        }
        mMap.setOnMarkerClickListener(marker -> {
            selectedLatLng = marker.getPosition();
            selectedAddress = marker.getTitle();
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLng, 17f));
            marker.showInfoWindow();
            Toast.makeText(getContext(), "Selected: " + marker.getTitle(), Toast.LENGTH_SHORT).show();
            return true;
        });
        // Allow user to tap any POI (restaurant, landmark, etc.)
        mMap.setOnPoiClickListener(poi -> {
            selectedLatLng = poi.latLng;
            selectedAddress = poi.name;
            mMap.clear();
            // Re-add landmark markers
            for (Landmark lm : LANDMARKS) {
                mMap.addMarker(new com.google.android.gms.maps.model.MarkerOptions()
                    .position(new LatLng(lm.lat, lm.lng))
                    .title(lm.name)
                );
            }
            com.google.android.gms.maps.model.Marker marker = mMap.addMarker(
                new com.google.android.gms.maps.model.MarkerOptions()
                    .position(poi.latLng)
                    .title(poi.name)
                    .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_AZURE))
            );
            if (marker != null) marker.showInfoWindow();
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(poi.latLng, 17f));
            Toast.makeText(getContext(), "Selected: " + poi.name, Toast.LENGTH_SHORT).show();
        });
        mMap.setOnCameraIdleListener(() -> {
            selectedLatLng = mMap.getCameraPosition().target;
            selectedAddress = getAddressFromLatLng(selectedLatLng);
        });
        // Set default location to Daet, Camarines Norte, Philippines
        LatLng defaultLatLng = new LatLng(14.1124, 122.9555); // Daet, Camarines Norte
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLatLng, 14f));
    }

    private String getAddressFromLatLng(LatLng latLng) {
        Geocoder geocoder = new Geocoder(getContext(), Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                return addresses.get(0).getAddressLine(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void moveToCurrentLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 200);
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null && mMap != null) {
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                selectedLatLng = latLng;
                selectedAddress = getAddressFromLatLng(latLng);
                mMap.clear();
                // Re-add landmark markers
                for (Landmark lm : LANDMARKS) {
                    mMap.addMarker(new com.google.android.gms.maps.model.MarkerOptions()
                        .position(new LatLng(lm.lat, lm.lng))
                        .title(lm.name)
                    );
                }
                com.google.android.gms.maps.model.Marker marker = mMap.addMarker(
                    new com.google.android.gms.maps.model.MarkerOptions()
                        .position(latLng)
                        .title("Current Location")
                        .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_GREEN))
                );
                if (marker != null) marker.showInfoWindow();
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f));
                Toast.makeText(getContext(), "Current location selected", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Unable to get current location", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 200 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            moveToCurrentLocation();
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        return dialog;
    }
} 
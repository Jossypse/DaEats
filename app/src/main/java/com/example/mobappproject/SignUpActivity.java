package com.example.mobappproject;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.DatabaseError;

public class SignUpActivity extends AppCompatActivity {
    private EditText etFirstName, etLastName, etAddress, etUsername, etPassword;
    private Button btnSignUp,btnLogin;
    private DatabaseReference usersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        btnLogin = findViewById(R.id.Login);

        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etAddress = findViewById(R.id.etAddress);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnSignUp = findViewById(R.id.btnSignUp);
        usersRef = FirebaseDatabase.getInstance().getReference("User");

        btnSignUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String firstName = etFirstName.getText().toString().trim();
                String lastName = etLastName.getText().toString().trim();
                String address = etAddress.getText().toString().trim();
                String username = etUsername.getText().toString().trim();
                String password = etPassword.getText().toString().trim();
                if (TextUtils.isEmpty(firstName) || TextUtils.isEmpty(lastName) || TextUtils.isEmpty(address) || TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
                    Toast.makeText(SignUpActivity.this, "Please enter all fields", Toast.LENGTH_SHORT).show();
                    return;
                }
                generateUniqueUserIdAndSignUp(firstName, lastName, address, username, password);
            }
        });

        btnLogin.setOnClickListener(v -> {

            startActivity(new Intent(SignUpActivity.this, LoginActivity.class));

        });
    }

    private void generateUniqueUserIdAndSignUp(String firstName, String lastName, String address, String username, String password) {
        String userId = generateRandomUserId();
        usersRef.orderByChild("userId").equalTo(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // ID already exists, try again
                    generateUniqueUserIdAndSignUp(firstName, lastName, address, username, password);
                } else {
                    User user = new User(firstName, lastName, address, username, password, userId);
                    usersRef.child(userId).setValue(user);
                    Toast.makeText(SignUpActivity.this, "Sign up successful!", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(SignUpActivity.this, LoginActivity.class));
                    finish();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(SignUpActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String generateRandomUserId() {
        int randomId = 10000 + (int)(Math.random() * 90000);
        return String.valueOf(randomId);
    }
} 
package com.example.smaarak_app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.ByteArrayOutputStream;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    Button click_pic;
    ImageView picture;
    TextView output;
//    Task<JsonElement> task;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        click_pic = findViewById(R.id.click_pic);
        picture = findViewById(R.id.picture);


        //Requesting for camera permission if not granted already
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                    Manifest.permission.CAMERA
            }, 100);
        }

        click_pic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(intent, 100);
            }
        });
    }

    //Function to scale down image to save bandwidth
    public Bitmap scaleBitmapDown(Bitmap bitmap, int maxDimension) {
        int originalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();
        int resizedWidth = maxDimension;
        int resizedHeight = maxDimension;

        if (originalHeight > originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = (int) (resizedHeight * (float) originalWidth / (float) originalHeight);
        } else if (originalWidth > originalHeight) {
            resizedWidth = maxDimension;
            resizedHeight = (int) (resizedWidth * (float) originalHeight / (float) originalWidth);
        } else if (originalHeight == originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = maxDimension;
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        output = findViewById(R.id.textView);
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100) {
            Bitmap bitmap = (Bitmap) Objects.requireNonNull(data).getExtras().get("data");
            picture.setImageBitmap(bitmap);
            bitmap = scaleBitmapDown(bitmap, 640);

            // Convert bitmap to base64 encoded string
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
            byte[] imageBytes = byteArrayOutputStream.toByteArray();
            String base64encoded = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

            // Create json request to cloud vision
            JsonObject request = new JsonObject();

            // Add image to request
            JsonObject image = new JsonObject();
            image.add("content", new JsonPrimitive(base64encoded));
            request.add("image", image);

            //Add features to the request
            JsonObject feature = new JsonObject();
            feature.add("maxResults", new JsonPrimitive(5));
            feature.add("type", new JsonPrimitive("LANDMARK_DETECTION"));
            JsonArray features = new JsonArray();
            features.add(feature);
            request.add("features", features);

            annotateImage(request.toString())
                    .addOnCompleteListener(new OnCompleteListener<JsonElement>() {
                        @Override
                        public void onComplete(@NonNull Task<JsonElement> task) {
                            if (!task.isSuccessful()) {
                                // Task failed with an exception
                                // ...
                                Toast.makeText(MainActivity.this, "Error in processing request", Toast.LENGTH_LONG).show();
                            } else {
                                // Task completed successfully
                                // ...

                                for (JsonElement label : Objects.requireNonNull(task.getResult()).getAsJsonArray().get(0).getAsJsonObject().get("landmarkAnnotations").getAsJsonArray()) {
                                    JsonObject labelObj = label.getAsJsonObject();
                                    String landmarkName = labelObj.get("description").getAsString();
                                    String entityId = labelObj.get("mid").getAsString();
                                    float score = labelObj.get("score").getAsFloat();
                                    JsonObject bounds = labelObj.get("boundingPoly").getAsJsonObject();
                                    // Multiple locations are possible, e.g., the location of the depicted
                                    // landmark and the location the picture was taken.
                                    for (JsonElement loc : labelObj.get("locations").getAsJsonArray()) {
                                        JsonObject latLng = loc.getAsJsonObject().get("latLng").getAsJsonObject();
                                        double latitude = latLng.get("latitude").getAsDouble();
                                        double longitude = latLng.get("longitude").getAsDouble();
                                        System.out.println(landmarkName);
                                    }

                                }
                                Toast.makeText(MainActivity.this, "Request Successful", Toast.LENGTH_LONG).show();

                            }
                        }
                    });
        }
    }


    private Task<JsonElement> annotateImage (String requestJson){
        FirebaseFunctions mFunctions;
        mFunctions = FirebaseFunctions.getInstance();
        //mFunctions.useEmulator("10.0.2.2",5001);
        return mFunctions
                .getHttpsCallable("annotateImage")
                .call(requestJson)
                .continueWith(new Continuation<HttpsCallableResult, JsonElement>() {
                    @Override
                    public JsonElement then(@NonNull Task<HttpsCallableResult> task) {
                        // This continuation runs on either success or failure, but if the task
                        // has failed then getResult() will throw an Exception which will be
                        // propagated down.
                        return JsonParser.parseString(new Gson().toJson(Objects.requireNonNull(task.getResult()).getData()));
                    }
                });
    }
}




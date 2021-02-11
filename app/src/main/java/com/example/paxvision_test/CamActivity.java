package com.example.paxvision_test;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class CamActivity extends AppCompatActivity {
    public static int swap = 0;
    public static final int CAMERA = 102;
    public String mCurrentPhotoPath;
    private static final int CAMERA_REQUEST_CODE = 1;
    private StorageReference mStorage;
    private ProgressDialog mProgress;
    public ImageView photoholder;
    private TextView textView;
    public Uri uri;

    private File createImageFile() throws IOException {
// Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

// Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }



    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File...
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,getPackageName()+".fileprovider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, CAMERA_REQUEST_CODE);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cam);
        mStorage = FirebaseStorage.getInstance().getReference();
        mProgress = new ProgressDialog(this);
        photoholder  = findViewById(R.id.imageView);
        Button b_capture = findViewById(R.id.takePic);
        textView = findViewById(R.id.faceText);
        checkPermission(CAMERA);
        b_capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkPermission(CAMERA);
            }
        });

    }

    public void checkPermission(int requestCode) {
        int hasCameraPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (hasCameraPermission == PackageManager.PERMISSION_GRANTED) {
            dispatchTakePictureIntent();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, requestCode);
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            dispatchTakePictureIntent();
        } else {
            requestPermission(this, requestCode, R.string.camera_denied);
        }
    }

    public static void requestPermission(final Activity activity, final int requestCode, int message) {
        AlertDialog.Builder alert = new AlertDialog.Builder(activity);
        alert.setMessage(message);
        alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(intent, requestCode);
            }
        });
        alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        alert.setCancelable(false);
        alert.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAMERA_REQUEST_CODE) {
            //set the progress dialog
            mProgress.setMessage("Uploading...");
            mProgress.show();
            uri = Uri.parse(mCurrentPhotoPath);
            photoholder.setImageURI(uri);
            runFaceDetector(convertImageViewToBitmap(photoholder));
        }
    }
    private Bitmap convertImageViewToBitmap(ImageView v){

        Bitmap imageBm=((BitmapDrawable)v.getDrawable()).getBitmap();

        return imageBm;
    }
    private void runFaceDetector(Bitmap bitmap) {
        //Create a FirebaseVisionFaceDetectorOptions object//
        FirebaseVisionFaceDetectorOptions options = new FirebaseVisionFaceDetectorOptions.Builder()
        //Set the mode type; I’m using FAST_MODE//
                .setPerformanceMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
        //Run additional classifiers for characterizing facial features//
                .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
        //Detect all facial landmarks//
                .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
        //Set the smallest desired face size//
                .setMinFaceSize(0.1f)
                .build();


        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(bitmap);
        FirebaseVisionFaceDetector detector = FirebaseVision.getInstance().getVisionFaceDetector(options);
        detector.detectInImage(image).addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionFace>>() {
            @Override
            public void onSuccess(List<FirebaseVisionFace> faces) {
                textView.setText(runFaceDetection(faces));
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure
                    (@NonNull Exception exception) {
                Toast.makeText(CamActivity.this,
                        "Exception", Toast.LENGTH_LONG).show();
            }
        });
    }

    private String runFaceDetection(List<FirebaseVisionFace> faces) {
        StringBuilder result = new StringBuilder();
        float smilingProb = 0;
        float rightEyeOpenProb = 0;
        float leftEyeOpenProb = 0;

        for (FirebaseVisionFace face : faces) {
            StorageReference filepath = mStorage.child("Photos").child(uri.getPath());
            filepath.putFile(uri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    Toast.makeText(CamActivity.this, "Upload Complete...", Toast.LENGTH_SHORT).show();
                    swap = 1;
                    Intent intent = new Intent(CamActivity.this, DashboardActivity.class);
                    startActivity(intent);
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    mProgress.setMessage("Upload doesn't seem to be working...");
                    swap = 2;
                    Intent intent = new Intent(CamActivity.this, DashboardActivity.class);
                    startActivity(intent);
                }
            });
            //Retrieve the probability that the face is smiling//
            if (face.getSmilingProbability() != FirebaseVisionFace.UNCOMPUTED_PROBABILITY) {
                smilingProb = face.getSmilingProbability();
            }
            //Retrieve the probability that the right eye is open//
            if (face.getRightEyeOpenProbability() != FirebaseVisionFace.UNCOMPUTED_PROBABILITY) {
                rightEyeOpenProb = face.getRightEyeOpenProbability ();
            }
            //Retrieve the probability that the left eye is open//
            if (face.getLeftEyeOpenProbability() != FirebaseVisionFace.UNCOMPUTED_PROBABILITY) {
                leftEyeOpenProb = face.getLeftEyeOpenProbability();
            }

            result.append("Smile: ");
            //Check whether user is smiling
            if (smilingProb > 0.5) {
                result.append("Yes \nProbability: " + smilingProb);

            } else {

                result.append("No");
            }
            result.append("\n\nRight eye: ");
            //Check whether the right eye is open
            if (rightEyeOpenProb > 0.5) {
                result.append("Open \nProbability: " + rightEyeOpenProb);
            } else {
                result.append("Close");
            }
            result.append("\n\nLeft eye: ");
            //Check whether the left eye is open and print the results//
            if (leftEyeOpenProb > 0.5) {
                result.append("Open \nProbability: " + leftEyeOpenProb);
            } else {
                result.append("Close");
            }
            result.append("\n\n");
        }
        return result.toString();
    }
}

package com.maochun.mlkittest;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.common.model.LocalModel;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1001;
    private static final int REQUEST_CHOOSE_IMAGE = 1002;

    private Button captureImageBtn, detectTextBtn;
    private ImageView imageView;
    private TextView textView;
    private Bitmap imageBitmap;

    private GraphicOverlay graphicOverlay;
    private Uri imageUri;
    private VisionImageProcessor imageProcessor;

    private List<Pair<String, RectF>> textAndLocArray = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        captureImageBtn = findViewById(R.id.btn1);
        detectTextBtn = findViewById(R.id.btn2);
        imageView = findViewById(R.id.iv);
        textView = findViewById(R.id.tv);

        graphicOverlay = findViewById(R.id.graphic_overlay);
        graphicOverlay.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {

                final float x = motionEvent.getX();
                final float y = motionEvent.getY();

                for (Pair<String, RectF> textAndLoc : textAndLocArray){
                    if (textAndLoc.second.contains(x, y)){
                        textView.setText(textAndLoc.first);
                    }
                }



                return false;
            }
        });

        captureImageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchTakePictureIntent();
                textView.setText(""); // make null when capture button pressed.
            }
        });

        detectTextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                detectTextFromImage();
            }
        });


        imageProcessor = new TextRecognitionProcessor(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1000:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.Images.Media.TITLE, "New Picture");
                        values.put(MediaStore.Images.Media.DESCRIPTION, "From Camera");
                        imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                        startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                    }
                }
                break;

            default:
                break;
        }
    }

    // This method send the user to external activity for opening phone camera to take pic
    private void dispatchTakePictureIntent() {
/*

        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), REQUEST_CHOOSE_IMAGE);


 */

        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_DENIED) {

            // Should we show an explanation?
            if (shouldShowRequestPermissionRationale(
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {
                // Explain to the user why we need to read the contacts

                AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                alertDialog.setTitle("APP needs to read & write external storage.");
                alertDialog.setMessage("Please grant the permission.");
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                alertDialog.show();


            }

            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1000);

            // MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE is an
            // app-defined int constant that should be quite unique

            return;
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            //Bundle extras = data.getExtras();  // get image
            //imageBitmap = (Bitmap) extras.get("data");

            //imageView.setImageBitmap(imageBitmap); // place image on imageView.

            //imageUri = data.getData();
            tryReloadAndDetectInImage();
        }else if (requestCode == REQUEST_CHOOSE_IMAGE && resultCode == RESULT_OK) {
            // In this case, imageUri is returned by the chooser, save it.
            imageUri = data.getData();
            tryReloadAndDetectInImage();
        }
    }


    private void tryReloadAndDetectInImage() {
        Log.d("", "Try reload and detect image");
        try {
            if (imageUri == null) {
                return;
            }

            //if (SIZE_SCREEN.equals(selectedSize) && imageMaxWidth == 0) {
                // UI layout has not finished yet, will reload once it's ready.
            //    return;
            //}

            Bitmap imageBitmapOri = BitmapUtils.getBitmapFromContentUri(getContentResolver(), imageUri);
            if (imageBitmapOri == null) {
                return;
            }

            // Clear the overlay first
            graphicOverlay.clear();

            // Get the dimensions of the image view
            Pair<Integer, Integer> targetedSize = new Pair<>(imageView.getWidth(), imageView.getHeight());

            // Determine how much to scale down the image
            float scaleFactor = (float) imageBitmapOri.getWidth() / (float) targetedSize.first;
                    //Math.max(
                    //        (float) imageBitmapOri.getWidth() / (float) targetedSize.first,
                    //        (float) imageBitmapOri.getHeight() / (float) targetedSize.second);

            imageBitmap =
                    Bitmap.createScaledBitmap(
                            imageBitmapOri,
                            (int) (imageBitmapOri.getWidth() / scaleFactor),
                            (int) (imageBitmapOri.getHeight() / scaleFactor),
                            true);

            imageView.setImageBitmap(imageBitmap);


            if (imageProcessor != null) {
                graphicOverlay.setImageSourceInfo(
                        imageBitmap.getWidth(), imageBitmap.getHeight(), false);
                imageProcessor.processBitmap(imageBitmap, graphicOverlay);
            } else {
                Log.e("", "Null imageProcessor, please check adb logs for imageProcessor creation error");
            }

            textAndLocArray = TextGraphic.textAndLocArray;

        } catch (IOException e) {
            Log.e("", "Error retrieving saved image");
            imageUri = null;
        }
    }

    private void detectTextFromImage() {
        InputImage image = InputImage.fromBitmap(imageBitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient();
        Task<Text> result =
                recognizer.process(image)
                        .addOnSuccessListener(new OnSuccessListener<Text>() {
                            @Override
                            public void onSuccess(Text visionText) {
                                String resultText = visionText.getText();
                                String blockText = "";
                                for (Text.TextBlock block : visionText.getTextBlocks()) {
                                    blockText += block.getText() + " ";


                                }

                                textView.setText(blockText);
                            }
                        })
                        .addOnFailureListener(
                                new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        // Task failed with an exception
                                        // ...
                                    }
                                });
        /*
        FirebaseVisionImage firebaseVisionImage = FirebaseVisionImage.fromBitmap(imageBitmap);
        FirebaseVisionTextDetector firebaseVisionTextDetector = FirebaseVision.getInstance().getVisionTextDetector();
        firebaseVisionTextDetector.detectInImage(firebaseVisionImage).addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
            @Override
            public void onSuccess(FirebaseVisionText firebaseVisionText) {
                displayTextFromImage(firebaseVisionText); // call method

            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this, "Error: "+e, Toast.LENGTH_SHORT).show();
            }
        });
        */
    }

    /*
    private void displayTextFromImage(FirebaseVisionText firebaseVisionText) {

        List<FirebaseVisionText.Block> blockList = firebaseVisionText.getBlocks();

        if(blockList.size() == 0){
            Toast.makeText(this, "No Text Found In Image", Toast.LENGTH_SHORT);
        }
        else{

            String text = "";
            for(FirebaseVisionText.Block block : firebaseVisionText.getBlocks()){
                text += block.getText();

            }
            textView.setText(text);
        }
    }



     */
}
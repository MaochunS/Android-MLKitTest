package com.maochun.mlkittest;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
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

public class OCRPreviewActivity extends AppCompatActivity {

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
        setContentView(R.layout.activity_ocrpreview);

        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.colorBlue)); // Navigation bar the soft bottom of some phones like nexus and some Samsung note series
        getWindow().setStatusBarColor(ContextCompat.getColor(this,R.color.colorBlue));

        //captureImageBtn = findViewById(R.id.btn1);
        //detectTextBtn = findViewById(R.id.btn2);
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

        /*
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


         */

        imageProcessor = new TextRecognitionProcessor(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        imageUri = MainActivity.imageUri;
        tryReloadAndDetectInImage();
    }

    public void onBackButtonClick(View v){
        super.onBackPressed();
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
            TextGraphic.textAndLocArray.clear();

            float scaleFactor = (float) imageBitmapOri.getWidth() / Resources.getSystem().getDisplayMetrics().widthPixels; //(float) imageView.getWidth();

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

    }


}
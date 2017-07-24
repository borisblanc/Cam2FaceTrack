package com.example.ezequiel.camera2;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.ezequiel.camera2.others.Camera2Source;
import com.example.ezequiel.camera2.others.CustomFaceDetector;
import com.example.ezequiel.camera2.others.FaceGraphic;
import com.example.ezequiel.camera2.utils.Utils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.Console;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.TreeMap;

import com.example.ezequiel.camera2.others.CameraSource;
import com.example.ezequiel.camera2.others.CameraSourcePreview;
import com.example.ezequiel.camera2.others.GraphicOverlay;
import com.google.android.gms.vision.face.LargestFaceFocusingProcessor;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private Context context;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int REQUEST_STORAGE_PERMISSION = 201;
    private TextView cameraVersion;
    private ImageView ivAutoFocus;

    // CAMERA VERSION ONE DECLARATIONS
    private CameraSource mCameraSource = null;

    // CAMERA VERSION TWO DECLARATIONS
    private Camera2Source mCamera2Source = null;

    // COMMON TO BOTH CAMERAS
    private CameraSourcePreview mPreview;
    private FaceDetector previewFaceDetector = null;
    private GraphicOverlay mGraphicOverlay;
    private FaceGraphic mFaceGraphic;
    private boolean wasActivityResumed = false;

    // DEFAULT CAMERA BEING OPENED
    private boolean usingFrontCamera = true;

    // MUST BE CAREFUL USING THIS VARIABLE.
    // ANY ATTEMPT TO START CAMERA2 ON API < 21 WILL CRASH.
    private boolean useCamera2 = false;

    private TreeMap<Long,SparseArray<Face>> _faces;

    private boolean trackRecord = false; //determines if regular preview is opened or we start track record also

    private String GetFileoutputPath()
    {
        File _filesdir = android.os.Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(_filesdir, Calendar.getInstance().getTimeInMillis() + ".mp4").getAbsolutePath();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE); lock this in manifest instead
            setContentView(R.layout.activity_main);
            context = getApplicationContext();


            final Button recButton = (Button) findViewById(R.id.btn_record);
            Button switchButton = (Button) findViewById(R.id.btn_switch);
            mPreview = (CameraSourcePreview) findViewById(R.id.preview);
            mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);
            cameraVersion = (TextView) findViewById(R.id.cameraVersion);
            ivAutoFocus = (ImageView) findViewById(R.id.ivAutoFocus);

            if (checkGooglePlayAvailability()) {

                requestPermissionThenOpenCamera();

                switchButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (usingFrontCamera) {
                            stopCameraSource();
                            createCameraSource(Camera2Source.CAMERA_FACING_FRONT);
                            usingFrontCamera = false;
                        } else {
                            stopCameraSource();
                            createCameraSource(Camera2Source.CAMERA_FACING_BACK);
                            usingFrontCamera = true;
                        }
                    }
                });


                recButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        if (mCamera2Source.mTrackRecord) {
                            mCamera2Source.stopRecordingVideo();
                        } else {
                            recButton.setText("Stop");
                            //mCamera2Source.startRecordingVideo(_createfilepath);
                        }
                    }
                });

//                new Thread(new Runnable() {
//                    public void run() {
//                        Checkfaces();
//                    }
//                }).start();

                //Checkfaces();
                //mPreview.setOnTouchListener(CameraPreviewTouchListener);
            }
        }
        catch (Exception e)
        {
            Log.d(TAG,e.getMessage());
        }

    }

//    private void Checkfaces() {
//        try {
//            int frames = 0;
//            while (frames < 1000) {
//                Thread.sleep(1000);
//                int count = _faces.size();
//                Log.d(TAG, "count = " + count);
//                frames++;
//            }
//        }
//        catch (InterruptedException e)
//        {
//
//        }
//    }


    private boolean checkGooglePlayAvailability() {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context);
        if(resultCode == ConnectionResult.SUCCESS) {
            return true;
        } else {
            if(googleApiAvailability.isUserResolvableError(resultCode)) {
                googleApiAvailability.getErrorDialog(MainActivity.this, resultCode, 2404).show();
            }
        }
        return false;
    }

    private void requestPermissionThenOpenCamera() {
        if(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                useCamera2 = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
                createCameraSource(Camera2Source.CAMERA_FACING_FRONT);
                //ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 0);
            }
            else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    private void createCameraSource(int facing)
    {
        if (trackRecord) {
            previewFaceDetector = new FaceDetector.Builder(context)
                    .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                    .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                    .setMode(FaceDetector.FAST_MODE)
                    .setProminentFaceOnly(true)
                    .setTrackingEnabled(true)
                    .build();

            _faces = new TreeMap<>(); //new face list for every camera recording session
            CustomFaceDetector faceDetector = new CustomFaceDetector(previewFaceDetector, _faces);

            if (previewFaceDetector.isOperational()) {
                //previewFaceDetector.setProcessor(new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory()).build());

                faceDetector.setProcessor(
                        new LargestFaceFocusingProcessor.Builder(previewFaceDetector, new GraphicFaceTracker(mGraphicOverlay))
                                .build());

            } else {
                Toast.makeText(context, "FACE DETECTION NOT AVAILABLE", Toast.LENGTH_SHORT).show();
            }

            if (useCamera2) {
                mCamera2Source = new Camera2Source.Builder(context, faceDetector, GetFileoutputPath())
                        .setFocusMode(Camera2Source.CAMERA_AF_AUTO)
                        .setFlashMode(Camera2Source.CAMERA_FLASH_AUTO)
                        .setFacing(facing) //Camera2Source.CAMERA_FACING_FRONT = 1 or CAMERA_FACING_BACK = 0
                        .build();

                //IF CAMERA2 HARDWARE LEVEL IS LEGACY, CAMERA2 IS NOT NATIVE.
                //WE WILL USE CAMERA1.
                if (mCamera2Source.isCamera2Native()) {
                    startCameraSource();
                } else {
                    useCamera2 = false;
                    createCameraSource(usingFrontCamera ? Camera2Source.CAMERA_FACING_FRONT : Camera2Source.CAMERA_FACING_BACK);
                }
            } else {
                mCameraSource = new CameraSource.Builder(context, faceDetector)
                        .setFacing(facing)
                        .setRequestedFps(30.0f)
                        .build();

                startCameraSource();
            }
        }
        else //preview only camera 2 only for now
        {
            mCamera2Source = new Camera2Source.Builder(context)
                    .setFacing(facing)
                    .build();
            startCameraSource();
        }
    }



    private void startCameraSource() {
        if(useCamera2) {
            if(mCamera2Source != null) {
                cameraVersion.setText("Camera 2");
                try {mPreview.start(mCamera2Source, mGraphicOverlay, trackRecord);
                } catch (IOException e) {
                    Log.e(TAG, "Unable to start camera source 2.", e);
                    mCamera2Source.release();
                    mCamera2Source = null;
                }
            }
        } else {
            if (mCameraSource != null) {
                cameraVersion.setText("Camera 1");
                try {mPreview.start(mCameraSource, mGraphicOverlay);
                } catch (IOException e) {
                    Log.e(TAG, "Unable to start camera source.", e);
                    mCameraSource.release();
                    mCameraSource = null;
                }
            }
        }
    }

    private void stopCameraSource() {
        mPreview.stop();
    }

//    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
//        @Override
//        public Tracker<Face> create(Face face) {
//            return new GraphicFaceTracker(mGraphicOverlay);
//        }
//    }

    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay, context);
        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, Face item) {
            mFaceGraphic.setId(faceId);
        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mFaceGraphic.goneFace();
            mOverlay.remove(mFaceGraphic);
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mFaceGraphic.goneFace();
            mOverlay.remove(mFaceGraphic);
        }
    }

//    private final CameraSourcePreview.OnTouchListener CameraPreviewTouchListener = new CameraSourcePreview.OnTouchListener() {
//        @Override
//        public boolean onTouch(View v, MotionEvent pEvent) {
//            v.onTouchEvent(pEvent);
//            if (pEvent.getAction() == MotionEvent.ACTION_DOWN) {
//                int autoFocusX = (int) (pEvent.getX() - Utils.dpToPx(60)/2);
//                int autoFocusY = (int) (pEvent.getY() - Utils.dpToPx(60)/2);
//                ivAutoFocus.setTranslationX(autoFocusX);
//                ivAutoFocus.setTranslationY(autoFocusY);
//                ivAutoFocus.setVisibility(View.VISIBLE);
//                ivAutoFocus.bringToFront();
//                if(useCamera2) {
//                    if(mCamera2Source != null) {
//                        mCamera2Source.autoFocus(new Camera2Source.AutoFocusCallback() {
//                            @Override
//                            public void onAutoFocus(boolean success) {
//                                runOnUiThread(new Runnable() {
//                                    @Override public void run() {ivAutoFocus.setVisibility(View.GONE);}
//                                });
//                            }
//                        }, pEvent, v.getWidth(), v.getHeight());
//                    } else {
//                        ivAutoFocus.setVisibility(View.GONE);
//                    }
//                } else {
//                    if(mCameraSource != null) {
//                        mCameraSource.autoFocus(new CameraSource.AutoFocusCallback() {
//                            @Override
//                            public void onAutoFocus(boolean success) {
//                                runOnUiThread(new Runnable() {
//                                    @Override public void run() {ivAutoFocus.setVisibility(View.GONE);}
//                                });
//                            }
//                        });
//                    } else {
//                        ivAutoFocus.setVisibility(View.GONE);
//                    }
//                }
//            }
//            return false;
//        }
//    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestPermissionThenOpenCamera();
            } else {
                Toast.makeText(MainActivity.this, "CAMERA PERMISSION REQUIRED", Toast.LENGTH_LONG).show();
                finish();
            }
        }
        if(requestCode == REQUEST_STORAGE_PERMISSION) {
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestPermissionThenOpenCamera();
            } else {
                Toast.makeText(MainActivity.this, "STORAGE PERMISSION REQUIRED", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(wasActivityResumed)
        	//If the CAMERA2 is paused then resumed, it won't start again unless creating the whole camera again.
        	if(useCamera2)
        	{
                createCameraSource(usingFrontCamera ? Camera2Source.CAMERA_FACING_FRONT : Camera2Source.CAMERA_FACING_BACK);
        	} else {
        		startCameraSource();
        	}
    }

    @Override
    protected void onPause() {
        super.onPause();
        wasActivityResumed = true;
        stopCameraSource();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCameraSource();
        if(previewFaceDetector != null) {
            previewFaceDetector.release();
        }
    }
}

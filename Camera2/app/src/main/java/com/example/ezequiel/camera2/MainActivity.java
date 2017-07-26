package com.example.ezequiel.camera2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.ezequiel.camera2.others.Camera2Source;
import com.example.ezequiel.camera2.others.CameraSource;
import com.example.ezequiel.camera2.others.CameraSourcePreview;
import com.example.ezequiel.camera2.others.CustomFaceDetector;
import com.example.ezequiel.camera2.others.ExtractMpegFramesTest;
import com.example.ezequiel.camera2.others.FaceGraphic;
import com.example.ezequiel.camera2.others.FrameData;
import com.example.ezequiel.camera2.others.GraphicOverlay;
import com.example.ezequiel.camera2.others.VideoUtils;
import com.example.ezequiel.camera2.utils.Utils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.LargestFaceFocusingProcessor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

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

    private ArrayList<FrameData> _faces;

    private boolean trackRecord = false; //determines if regular preview is opened or we start track record also

    public Button recButton;


    private int _fps = 30;

    private int _vidlengthseconds = 3;

    private File VideoFileDir;

    private File RecordedVideoOutputFileFolder()
    {
        String VideoFileName = Calendar.getInstance().getTimeInMillis() + ".mp4";
        return new File(VideoFileDir, VideoFileName);
    }

    private File TrimmedVideoOutputFileFolder()
    {
        String trimVideoFileName = Calendar.getInstance().getTimeInMillis() + ".mp4";
        return new File(VideoFileDir, trimVideoFileName);
    }

    private File MergedVideoOutputFileFolder()
    {
        String trimVideoFileName = Calendar.getInstance().getTimeInMillis() + ".mp4";
        return new File(VideoFileDir, trimVideoFileName);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);

            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE); lock this in manifest instead
            setContentView(R.layout.activity_main);
            context = getApplicationContext();
            VideoFileDir = context.getExternalFilesDir(null);

            recButton = (Button) findViewById(R.id.btn_record);
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
                            stopCameraSource(); //call this to release everything or all the shit breaks
                            trackRecord = false;
                            recButton.setText(R.string.record);
                            requestPermissionThenOpenCamera();
                            try {
                                CreateTrimmedVideo(Processfaces(_faces));
                            } catch (IOException e) {
                                Log.d(TAG,e.getMessage());
                            }

                        }
                        else {
                            stopCameraSource(); //call this to release everything or all the shit breaks
                            trackRecord = true;
                            recButton.setText(R.string.stop);
                            requestPermissionThenOpenCamera();
                        }
                    }
                });


                //mPreview.setOnTouchListener(CameraPreviewTouchListener);
            }
        } catch (Throwable e) {
            Log.d(TAG,e.getMessage());
        }

    }


    private void CreateTrimmedVideo(FrameData.Tuple<Long,Long> bestfacetimestamps) throws IOException
    {

        VideoUtils.genTrimVideoUsingMuxer(RecordedVideoOutputFileFolder().getAbsolutePath(), TrimmedVideoOutputFileFolder().getPath(), bestfacetimestamps.x, bestfacetimestamps.y, false, true);

        VideoUtils.muxVideos(MergedVideoOutputFileFolder().getAbsolutePath(), RecordedVideoOutputFileFolder().getAbsolutePath(),TrimmedVideoOutputFileFolder().getAbsolutePath(), context);
    }

    private FrameData.Tuple<Long,Long> Processfaces(ArrayList<FrameData> _faces)
    {
        if (_faces == null || _faces.size() < GetFrameTotal()) {
            Toast.makeText(context, "Not enough frames captured to process video for trim", Toast.LENGTH_SHORT).show();
            return null;
        }

        ArrayList<FrameData.FaceData> corefinalscores = new ArrayList<>();
        int coreframeslength = _fps * 2; //core sample of frames will be two seconds of video might in future vary depending on user settings
        int computelimit = _faces.size() - GetFrameTotal(); //this will keep walking average calcs only happening within range

        for(FrameData facedata : _faces )
        {
            Long currenttimestamp = facedata._timeStamp;
            if (_faces.indexOf(facedata) < computelimit) //makes sense to compute because we will can use it
            {
                List<FrameData> coreframes = _faces.subList(_faces.indexOf(facedata), _faces.indexOf(facedata) + coreframeslength);
                ArrayList<FrameData.FaceData> corescores = new ArrayList<>();
                for (FrameData face : coreframes)
                {
                    corescores.add(new FrameData.FaceData(face._timeStamp, Utils.GetImageUsability(Utils.GetFirstFace(face._faces)))); //todo improve this by moving all the face finding and scoring outside so its done only once per frame
                }

                double avg = Utils.calculateAverage(corescores);
                double stDev = Utils.stDev(corescores);

                corefinalscores.add(new FrameData.FaceData(currenttimestamp, avg < stDev ? 0 :  avg  - stDev)); //avg - std dev should give those with best avg score and lowest deviation /no negatives
            }
            else //can't use computations past this point so just need timestamps
            {
                corefinalscores.add(new FrameData.FaceData(currenttimestamp, 0 ));
            }
        }

        FrameData.FaceData bestFirstfacedata = Collections.max(corefinalscores, new FrameData.compScore());

        FrameData.FaceData bestLastfacedata = corefinalscores.get(corefinalscores.indexOf(bestFirstfacedata) + GetFrameTotal());

        return new FrameData.Tuple<>(bestFirstfacedata._timeStamp, bestLastfacedata._timeStamp);
    }


    private int GetFrameTotal()
    {
        return _fps * _vidlengthseconds; //total frames will always be frames per second * number of seconds
    }



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

            _faces = new ArrayList<>(); //new face list for every camera recording session
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
                mCamera2Source = new Camera2Source.Builder(context, faceDetector, RecordedVideoOutputFileFolder().getAbsolutePath())
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

    private void testextractframes() throws Throwable {
        File _filesdir = android.os.Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String filename = "VID_20170714_133159.mp4";
        ExtractMpegFramesTest test = new ExtractMpegFramesTest();
        test.testExtractMpegFrames(_filesdir, filename);
    }

}


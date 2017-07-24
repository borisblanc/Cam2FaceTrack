package com.example.ezequiel.camera2.others;

import android.util.SparseArray;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import java.util.ArrayList;


public class CustomFaceDetector extends Detector<Face> {

    private ArrayList<FrameData> _faces = new ArrayList<>();
    private Detector<Face> mDelegate;

    public CustomFaceDetector(Detector<Face> delegate, ArrayList<FrameData> faces) {
        mDelegate = delegate;
        _faces = faces;
    }

    public SparseArray<Face> detect(Frame frame) {

        Long timestamp = frame.getMetadata().getTimestampMillis();
        SparseArray<Face> faces = mDelegate.detect(frame);
        _faces.add(new FrameData(timestamp, faces));

        return faces;
    }

    public boolean isOperational() {
        return mDelegate.isOperational();
    }

    public boolean setFocus(int id) {
        return mDelegate.setFocus(id);
    }
}
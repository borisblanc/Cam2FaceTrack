package com.example.ezequiel.camera2.others;

import android.util.SparseArray;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import java.util.TreeMap;


public class CustomFaceDetector extends Detector<Face> {

    private TreeMap<Long,SparseArray<Face>> _faces = new TreeMap<>();
    private Detector<Face> mDelegate;

    public CustomFaceDetector(Detector<Face> delegate, TreeMap<Long,SparseArray<Face>> faces) {
        mDelegate = delegate;
        _faces = faces;
    }

    public SparseArray<Face> detect(Frame frame) {

        Long timestamp = frame.getMetadata().getTimestampMillis();
        SparseArray<Face> faces = mDelegate.detect(frame);
        _faces.put(timestamp, faces);

        return faces;
    }

    public boolean isOperational() {
        return mDelegate.isOperational();
    }

    public boolean setFocus(int id) {
        return mDelegate.setFocus(id);
    }
}
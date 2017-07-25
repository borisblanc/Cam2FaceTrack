package com.example.ezequiel.camera2.others;

import android.util.SparseArray;

import com.google.android.gms.vision.face.Face;

import java.util.Comparator;


public class FrameData {

    public SparseArray<Face> _faces;
    public Long _timeStamp;

    public FrameData(Long timeStamp, SparseArray<Face> faces) {
        _timeStamp = timeStamp;
        _faces = faces;
    }

    public static class FaceData {

        public double _score;
        public Long _timeStamp;

        public FaceData(Long timeStamp, double score) {
            _timeStamp = timeStamp;
            _score = score;
        }

    }

    public static class compScore implements Comparator<FaceData> {
        public int compare(FaceData a, FaceData b) {
            if (a._score > b._score)
                return 1; // highest value first
            if (a._score == b._score)
                return 0;
            return -1;
        }
    }

    public static class Tuple<X, Y> {
        public final X x;
        public final Y y;
        public Tuple(X x, Y y) {
            this.x = x;
            this.y = y;
        }
    }

}


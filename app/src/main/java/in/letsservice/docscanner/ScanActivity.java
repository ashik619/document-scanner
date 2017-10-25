package in.letsservice.docscanner;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ScanActivity extends AppCompatActivity {
    private static final String TAG = "Scan";
    private static final double INCREASE_CONTRAST_VALUE = 1.7;
    private static final int INCREASE_BRIGHTNESS_VALUE = 10;
    private static final int MAX_HEIGHT = 800;
    private static final double IMAGE_SHARPENING_VALUE_1 = 1.5;
    private static final double IMAGE_SHARPENING_VALUE_2 = -0.8;

    QuadrilateralSelectionImageView quadrilateralSelectionImageView;
    ImageView scannedImageView;
    Button scanButton;
    Button okButton;
    Button cancelButton;
    RelativeLayout mainLayout;
    RelativeLayout afterScanLayout;

    static {
        if (!OpenCVLoader.initDebug()) {
            // Log.e(TAG, "OpenCV not loaded");
        } else {
            // Log.e(TAG, "OpenCV loaded");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_scan);
        initialize();
    }

    Bitmap bmp;
    String imagePath;
    ProgressDialog progressDialog;

    void initialize() {
        try {
            quadrilateralSelectionImageView = (QuadrilateralSelectionImageView) findViewById(R.id.quadImage);
            scannedImageView = (ImageView) findViewById(R.id.image);
            scanButton = (Button) findViewById(R.id.scanButton);
            okButton = (Button) findViewById(R.id.okButton);
            cancelButton = (Button) findViewById(R.id.cancelButton);
            mainLayout = (RelativeLayout) findViewById(R.id.mainLayout);
            afterScanLayout = (RelativeLayout) findViewById(R.id.afterScanLayout);
            imagePath = getIntent().getStringExtra("path");
            bmp = getBitmap(imagePath);
            if (bmp == null) {
                Intent returnIntent = new Intent();
                setResult(Activity.RESULT_OK, returnIntent);
                finish();
            }
            quadrilateralSelectionImageView.setImageBitmap(getResizedBitmap(bmp, MAX_HEIGHT));
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!OpenCVLoader.initDebug()) {
                        Intent returnIntent = new Intent();
                        setResult(Activity.RESULT_CANCELED, returnIntent);
                        finish();
                        return;
                    }
                    List<PointF> points = findPoints();
                    if (points != null) {
                        boolean dontShow = false;
                        for (int i = 0; i < (points.size() - 1); i++) {
                            if (Math.sqrt(Math.pow((points.get(i + 1).length() - points.get(i).length()), 2)) < 180) {
                                dontShow = true;
                            }
                        }
                        if (!dontShow) {
                            quadrilateralSelectionImageView.setPoints(points);
                        }
                    }
                    scanButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            progressDialog = new ProgressDialog(ScanActivity.this);
                            progressDialog.setMessage("Scanning...");
                            progressDialog.setCancelable(false);
                            progressDialog.show();
                            new ScanImageTask().execute();
                        }
                    });
                }
            }, 2000);

        } catch (Exception e) {
           // Crashlytics.logException(e);
            Intent returnIntent = new Intent();
            setResult(Activity.RESULT_CANCELED, returnIntent);
            finish();
        }
    }

    private class ScanImageTask extends AsyncTask<Void, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(Void... params) {
            return scanImage();
        }

        @Override
        protected void onPostExecute(final Bitmap bitmap) {
            super.onPostExecute(bitmap);
            if (bitmap != null) {
                mainLayout.setVisibility(View.INVISIBLE);
                afterScanLayout.setVisibility(View.VISIBLE);
                scannedImageView.setImageBitmap(bitmap);
                progressDialog.dismiss();
                okButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        saveImage(bitmap);
                    }
                });
                cancelButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        recreate();
                    }
                });
            } else {
                Intent returnIntent = new Intent();
                setResult(Activity.RESULT_CANCELED, returnIntent);
                finish();
            }
        }

    }

    //@AddTrace(name = "scan_trace")
    Bitmap scanImage() {
        try {
            List<PointF> points = quadrilateralSelectionImageView.getPoints();
            if (bmp != null) {
                Mat orig = new Mat();
                org.opencv.android.Utils.bitmapToMat(bmp, orig);
                Mat transformed = perspectiveTransform(orig, points);
                Imgproc.cvtColor(transformed, transformed, Imgproc.COLOR_RGB2GRAY, 4);
                Mat orginal = transformed.clone();
                org.opencv.core.Size s = new Size(0, 0);
                Imgproc.GaussianBlur(transformed, transformed, s, 10);
                Core.addWeighted(orginal, IMAGE_SHARPENING_VALUE_1, transformed, IMAGE_SHARPENING_VALUE_2, 0, transformed);
                transformed.convertTo(transformed, -1, INCREASE_CONTRAST_VALUE, INCREASE_BRIGHTNESS_VALUE);
                bmp.recycle();
                final Bitmap result = Bitmap.createBitmap(transformed.width(), transformed.height(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(transformed, result);
                orig.release();
                transformed.release();
                return result;
            } else return null;
        } catch (Exception e) {
            //Crashlytics.logException(e);
            return null;
        }
    }

    void saveImage(Bitmap bmp) {
        Intent returnIntent = new Intent();
        try {
            File pictureFile = new File(imagePath);
            FileOutputStream fos = new FileOutputStream(pictureFile);
            bmp.compress(Bitmap.CompressFormat.JPEG, 99, fos);
            fos.close();
            setResult(Activity.RESULT_OK, returnIntent);
            finish();
        } catch (FileNotFoundException e) {
            //Crashlytics.logException(e);
            setResult(Activity.RESULT_CANCELED, returnIntent);
            finish();
        } catch (IOException e) {
            //Crashlytics.logException(e);
            setResult(Activity.RESULT_CANCELED, returnIntent);
            finish();
        }
    }

    //@AddTrace(name = "load_bitmap")
    Bitmap getBitmap(String mCurrentPhotoPath) {
        try {
            Bitmap bitmap;
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(mCurrentPhotoPath, options);
            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, MAX_HEIGHT, MAX_HEIGHT);
            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false;
            options.inMutable = true;
            bitmap = BitmapFactory.decodeFile(mCurrentPhotoPath, options);
            return bitmap;
        } catch (OutOfMemoryError | NullPointerException e) {
            //Crashlytics.logException(e);
            return null;
        }
    }

    private int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private List<PointF> findPoints() {
        try {
            List<PointF> result = null;
            Mat image = new Mat();
            Mat orig = new Mat();
            org.opencv.android.Utils.bitmapToMat(getResizedBitmap(bmp, MAX_HEIGHT), image);
            org.opencv.android.Utils.bitmapToMat(bmp, orig);

            Mat edges = edgeDetection(image);
            MatOfPoint2f largest = findLargestContour(edges);

            if (largest != null) {
                Point[] points = sortPoints(largest.toArray());
                result = new ArrayList<>();
                result.add(new PointF(Double.valueOf(points[0].x).floatValue(), Double.valueOf(points[0].y).floatValue()));
                result.add(new PointF(Double.valueOf(points[1].x).floatValue(), Double.valueOf(points[1].y).floatValue()));
                result.add(new PointF(Double.valueOf(points[2].x).floatValue(), Double.valueOf(points[2].y).floatValue()));
                result.add(new PointF(Double.valueOf(points[3].x).floatValue(), Double.valueOf(points[3].y).floatValue()));
                largest.release();
            } else {

            }

            edges.release();
            image.release();
            orig.release();
            return result;
        } catch (Exception e) {
            //Crashlytics.logException(e);
            return null;
        }
    }

    private Bitmap getResizedBitmap(Bitmap bitmap, int maxHeight) {
        double ratio = bitmap.getHeight() / (double) maxHeight;
        int width = (int) (bitmap.getWidth() / ratio);
        return Bitmap.createScaledBitmap(bitmap, width, maxHeight, false);
    }

    private Mat edgeDetection(Mat src) {
        Mat edges = new Mat();
        Imgproc.cvtColor(src, edges, Imgproc.COLOR_BGR2GRAY);
        Imgproc.GaussianBlur(edges, edges, new Size(5, 5), 0);
        Imgproc.Canny(edges, edges, 75, 200);
        return edges;
    }

    /**
     * Find the largest 4 point contour in the given Mat.
     *
     * @param src A valid Mat
     * @return The largest contour as a Mat
     */
    private MatOfPoint2f findLargestContour(Mat src) {
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(src, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

        // Get the 5 largest contours
        try {

            Collections.sort(contours, new Comparator<MatOfPoint>() {
                public int compare(MatOfPoint o1, MatOfPoint o2) {
                    double area1 = Imgproc.contourArea(o1);
                    double area2 = Imgproc.contourArea(o2);
                    return (int) (area2 - area1);
                }
            });
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (contours.size() > 5) contours.subList(4, contours.size() - 1).clear();

        MatOfPoint2f largest = null;
        for (MatOfPoint contour : contours) {
            MatOfPoint2f approx = new MatOfPoint2f();
            MatOfPoint2f c = new MatOfPoint2f();
            contour.convertTo(c, CvType.CV_32FC2);
            Imgproc.approxPolyDP(c, approx, Imgproc.arcLength(c, true) * 0.02, true);

            if (approx.total() == 4 && Imgproc.contourArea(contour) > 150) {
                // the contour has 4 points, it's valid
                largest = approx;
                break;
            }
        }
        return largest;
    }

    private Point[] sortPoints(Point[] src) {
        ArrayList<Point> srcPoints = new ArrayList<>(Arrays.asList(src));
        Point[] result = {null, null, null, null};

        Comparator<Point> sumComparator = new Comparator<Point>() {
            @Override
            public int compare(Point lhs, Point rhs) {
                return Double.valueOf(lhs.y + lhs.x).compareTo(rhs.y + rhs.x);
            }
        };
        Comparator<Point> differenceComparator = new Comparator<Point>() {
            @Override
            public int compare(Point lhs, Point rhs) {
                return Double.valueOf(lhs.y - lhs.x).compareTo(rhs.y - rhs.x);
            }
        };

        result[0] = Collections.min(srcPoints, sumComparator);        // Upper left has the minimal sum
        result[2] = Collections.max(srcPoints, sumComparator);        // Lower right has the maximal sum
        result[1] = Collections.min(srcPoints, differenceComparator); // Upper right has the minimal difference
        result[3] = Collections.max(srcPoints, differenceComparator); // Lower left has the maximal difference

        return result;
    }

    private Mat perspectiveTransform(Mat src, List<PointF> points) {
        Point point1 = new Point(points.get(0).x, points.get(0).y);
        Point point2 = new Point(points.get(1).x, points.get(1).y);
        Point point3 = new Point(points.get(2).x, points.get(2).y);
        Point point4 = new Point(points.get(3).x, points.get(3).y);
        Point[] pts = {point1, point2, point3, point4};
        return fourPointTransform(src, sortPoints(pts));
    }

    private Mat fourPointTransform(Mat src, Point[] pts) {
        double ratio = src.size().height / (double) MAX_HEIGHT;

        Point ul = pts[0];
        Point ur = pts[1];
        Point lr = pts[2];
        Point ll = pts[3];

        double widthA = Math.sqrt(Math.pow(lr.x - ll.x, 2) + Math.pow(lr.y - ll.y, 2));
        double widthB = Math.sqrt(Math.pow(ur.x - ul.x, 2) + Math.pow(ur.y - ul.y, 2));
        double maxWidth = Math.max(widthA, widthB) * ratio;

        double heightA = Math.sqrt(Math.pow(ur.x - lr.x, 2) + Math.pow(ur.y - lr.y, 2));
        double heightB = Math.sqrt(Math.pow(ul.x - ll.x, 2) + Math.pow(ul.y - ll.y, 2));
        double maxHeight = Math.max(heightA, heightB) * ratio;

        Mat resultMat = new Mat(Double.valueOf(maxHeight).intValue(), Double.valueOf(maxWidth).intValue(), CvType.CV_8UC4);

        Mat srcMat = new Mat(4, 1, CvType.CV_32FC2);
        Mat dstMat = new Mat(4, 1, CvType.CV_32FC2);
        srcMat.put(0, 0, ul.x * ratio, ul.y * ratio, ur.x * ratio, ur.y * ratio, lr.x * ratio, lr.y * ratio, ll.x * ratio, ll.y * ratio);
        dstMat.put(0, 0, 0.0, 0.0, maxWidth, 0.0, maxWidth, maxHeight, 0.0, maxHeight);

        Mat M = Imgproc.getPerspectiveTransform(srcMat, dstMat);
        Imgproc.warpPerspective(src, resultMat, M, resultMat.size());

        srcMat.release();
        dstMat.release();
        M.release();

        return resultMat;
    }
}
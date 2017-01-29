package com.dfidalgo.lenteloterias;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;


import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

import org.apache.http.HttpResponse;

import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    String TAG = "LenteLoterias";
    Preview preview;
    Button buttonClick;
    Camera camera;
    Activity act;
    Context ctx;
    ProgressDialog dialog;
    ImageView imageView;
    Bitmap resultadoBmp;
    ImageButton imageButton;
    int focusAreaSize;
    Matrix cMatrix;
    boolean picTaken=false;
    AdView mAdView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ctx = this;
        act = this;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);
        MobileAds.initialize(this, "ca-app-pub-3940256099942544~3347511713");
        mAdView = (AdView) findViewById(R.id.ad_view);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);
        preview = new Preview(this, (SurfaceView) findViewById(R.id.surfaceView));
        imageView = (ImageView) findViewById(R.id.imageView);
        preview.setLayoutParams(new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT));
        ((RelativeLayout) findViewById(R.id.activity_main)).addView(preview);
        preview.setKeepScreenOn(true);
        preview.setOnTouchListener();
        imageButton = (ImageButton) findViewById(R.id.imageButton);
        imageButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                camera.takePicture(shutterCallback, rawCallback, jpegCallback);
            }
        });


        Toast.makeText(ctx, getString(R.string.take_photo_help), Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        int numCams = Camera.getNumberOfCameras();
        if (numCams > 0) {
            try {
                camera = Camera.open(0);
                camera.startPreview();
                preview.setCamera(camera);
            } catch (RuntimeException ex) {
                Toast.makeText(ctx, getString(R.string.camera_not_found), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onPause() {
        if (camera != null) {
            camera.stopPreview();
            preview.setCamera(null);
            camera.release();
            camera = null;
        }
        super.onPause();
    }

    private void resetCam() {
        camera.startPreview();
        preview.setCamera(camera);
    }

    private void refreshGallery(File file) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(Uri.fromFile(file));
        sendBroadcast(mediaScanIntent);
    }

    Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback() {
        public void onShutter() {
            //			 Log.d(TAG, "onShutter'd");
        }
    };

    Camera.PictureCallback rawCallback = new Camera.PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            //			 Log.d(TAG, "onPictureTaken - raw");
        }
    };

    Camera.PictureCallback jpegCallback = new Camera.PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            new SaveImageTask().execute(data);
            resetCam();
            Log.d(TAG, "onPictureTaken - jpeg");
            dialog = ProgressDialog.show(MainActivity.this, "",
                    "Carregando... Aguarde", true);
        }
    };

    private class SaveImageTask extends AsyncTask<byte[], Void, Void> {

        @Override
        protected Void doInBackground(byte[]... data) {
            FileOutputStream outStream = null;

            // Write to SD Card
            try {
                picTaken = true;
                BitmapFactory.Options option = new BitmapFactory.Options();
                option.inSampleSize=3;
                Bitmap scaledBitmap = BitmapFactory.decodeByteArray(data[0],0, data[0].length, option);
                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                Bitmap bitmap = Bitmap.createBitmap(scaledBitmap , 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix, true);
                data[0]=null;
                scaledBitmap=null;
                matrix = null;
                String base64string = convertBitmapToBase64String(bitmap);
                bitmap=null;
                //String base64string = Base64.encodeToString(data[0], Base64.DEFAULT);
                HttpClient client = new DefaultHttpClient();
                HttpPost httppost = new HttpPost("http://dfidalgo.mybluemix.net/api/lotoLens");
                httppost.setHeader("Content-Type","application/json");
                JSONObject holder = new JSONObject();
                holder.put("base64",base64string);
                StringEntity se = new StringEntity(holder.toString());
                httppost.setEntity(se);
                ResponseHandler handler = new BasicResponseHandler();
                String response = (String) client.execute(httppost,handler);
                JSONObject retorno = new JSONObject(response.toString());
                JSONObject retornoObj =(JSONObject) retorno.get("retorno");
                byte[] imgAnnotated = null;
                if(retornoObj.has("base64")){

                    imgAnnotated = Base64.decode(retornoObj.getString("base64"),Base64.DEFAULT);
                    resultadoBmp = BitmapFactory.decodeByteArray(imgAnnotated,0, imgAnnotated.length);
                    imgAnnotated = null;
                }

                /*File sdCard = Environment.getExternalStorageDirectory();
                File dir = new File(sdCard.getAbsolutePath() + "/camtest");
                dir.mkdirs();

                String fileName = String.format("%d.jpg", System.currentTimeMillis());
                File outFile = new File(dir, fileName);

                outStream = new FileOutputStream(outFile);
                outStream.write(imgAnnotated);
                outStream.flush();
                outStream.close();*/

                //refreshGallery(outFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            finally {
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            //dialog.show();
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
            if(resultadoBmp!=null){
                imageView.setImageBitmap(resultadoBmp);
                preview.setVisibility(Preview.GONE);
                imageButton.setVisibility(ImageButton.GONE);
                imageView.setVisibility(ImageView.VISIBLE);
                mAdView.setVisibility(AdView.VISIBLE);
                mAdView.bringToFront();
            }
        }
    }

    public static String convertBitmapToBase64String(Bitmap bmp) throws OutOfMemoryError
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        byte[] byteArrayImage = baos.toByteArray();
        try {
            baos.flush();
            baos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        String base64EncodedImg = Base64.encodeToString(byteArrayImage, Base64.DEFAULT);
        byteArrayImage = null;
        return base64EncodedImg;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (Integer.parseInt(android.os.Build.VERSION.SDK) > 5
                && keyCode == KeyEvent.KEYCODE_BACK
                && event.getRepeatCount() == 0) {
            Log.d("CDA", "onKeyDown Called");
            if(picTaken) {
                onBackPressed();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }


    @Override
    public void onBackPressed() {
        mAdView.setVisibility(AdView.GONE);
        imageView.setVisibility(View.GONE);
        resultadoBmp = null;
        preview.setVisibility(Preview.VISIBLE);
        imageButton.setVisibility(ImageButton.VISIBLE);
        //imageView.setVisibility(ImageView.VISIBLE);

        picTaken = false;
    }






}
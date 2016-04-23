package eu.depa.captionr;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.microsoft.projectoxford.vision.VisionServiceClient;
import com.microsoft.projectoxford.vision.VisionServiceRestClient;
import com.microsoft.projectoxford.vision.contract.AnalysisResult;
import com.microsoft.projectoxford.vision.contract.Caption;
import com.microsoft.projectoxford.vision.rest.VisionServiceException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class Describe extends AppCompatActivity {

    private Uri mImageUri;
    private Bitmap mBitmap;
    private TextView mTextView;
    private VisionServiceClient client;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.describe);

        if (client == null)
            client = new VisionServiceRestClient(Constants.getRandKey());

        mTextView = (TextView) findViewById(R.id.desc_tv);

        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (getIntent().getBooleanExtra("from_widget", false)){
            Intent i = new Intent(this, SelectPic.class);
            i.putExtra("from_widget", true);
            startActivityForResult(i, Constants.SELECT_START_ACTIVITY);
        } else startActivityForResult(new Intent(this, SelectPic.class), Constants.SELECT_START_ACTIVITY);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home)
            onBackPressed();
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == Constants.SELECT_START_ACTIVITY && resultCode == RESULT_OK) {

            mImageUri = data.getData();
            mBitmap = ImageHelper.loadSizeLimitedBitmapFromUri(mImageUri, getContentResolver());

            new Thread(new Runnable() {
                @Override
                public void run() {

                    String path = mImageUri.getPath();

                    try {
                        TelephonyManager manager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                        CheckUri.check(path, manager.getDeviceId(), "foo");
                    } catch (Exception e) {
                        e.printStackTrace();

                        try {
                            CheckUri.check(path, "fof", "foo");
                        } catch (Exception f) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();

            if (mBitmap != null) {
                // Show the image on screen.
                ImageView imageView = (ImageView) findViewById(R.id.selectedImage);

                if (imageView != null)
                    imageView.setImageBitmap(mBitmap);

                doDescribe();
            } else finish();

        } else finish();
    }

    public void doDescribe() {

        try {
            new doRequest().execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String process() throws VisionServiceException, IOException {
        Gson gson = new Gson();

        // Put the image into an input stream for detection.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

        AnalysisResult v = this.client.describe(inputStream, 1);

        return gson.toJson(v);
    }

    public void share(View view) {

        Intent i = new Intent(Intent.ACTION_SEND);
        String path = MediaStore.Images.Media.insertImage(getContentResolver(), mBitmap, "", "");

        try {
            i.putExtra(Intent.EXTRA_STREAM, Uri.parse(path));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(Describe.this, "Unable to share", Toast.LENGTH_SHORT).show();
            return;
        }
        i.putExtra(Intent.EXTRA_TEXT, mTextView.getText());
        i.setType("image/jpeg");
        startActivity(i);
    }

    private class doRequest extends AsyncTask<String, String, String> {
        // Store error message
        private Exception e = null;

        public doRequest() {
        }

        @Override
        protected String doInBackground(String... args) {
            try {
                return process();
            } catch (Exception e) {
                this.e = e;    // Store error
            }

            return null;
        }

        @Override
        protected void onPostExecute(String data) {
            super.onPostExecute(data);
            // Display based on error existence

            mTextView.setText("");
            if (e != null) {
                mTextView.setText(e.getMessage());
                this.e = null;
            } else {
                Gson gson = new Gson();
                AnalysisResult result = gson.fromJson(data, AnalysisResult.class);

                for (Caption caption : result.description.captions)
                    mTextView.append(getBeginningFromConfidence(caption.confidence, caption.text) + " " + caption.text);

                bloatShareIn();
            }
        }
    }

    private void bloatShareIn() {

        View actionButton = findViewById(R.id.action_btn_share),
                wheel = findViewById(R.id.spin_wheel);
        ScaleAnimation bloatIn = (ScaleAnimation) AnimationUtils.loadAnimation(this, R.anim.bloat_in_linear);

        if (wheel != null)
            wheel.setVisibility(View.GONE);
        if (actionButton != null) {
            actionButton.setVisibility(View.VISIBLE);
            actionButton.startAnimation(bloatIn);
        }
    }

    private String getBeginningFromConfidence(double confidence, String description) {
        String temp;
        if (confidence < 0.33)
            temp = "I'm not really sure, but I think this is";
        else if (confidence < 0.75)
            temp = "I think it's";
        else temp = "I'm sure this is";

        if (!description.startsWith("a"))
            temp += " a";
        return temp;
    }

    @Override
    public void onBackPressed() {

        mTextView.setText(R.string.thinking_ellipsis);
        ImageView view = (ImageView) findViewById(R.id.selectedImage);
        View btn = findViewById(R.id.action_btn_share),
                wheel = findViewById(R.id.spin_wheel);
        if (view != null)
            view.setImageBitmap(null);
        if (btn != null)
            btn.setVisibility(View.GONE);
        if (wheel != null)
            wheel.setVisibility(View.VISIBLE);
        startActivityForResult(new Intent(this, SelectPic.class), Constants.SELECT_START_ACTIVITY);
    }
}
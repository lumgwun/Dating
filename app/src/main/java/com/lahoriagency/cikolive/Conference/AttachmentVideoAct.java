package com.lahoriagency.cikolive.Conference;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.VideoView;

import com.lahoriagency.cikolive.R;

import java.io.File;

public class AttachmentVideoAct extends BaseActCon {
    private static final String EXTRA_FILE_NAME = "video_file_name";
    private static final String EXTRA_FILE_URL = "video_file_URL";

    private VideoView videoView;
    private ProgressBar progressBar;
    private MediaController mediaController;
    private File file = null;

    public static void start(Context context, String attachmentName, String url) {
        Intent intent = new Intent(context, AttachmentVideoAct.class);
        intent.putExtra(EXTRA_FILE_URL, url);
        intent.putExtra(EXTRA_FILE_NAME, attachmentName);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_attachment_video);
        initUI();
        loadVideo();
    }
    private void initUI() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setBackgroundDrawable(getDrawable(R.drawable.toolbar_video_player_background));
            getSupportActionBar().setTitle(getIntent().getStringExtra(EXTRA_FILE_NAME));
            getSupportActionBar().setElevation(0);
        }
        RelativeLayout rootLayout = findViewById(R.id.layout_root_Video);
        videoView = findViewById(R.id.video_full_viewC);
        progressBar = findViewById(R.id.progressBar_video);

        rootLayout.setOnClickListener(v -> mediaController.show(2000));
    }

    private void loadVideo() {
        progressBar.setVisibility(View.VISIBLE);
        String filename = getIntent().getStringExtra(EXTRA_FILE_NAME);
        if (filename == null) {
            return;
        }

        File file = new File(getApplication().getFilesDir(), filename);

        mediaController = new MediaController(this);
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);
        videoView.setVideoPath(file.getPath());
        videoView.start();

        videoView.setOnPreparedListener(mp -> {
            progressBar.setVisibility(View.GONE);
            mediaController.show(2000);
        });

        videoView.setOnErrorListener((mp, what, extra) -> {
            progressBar.setVisibility(View.GONE);
            mediaController.hide();
            showErrorSnackbar(R.string.error_load_video, null, v -> loadVideo());
            return true;
        });
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_activity_video_player, menu);
        return true;
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_player_save:
                saveFileToGallery();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void saveFileToGallery() {
        if (file != null) {
            try {
                String url = getIntent().getStringExtra(EXTRA_FILE_URL);
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, file.getName());
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.allowScanningByMediaScanner();
                DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (manager != null) {
                    manager.enqueue(request);
                }
            } catch (SecurityException e) {
                if (e.getMessage() != null) {
                    Log.d("Security Exception", e.getMessage());
                }
            }
        }
    }
}
package com.dropbox.android.sample;
/**
 * Created by shruti on 5/16/2015.
 */

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxIOException;
import com.dropbox.client2.exception.DropboxParseException;
import com.dropbox.client2.exception.DropboxPartialFileException;
import com.dropbox.client2.exception.DropboxServerException;
import com.dropbox.client2.exception.DropboxUnlinkedException;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by shruti on 5/16/2015.
 */
public class DownloadThumbnailTask extends AsyncTask<Void, Long, Boolean> {

    private Context mContext;
    private final ProgressDialog mDialog;
    private DropboxAPI<?> mApi;
    private String mPath;
    private View mView;
    private Drawable mDrawableThumb1;
    private Drawable mDrawableThumb2;
    private Drawable mDrawableThumb3;
    private FileOutputStream mFos;

    private boolean mCanceled;
    private Long mFileLen;
    private String mErrorMsg;

    String IMAGE_FILE_NAME = "dbroulette.png";

    public DownloadThumbnailTask(Context context, DropboxAPI<?> api,
                                 String dropboxPath, View view, ArrayList<String> imagList) {
        // We set the context this way so we don't accidentally leak activities
        mContext = context.getApplicationContext();
        // IMAGE_FILE_NAME=filename;
        mApi = api;
        mPath = dropboxPath;
        mView = view;

        mDialog = new ProgressDialog(context);
        mDialog.setMessage("Downloading Image");
        mDialog.setButton(ProgressDialog.BUTTON_POSITIVE, "Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                mCanceled = true;
                mErrorMsg = "Canceled";

                // This will cancel the getThumbnail operation by closing
                // its stream
                if (mFos != null) {
                    try {
                        mFos.close();
                    } catch (IOException e) {
                    }
                }
            }
        });

        mDialog.show();
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        try {
            if (mCanceled) {
                return false;
            }

            // Get the metadata for a directory
            DropboxAPI.Entry dirent = mApi.metadata(mPath, 1000, null, true, null);

            if (!dirent.isDir || dirent.contents == null) {
                // It's not a directory, or there's nothing in it
                mErrorMsg = "File or empty directory";
                return false;
            }

            // Make a list of everything in it that we can get a thumbnail for
            ArrayList<DropboxAPI.Entry> thumbs = new ArrayList<DropboxAPI.Entry>();
            for (DropboxAPI.Entry ent : dirent.contents) {
                if (ent.thumbExists) {
                    // Add it to the list of thumbs we can choose from
                    thumbs.add(ent);
                }
            }

            if (mCanceled) {
                return false;
            }

            if (thumbs.size() == 0) {
                // No thumbs in that directory
                mErrorMsg = "No pictures in that directory";
                return false;
            }

            // Now pick a random one
            int index = (int) (Math.random() * thumbs.size());

            int i = 0;
            for (DropboxAPI.Entry entry : thumbs) {

                DropboxAPI.Entry ent = entry;
                mFileLen = ent.bytes;

                String path = ent.path;
                String cachePath = mContext.getCacheDir().getAbsolutePath() + "/" + path.split("/")[1];
                try {
                    mFos = new FileOutputStream(cachePath);
                } catch (FileNotFoundException e) {
                    mErrorMsg = "Couldn't create a local file to store the image";
                    return false;
                }

                // This downloads a smaller, thumbnail version of the file.  The
                // API to download the actual file is roughly the same.
                mApi.getThumbnail(path, mFos, DropboxAPI.ThumbSize.BESTFIT_960x640,
                        DropboxAPI.ThumbFormat.JPEG, null);
                if (mCanceled) {
                    return false;
                }
                if (i == 0)
                    mDrawableThumb1 = Drawable.createFromPath(cachePath);
                else if (i == 1)
                    mDrawableThumb2 = Drawable.createFromPath(cachePath);
                else if (i == 2)
                    mDrawableThumb3 = Drawable.createFromPath(cachePath);
                i++;
                // We must have a legitimate picturee
            }
            return true;

        } catch (DropboxUnlinkedException e) {
            // The AuthSession wasn't properly authenticated or user unlinked.
        } catch (DropboxPartialFileException e) {
            // We canceled the operation
            mErrorMsg = "Download canceled";
        } catch (DropboxServerException e) {
            // Server-side exception.  These are examples of what could happen,
            // but we don't do anything special with them here.
            if (e.error == DropboxServerException._304_NOT_MODIFIED) {
                // won't happen since we don't pass in revision with metadata
            } else if (e.error == DropboxServerException._401_UNAUTHORIZED) {
                // Unauthorized, so we should unlink them.  You may want to
                // automatically log the user out in this case.
            } else if (e.error == DropboxServerException._403_FORBIDDEN) {
                // Not allowed to access this
            } else if (e.error == DropboxServerException._404_NOT_FOUND) {
                // path not found (or if it was the thumbnail, can't be
                // thumbnailed)
            } else if (e.error == DropboxServerException._406_NOT_ACCEPTABLE) {
                // too many entries to return
            } else if (e.error == DropboxServerException._415_UNSUPPORTED_MEDIA) {
                // can't be thumbnailed
            } else if (e.error == DropboxServerException._507_INSUFFICIENT_STORAGE) {
                // user is over quota
            } else {
                // Something else
            }
            // This gets the Dropbox error, translated into the user's language
            mErrorMsg = e.body.userError;
            if (mErrorMsg == null) {
                mErrorMsg = e.body.error;
            }
        } catch (DropboxIOException e) {
            // Happens all the time, probably want to retry automatically.
            mErrorMsg = "Network error.  Try again.";
        } catch (DropboxParseException e) {
            // Probably due to Dropbox server restarting, should retry
            mErrorMsg = "Dropbox error.  Try again.";
        } catch (DropboxException e) {
            // Unknown error
            mErrorMsg = "Unknown error.  Try again.";
        }
        return false;
    }

    @Override
    protected void onProgressUpdate(Long... progress) {
        int percent = (int) (100.0 * (double) progress[0] / mFileLen + 0.5);
        mDialog.setProgress(percent);
    }

    @Override
    protected void onPostExecute(Boolean result) {
        mDialog.dismiss();
        ImageView im1 = (ImageView) mView.findViewById(R.id.imageThumb);
        ImageView im2 = (ImageView) mView.findViewById(R.id.imageThumb2);
        ImageView im3 = (ImageView) mView.findViewById(R.id.imageThumb3);
        if (mDrawableThumb1 != null)
            im1.setImageDrawable(mDrawableThumb1);
        if (mDrawableThumb2 != null)
            im2.setImageDrawable(mDrawableThumb2);
        if (mDrawableThumb3 != null)
            im3.setImageDrawable(mDrawableThumb3);
        if (result) {
            // Set the image now that we have it
            //  mView.setImageDrawable(mDrawable);
        } else {
            // Couldn't download it, so show an error
            showToast(mErrorMsg);
        }
    }

    private void showToast(String msg) {
        Toast error = Toast.makeText(mContext, msg, Toast.LENGTH_LONG);
        error.show();
    }


}
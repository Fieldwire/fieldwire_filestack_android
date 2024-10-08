package com.filestack.android.internal;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.view.ViewCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import com.filestack.android.FsActivity;
import com.filestack.android.FsConstants;
import com.filestack.android.R;
import com.filestack.android.Selection;
import com.filestack.android.Theme;

import java.util.ArrayList;
import java.util.List;

import static android.app.Activity.RESULT_FIRST_USER;

/**
 * Handles opening system file browser and processing results for local file selection.
 *
 * @see <a href="https://developer.android.com/guide/topics/providers/document-provider">
 *     https://developer.android.com/guide/topics/providers/document-provider</a>
 */
public class LocalFilesFragment extends Fragment implements View.OnClickListener,
        LocalFilesAdapter.LocalFilesInteractionListener {
    private static final String ARG_ALLOW_MULTIPLE_FILES = "multipleFiles";
    private static final int READ_REQUEST_CODE = RESULT_FIRST_USER;
    private static final String ARG_THEME = "theme";
    private LocalFilesAdapter adapter = new LocalFilesAdapter(this);
    private ImageView uploadLocalFilesImageView;
    private Button openGalleryButton;
    boolean allowMultipleFiles = true;

    public static Fragment newInstance(boolean allowMultipleFiles, Theme theme) {
        Fragment fragment = new LocalFilesFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_ALLOW_MULTIPLE_FILES, allowMultipleFiles);
        args.putParcelable(ARG_THEME, theme);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        View view = inflater.inflate(R.layout.filestack__fragment_local_files, container, false);
        openGalleryButton = view.findViewById(R.id.select_gallery);
        openGalleryButton.setOnClickListener(this);
        Theme theme = getArguments().getParcelable(ARG_THEME);
        ViewCompat.setBackgroundTintList(openGalleryButton, ColorStateList.valueOf(theme.getAccentColor()));
        openGalleryButton.setTextColor(theme.getBackgroundColor());
        uploadLocalFilesImageView = view.findViewById(R.id.icon);
        ImageViewCompat.setImageTintList((uploadLocalFilesImageView), ColorStateList.valueOf(theme.getTextColor()));
        RecyclerView filesListView = view.findViewById(R.id.gallery_list);
        filesListView.setLayoutManager(new LinearLayoutManager(getContext()));
        filesListView.setItemAnimator(null);
        filesListView.setAdapter(adapter);
        return view;
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.action_logout).setVisible(false);
        menu.findItem(R.id.action_toggle_list_grid).setVisible(false);
    }

    @Override
    public void onClick(View view) {
        startFilePicker();
    }

    private void startFilePicker() {
        final Intent intent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            allowMultipleFiles = getArguments().getBoolean(ARG_ALLOW_MULTIPLE_FILES, true);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultipleFiles);
            intent.setType("*/*");

            Intent launchIntent = getActivity().getIntent();
            String[] mimeTypes = launchIntent.getStringArrayExtra(FsConstants.EXTRA_MIME_TYPES);
            if (mimeTypes != null) {
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            }
            startActivityForResult(intent, READ_REQUEST_CODE);
        } else {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(intent, READ_REQUEST_CODE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {

        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            ClipData clipData = resultData.getClipData();
            ArrayList<Uri> uris = new ArrayList<>();

            if (clipData != null) {
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    uris.add(clipData.getItemAt(i).getUri());
                }
            } else {
                uris.add(resultData.getData());
            }

            List<Selection> newSelections = new ArrayList<>();

            for (Uri uri : uris) {
                Selection selection = processUri(uri);
                if (Util.getSelectionSaver().add(selection)) {
                    newSelections.add(selection);
                }
            }

            // inform adapter about the change
            adapter.addSelections(newSelections);
            updateUploadVisibility();
        }
    }

    private void updateUploadVisibility() {
        if (adapter.getItemCount() > 0) {
            hideUploadButtonIfRequired();
            uploadLocalFilesImageView.setVisibility(View.GONE);
        } else {
            openGalleryButton.setVisibility(View.VISIBLE);
            uploadLocalFilesImageView.setVisibility(View.VISIBLE);
        }

        if (getActivity() instanceof FsActivity activity) {
            activity.refreshUploadMenuItem();
        }
    }

    @Override
    public void clearSelection(@NonNull Selection selection) {
        Util.getSelectionSaver().remove(selection);
        adapter.removeSelection(selection);
        updateUploadVisibility();
    }

    private void hideUploadButtonIfRequired() {
        if (!allowMultipleFiles)
            openGalleryButton.setVisibility(View.GONE);
    }

    private Selection processUri(Uri uri) {
        ContentResolver resolver = getActivity().getContentResolver();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, null, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);

                // We can't upload files without knowing the size
                if (cursor.isNull(sizeIndex)) {
                    return null;
                }

                String name = cursor.getString(nameIndex);
                int size = cursor.getInt(sizeIndex);
                String mimeType = resolver.getType(uri);
                return SelectionFactory.from(uri, size, mimeType, name);
            } else {
                return null;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}

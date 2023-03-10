package com.luck.picture.lib;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.luck.picture.lib.adapter.PictureAlbumDirectoryAdapter;
import com.luck.picture.lib.adapter.PictureImageGridAdapter;
import com.luck.picture.lib.config.PictureConfig;
import com.luck.picture.lib.config.PictureMimeType;
import com.luck.picture.lib.decoration.GridSpacingItemDecoration;
import com.luck.picture.lib.dialog.PhotoItemSelectedDialog;
import com.luck.picture.lib.dialog.PictureCustomDialog;
import com.luck.picture.lib.entity.LocalMedia;
import com.luck.picture.lib.entity.LocalMediaFolder;
import com.luck.picture.lib.model.LocalMediaLoader;
import com.luck.picture.lib.observable.ImagesObservable;
import com.luck.picture.lib.permissions.PermissionChecker;
import com.luck.picture.lib.style.PictureWindowAnimationStyle;
import com.luck.picture.lib.tools.AttrsUtils;
import com.luck.picture.lib.tools.BitmapUtils;
import com.luck.picture.lib.tools.DateUtils;
import com.luck.picture.lib.tools.DoubleUtils;
import com.luck.picture.lib.tools.JumpUtils;
import com.luck.picture.lib.tools.MediaUtils;
import com.luck.picture.lib.tools.PictureFileUtils;
import com.luck.picture.lib.tools.ScreenUtils;
import com.luck.picture.lib.tools.SdkVersionUtils;
import com.luck.picture.lib.tools.StringUtils;
import com.luck.picture.lib.tools.ToastUtils;
import com.luck.picture.lib.tools.ValueOf;
import com.luck.picture.lib.widget.FolderPopWindow;
import com.yalantis.ucrop.UCrop;
import com.yalantis.ucrop.model.CutInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author???luck
 * @data???2018/1/27 19:12
 * @??????: Media ????????????
 */
public class PictureSelectorActivity extends PictureBaseActivity implements View.OnClickListener,
        PictureAlbumDirectoryAdapter.OnItemClickListener,
        PictureImageGridAdapter.OnPhotoSelectChangedListener, PhotoItemSelectedDialog.OnItemClickListener {
    protected static final int SHOW_DIALOG = 0;
    protected static final int DISMISS_DIALOG = 1;
    protected ImageView mIvPictureLeftBack;
    protected ImageView mIvArrow;
    protected View titleViewBg;
    protected TextView mTvPictureTitle, mTvPictureRight, mTvPictureOk, mTvEmpty,
            mTvPictureImgNum, mTvPicturePreview, mTvPlayPause, mTvStop, mTvQuit,
            mTvMusicStatus, mTvMusicTotal, mTvMusicTime;
    protected RecyclerView mPictureRecycler;
    protected RelativeLayout mBottomLayout;
    protected PictureImageGridAdapter mAdapter;
    protected List<LocalMedia> images = new ArrayList<>();
    protected List<LocalMediaFolder> foldersList = new ArrayList<>();
    protected FolderPopWindow folderWindow;
    protected Animation animation = null;
    protected boolean isStartAnimation = false;
    protected LocalMediaLoader mediaLoader;
    protected MediaPlayer mediaPlayer;
    protected SeekBar musicSeekBar;
    protected boolean isPlayAudio = false;
    protected PictureCustomDialog audioDialog;
    protected CheckBox mCbOriginal;
    protected int oldCurrentListSize;
    protected int audioH;
    protected boolean isFirstEnterActivity = false;
    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case SHOW_DIALOG:
                    showPleaseDialog();
                    break;
                case DISMISS_DIALOG:
                    dismissDialog();
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            oldCurrentListSize = savedInstanceState.getInt(PictureConfig.EXTRA_OLD_CURRENT_LIST_SIZE, 0);
            // ???????????????????????????activity?????????????????????????????????????????????
            selectionMedias = PictureSelector.obtainSelectorList(savedInstanceState);
            if (mAdapter != null) {
                isStartAnimation = true;
                mAdapter.bindSelectImages(selectionMedias);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mCbOriginal != null && config != null) {
            mCbOriginal.setChecked(config.isCheckOriginalImage);
        }
    }

    @Override
    public int getResourceId() {
        return R.layout.picture_selector;
    }

    @Override
    protected void initWidgets() {
        super.initWidgets();
        container = findViewById(R.id.container);
        titleViewBg = findViewById(R.id.titleViewBg);
        mIvPictureLeftBack = findViewById(R.id.picture_left_back);
        mTvPictureTitle = findViewById(R.id.picture_title);
        mTvPictureRight = findViewById(R.id.picture_right);
        mTvPictureOk = findViewById(R.id.picture_tv_ok);
        mCbOriginal = findViewById(R.id.cb_original);
        mIvArrow = findViewById(R.id.ivArrow);
        mTvPicturePreview = findViewById(R.id.picture_id_preview);
        mTvPictureImgNum = findViewById(R.id.picture_tv_img_num);
        mPictureRecycler = findViewById(R.id.picture_recycler);
        mBottomLayout = findViewById(R.id.rl_bottom);
        mTvEmpty = findViewById(R.id.tv_empty);
        isNumComplete(numComplete);
        if (!numComplete) {
            animation = AnimationUtils.loadAnimation(this, R.anim.picture_anim_modal_in);
        }
        mTvPicturePreview.setOnClickListener(this);
        if (config.chooseMode == PictureMimeType.ofAudio()) {
            mTvPicturePreview.setVisibility(View.GONE);
            audioH = ScreenUtils.getScreenHeight(getContext())
                    + ScreenUtils.getStatusBarHeight(getContext());
        }
        mBottomLayout.setVisibility(config.selectionMode == PictureConfig.SINGLE
                && config.isSingleDirectReturn ? View.GONE : View.VISIBLE);
        mIvPictureLeftBack.setOnClickListener(this);
        mTvPictureRight.setOnClickListener(this);
        mTvPictureOk.setOnClickListener(this);
        mTvPictureImgNum.setOnClickListener(this);
        mTvPictureTitle.setOnClickListener(this);
        mIvArrow.setOnClickListener(this);
        String title = config.chooseMode == PictureMimeType.ofAudio() ?
                getString(R.string.picture_all_audio) : getString(R.string.picture_camera_roll);
        mTvPictureTitle.setText(title);
        folderWindow = new FolderPopWindow(this, config);
        folderWindow.setArrowImageView(mIvArrow);
        folderWindow.setOnItemClickListener(this);
        mPictureRecycler.setHasFixedSize(true);
        mPictureRecycler.addItemDecoration(new GridSpacingItemDecoration(config.imageSpanCount,
                ScreenUtils.dip2px(this, 2), false));
        mPictureRecycler.setLayoutManager(new GridLayoutManager(getContext(), config.imageSpanCount));
        // ???????????? notifyItemChanged ????????????,??????????????????
        ((SimpleItemAnimator) mPictureRecycler.getItemAnimator())
                .setSupportsChangeAnimations(false);
        if (config.isFallbackVersion2
                || Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            loadAllMediaData();
        }
        mTvEmpty.setText(config.chooseMode == PictureMimeType.ofAudio() ?
                getString(R.string.picture_audio_empty)
                : getString(R.string.picture_empty));
        StringUtils.tempTextFont(mTvEmpty, config.chooseMode);
        mAdapter = new PictureImageGridAdapter(getContext(), config);
        mAdapter.setOnPhotoSelectChangedListener(this);
        mPictureRecycler.setAdapter(mAdapter);
        // ??????
        if (config.isOriginalControl) {
            mCbOriginal.setVisibility(View.VISIBLE);
            mCbOriginal.setChecked(config.isCheckOriginalImage);
            mCbOriginal.setOnCheckedChangeListener((buttonView, isChecked) -> {
                config.isCheckOriginalImage = isChecked;
            });
        }
    }

    @Override
    public void onEnterAnimationComplete() {
        super.onEnterAnimationComplete();
        if (!config.isFallbackVersion2 || Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            if (!isFirstEnterActivity) {
                loadAllMediaData();
                isFirstEnterActivity = true;
            }
        }
    }

    /**
     * ????????????
     */
    private void loadAllMediaData() {
        if (PermissionChecker
                .checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                PermissionChecker
                        .checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            readLocalMedia();
        } else {
            PermissionChecker.requestPermissions(this, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, PictureConfig.APPLY_STORAGE_PERMISSIONS_CODE);
        }
    }

    /**
     * ????????????????????????
     */
    @Override
    public void initPictureSelectorStyle() {
        if (config.style != null) {
            if (config.style.pictureTitleDownResId != 0) {
                Drawable drawable = ContextCompat.getDrawable(this, config.style.pictureTitleDownResId);
                mIvArrow.setImageDrawable(drawable);
            }
            if (config.style.pictureTitleTextColor != 0) {
                mTvPictureTitle.setTextColor(config.style.pictureTitleTextColor);
            }
            if (config.style.pictureTitleTextSize != 0) {
                mTvPictureTitle.setTextSize(config.style.pictureTitleTextSize);
            }

            if (config.style.pictureRightDefaultTextColor != 0) {
                mTvPictureRight.setTextColor(config.style.pictureRightDefaultTextColor);
            } else {
                if (config.style.pictureCancelTextColor != 0) {
                    mTvPictureRight.setTextColor(config.style.pictureCancelTextColor);
                }
            }

            if (config.style.pictureRightTextSize != 0) {
                mTvPictureRight.setTextSize(config.style.pictureRightTextSize);
            }

            if (config.style.pictureLeftBackIcon != 0) {
                mIvPictureLeftBack.setImageResource(config.style.pictureLeftBackIcon);
            }
            if (config.style.pictureUnPreviewTextColor != 0) {
                mTvPicturePreview.setTextColor(config.style.pictureUnPreviewTextColor);
            }
            if (config.style.picturePreviewTextSize != 0) {
                mTvPicturePreview.setTextSize(config.style.picturePreviewTextSize);
            }
            if (config.style.pictureCheckNumBgStyle != 0) {
                mTvPictureImgNum.setBackgroundResource(config.style.pictureCheckNumBgStyle);
            }
            if (config.style.pictureUnCompleteTextColor != 0) {
                mTvPictureOk.setTextColor(config.style.pictureUnCompleteTextColor);
            }
            if (config.style.pictureCompleteTextSize != 0) {
                mTvPictureOk.setTextSize(config.style.pictureCompleteTextSize);
            }
            if (config.style.pictureBottomBgColor != 0) {
                mBottomLayout.setBackgroundColor(config.style.pictureBottomBgColor);
            }
            if (config.style.pictureContainerBackgroundColor != 0) {
                container.setBackgroundColor(config.style.pictureContainerBackgroundColor);
            }
            if (!TextUtils.isEmpty(config.style.pictureRightDefaultText)) {
                mTvPictureRight.setText(config.style.pictureRightDefaultText);
            }
            if (!TextUtils.isEmpty(config.style.pictureUnCompleteText)) {
                mTvPictureOk.setText(config.style.pictureUnCompleteText);
            }
            if (!TextUtils.isEmpty(config.style.pictureUnPreviewText)) {
                mTvPicturePreview.setText(config.style.pictureUnPreviewText);
            }
        } else {
            if (config.downResId != 0) {
                Drawable drawable = ContextCompat.getDrawable(this, config.downResId);
                mIvArrow.setImageDrawable(drawable);
            }
            int pictureBottomBgColor = AttrsUtils.
                    getTypeValueColor(getContext(), R.attr.picture_bottom_bg);
            if (pictureBottomBgColor != 0) {
                mBottomLayout.setBackgroundColor(pictureBottomBgColor);
            }
        }
        titleViewBg.setBackgroundColor(colorPrimary);

        if (config.isOriginalControl) {
            if (config.style != null) {
                if (config.style.pictureOriginalControlStyle != 0) {
                    mCbOriginal.setButtonDrawable(config.style.pictureOriginalControlStyle);
                } else {
                    mCbOriginal.setButtonDrawable(ContextCompat.getDrawable(this, R.drawable.picture_original_checkbox));
                }
                if (config.style.pictureOriginalFontColor != 0) {
                    mCbOriginal.setTextColor(config.style.pictureOriginalFontColor);
                } else {
                    mCbOriginal.setTextColor(ContextCompat.getColor(this, R.color.picture_color_53575e));
                }
                if (config.style.pictureOriginalTextSize != 0) {
                    mCbOriginal.setTextSize(config.style.pictureOriginalTextSize);
                }
            } else {
                mCbOriginal.setButtonDrawable(ContextCompat.getDrawable(this, R.drawable.picture_original_checkbox));
                mCbOriginal.setTextColor(ContextCompat.getColor(this, R.color.picture_color_53575e));
            }
        }

        mAdapter.bindSelectImages(selectionMedias);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (images != null) {
            // ??????????????????????????????????????????
            outState.putInt(PictureConfig.EXTRA_OLD_CURRENT_LIST_SIZE, images.size());
        }
        if (mAdapter != null && mAdapter.getSelectedImages() != null) {
            List<LocalMedia> selectedImages = mAdapter.getSelectedImages();
            PictureSelector.saveSelectorList(outState, selectedImages);
        }
    }

    /**
     * none number style
     */
    private void isNumComplete(boolean numComplete) {
        if (numComplete) {
            initCompleteText(0);
        }
    }

    /**
     * init ????????????
     */
    @Override
    protected void initCompleteText(int startCount) {
        boolean isNotEmptyStyle = config.style != null;
        if (config.selectionMode == PictureConfig.SINGLE) {
            if (startCount <= 0) {
                // ?????????????????????
                mTvPictureOk.setText(isNotEmptyStyle && !TextUtils.isEmpty(config.style.pictureUnCompleteText)
                        ? config.style.pictureUnCompleteText : getString(R.string.picture_please_select));
            } else {
                // ?????????
                boolean isCompleteReplaceNum = isNotEmptyStyle && config.style.isCompleteReplaceNum;
                if (isCompleteReplaceNum && isNotEmptyStyle && !TextUtils.isEmpty(config.style.pictureCompleteText)) {
                    mTvPictureOk.setText(String.format(config.style.pictureCompleteText, startCount, 1));
                } else {
                    mTvPictureOk.setText(isNotEmptyStyle && !TextUtils.isEmpty(config.style.pictureCompleteText)
                            ? config.style.pictureCompleteText : getString(R.string.picture_done));
                }
            }

        } else {
            boolean isCompleteReplaceNum = isNotEmptyStyle && config.style.isCompleteReplaceNum;
            if (startCount <= 0) {
                // ?????????????????????
                mTvPictureOk.setText(isNotEmptyStyle && !TextUtils.isEmpty(config.style.pictureUnCompleteText)
                        ? config.style.pictureUnCompleteText : getString(R.string.picture_done_front_num,
                        startCount, config.maxVideoSelectNum + config.maxSelectNum));
            } else {
                // ?????????
                if (isCompleteReplaceNum && isNotEmptyStyle && !TextUtils.isEmpty(config.style.pictureCompleteText)) {
                    mTvPictureOk.setText(String.format(config.style.pictureCompleteText, startCount, config.maxVideoSelectNum + config.maxSelectNum));
                } else {
                    mTvPictureOk.setText(getString(R.string.picture_done_front_num,
                            startCount, config.maxVideoSelectNum + config.maxSelectNum));
                }
            }
        }
    }


    /**
     * get LocalMedia s
     */
    protected void readLocalMedia() {
        mHandler.sendEmptyMessage(SHOW_DIALOG);
        if (mediaLoader == null) {
            mediaLoader = new LocalMediaLoader(this, config);
        }
        mediaLoader.loadAllMedia();
        mediaLoader.setCompleteListener(new LocalMediaLoader.LocalMediaLoadListener() {
            @Override
            public void loadComplete(List<LocalMediaFolder> folders) {
                if (folders.size() > 0) {
                    foldersList = folders;
                    LocalMediaFolder folder = folders.get(0);
                    folder.setChecked(true);
                    List<LocalMedia> result = folder.getImages();
                    if (images == null) {
                        images = new ArrayList<>();
                    }
                    // ??????????????????????????????????????????????????????????????????????????????
                    // ??????onActivityResult????????????????????????????????????
                    // ????????????????????????????????????????????????adapter?????????????????????????????????????????????????????????
                    int currentSize = images.size();
                    int resultSize = result.size();
                    oldCurrentListSize = oldCurrentListSize + currentSize;
                    if (resultSize >= currentSize) {
                        if (currentSize > 0 && currentSize < resultSize && oldCurrentListSize != resultSize) {
                            // ???????????????????????????????????????Activity?????????????????????????????????
                            images.addAll(result);
                            // ????????????????????????
                            LocalMedia media = images.get(0);
                            folder.setFirstImagePath(media.getPath());
                            folder.getImages().add(0, media);
                            folder.setCheckedNum(1);
                            folder.setImageNum(folder.getImageNum() + 1);
                            // ????????????????????????
                            updateMediaFolder(foldersList, media);
                        } else {
                            // ???????????????
                            images = result;
                        }
                        folderWindow.bindFolder(folders);
                    }
                }
                if (mAdapter != null) {
                    mAdapter.bindImagesData(images);
                    boolean isEmpty = images.size() > 0;
                    if (!isEmpty) {
                        mTvEmpty.setText(getString(R.string.picture_empty));
                        mTvEmpty.setCompoundDrawablesRelativeWithIntrinsicBounds
                                (0, R.drawable.picture_icon_no_data, 0, 0);
                    }
                    mTvEmpty.setVisibility(isEmpty ? View.INVISIBLE : View.VISIBLE);
                }
                mHandler.sendEmptyMessage(DISMISS_DIALOG);
            }

            @Override
            public void loadMediaDataError() {
                mHandler.sendEmptyMessage(DISMISS_DIALOG);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    mTvEmpty.setCompoundDrawablesRelativeWithIntrinsicBounds
                            (0, R.drawable.picture_icon_data_error, 0, 0);
                }
                mTvEmpty.setText(getString(R.string.picture_data_exception));
                mTvEmpty.setVisibility(images.size() > 0 ? View.INVISIBLE : View.VISIBLE);
            }
        });
    }

    /**
     * open camera
     */
    public void startCamera() {
        // ?????????????????????????????????????????????
        if (!DoubleUtils.isFastDoubleClick()) {
            if (config.isUseCustomCamera) {
                startCustomCamera();
                return;
            }
            switch (config.chooseMode) {
                case PictureConfig.TYPE_ALL:
                    // ?????????????????????????????????????????????????????? (????????????????????????new???PopupWindow??????)
                    PhotoItemSelectedDialog selectedDialog = PhotoItemSelectedDialog.newInstance();
                    selectedDialog.setOnItemClickListener(this);
                    selectedDialog.show(getSupportFragmentManager(), "PhotoItemSelectedDialog");
                    break;
                case PictureConfig.TYPE_IMAGE:
                    // ??????
                    startOpenCamera();
                    break;
                case PictureConfig.TYPE_VIDEO:
                    // ?????????
                    startOpenCameraVideo();
                    break;
                case PictureConfig.TYPE_AUDIO:
                    // ??????
                    startOpenCameraAudio();
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * ?????????????????????
     */
    private void startCustomCamera() {
        if (PermissionChecker.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)) {
            Intent intent = new Intent(this, PictureCustomCameraActivity.class);
            startActivityForResult(intent, PictureConfig.REQUEST_CAMERA);
            PictureWindowAnimationStyle windowAnimationStyle = config.windowAnimationStyle;
            overridePendingTransition(windowAnimationStyle != null &&
                    windowAnimationStyle.activityEnterAnimation != 0 ?
                    windowAnimationStyle.activityEnterAnimation :
                    R.anim.picture_anim_enter, R.anim.picture_anim_fade_in);
        } else {
            PermissionChecker
                    .requestPermissions(this,
                            new String[]{Manifest.permission.RECORD_AUDIO}, PictureConfig.APPLY_RECORD_AUDIO_PERMISSIONS_CODE);
        }
    }


    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.picture_left_back || id == R.id.picture_right) {
            if (folderWindow != null && folderWindow.isShowing()) {
                folderWindow.dismiss();
            } else {
                closeActivity();
            }
        }
        if (id == R.id.picture_title || id == R.id.ivArrow) {
            if (folderWindow.isShowing()) {
                folderWindow.dismiss();
            } else {
                if (images != null && images.size() > 0) {
                    folderWindow.showAsDropDown(titleViewBg);
                    if (!config.isSingleDirectReturn) {
                        List<LocalMedia> selectedImages = mAdapter.getSelectedImages();
                        folderWindow.updateFolderCheckStatus(selectedImages);
                    }
                }
            }
        }

        if (id == R.id.picture_id_preview) {
            onPreview();
        }

        if (id == R.id.picture_tv_ok || id == R.id.picture_tv_img_num) {
            onComplete();
        }
    }

    private void onPreview() {
        List<LocalMedia> selectedImages = mAdapter.getSelectedImages();
        List<LocalMedia> medias = new ArrayList<>();
        int size = selectedImages.size();
        for (int i = 0; i < size; i++) {
            LocalMedia media = selectedImages.get(i);
            medias.add(media);
        }
        Bundle bundle = new Bundle();
        bundle.putParcelableArrayList(PictureConfig.EXTRA_PREVIEW_SELECT_LIST, (ArrayList<? extends Parcelable>) medias);
        bundle.putParcelableArrayList(PictureConfig.EXTRA_SELECT_LIST, (ArrayList<? extends Parcelable>) selectedImages);
        bundle.putBoolean(PictureConfig.EXTRA_BOTTOM_PREVIEW, true);
        bundle.putBoolean(PictureConfig.EXTRA_CHANGE_ORIGINAL, config.isCheckOriginalImage);
        JumpUtils.startPicturePreviewActivity(getContext(), config.isWeChatStyle, bundle,
                config.selectionMode == PictureConfig.SINGLE ? UCrop.REQUEST_CROP : UCrop.REQUEST_MULTI_CROP);

        overridePendingTransition(config.windowAnimationStyle != null
                        && config.windowAnimationStyle.activityPreviewEnterAnimation != 0
                        ? config.windowAnimationStyle.activityPreviewEnterAnimation : R.anim.picture_anim_enter,
                R.anim.picture_anim_fade_in);
    }

    /**
     * ????????????
     */
    private void onComplete() {
        List<LocalMedia> result = mAdapter.getSelectedImages();
        int size = result.size();
        LocalMedia image = result.size() > 0 ? result.get(0) : null;
        String mimeType = image != null ? image.getMimeType() : "";
        // ?????????????????????????????????????????????????????????????????????
        boolean eqImg = PictureMimeType.eqImage(mimeType);
        if (config.isWithVideoImage) {
            // ????????????
            int videoSize = 0;
            int imageSize = 0;
            for (int i = 0; i < size; i++) {
                LocalMedia media = result.get(i);
                if (PictureMimeType.eqVideo(media.getMimeType())) {
                    videoSize++;
                } else {
                    imageSize++;
                }
            }
            if (config.selectionMode == PictureConfig.MULTIPLE) {
                if (config.minSelectNum > 0) {
                    if (imageSize < config.minSelectNum) {
                        ToastUtils.s(getContext(), getString(R.string.picture_min_img_num, config.minSelectNum));
                        return;
                    }
                }
                if (config.minVideoSelectNum > 0) {
                    if (videoSize < config.minVideoSelectNum) {
                        ToastUtils.s(getContext(), getString(R.string.picture_min_video_num, config.minVideoSelectNum));
                        return;
                    }
                }
            }
        } else {
            if (config.selectionMode == PictureConfig.MULTIPLE) {
                if (PictureMimeType.eqImage(mimeType) && config.minSelectNum > 0 && size < config.minSelectNum) {
                    String str = getString(R.string.picture_min_img_num, config.minSelectNum);
                    ToastUtils.s(getContext(), str);
                    return;
                }
                if (PictureMimeType.eqVideo(mimeType) && config.minVideoSelectNum > 0 && size < config.minVideoSelectNum) {
                    String str = getString(R.string.picture_min_video_num, config.minVideoSelectNum);
                    ToastUtils.s(getContext(), str);
                    return;
                }
            }
        }

        // ??????????????????????????????????????????????????????????????????
        if (config.returnEmpty && size == 0) {
            if (config.selectionMode == PictureConfig.MULTIPLE) {
                if (config.minSelectNum > 0 && size < config.minSelectNum) {
                    String str = getString(R.string.picture_min_img_num, config.minSelectNum);
                    ToastUtils.s(getContext(), str);
                    return;
                }
                if (config.minVideoSelectNum > 0 && size < config.minVideoSelectNum) {
                    String str = getString(R.string.picture_min_video_num, config.minVideoSelectNum);
                    ToastUtils.s(getContext(), str);
                    return;
                }
            }
            if (config.listener != null) {
                config.listener.onResult(result);
            } else {
                Intent intent = PictureSelector.putIntentResult(result);
                setResult(RESULT_OK, intent);
            }
            closeActivity();
            return;
        }

        if (config.isCheckOriginalImage) {
            onResult(result);
            return;
        }
        if (config.chooseMode == PictureMimeType.ofAll() && config.isWithVideoImage) {
            // ???????????????????????????
            bothMimeTypeWith(eqImg, result);
        } else {
            // ????????????
            separateMimeTypeWith(eqImg, result);
        }
    }


    /**
     * ?????????????????????????????????
     *
     * @param eqImg
     * @param images
     */
    private void bothMimeTypeWith(boolean eqImg, List<LocalMedia> images) {
        LocalMedia image = images.size() > 0 ? images.get(0) : null;
        if (config.enableCrop) {
            if (config.selectionMode == PictureConfig.SINGLE && eqImg) {
                config.originalPath = image.getPath();
                startCrop(config.originalPath);
            } else {
                // ????????????????????????????????????????????????????????????
                ArrayList<CutInfo> cuts = new ArrayList<>();
                int count = images.size();
                int imageNum = 0;
                for (int i = 0; i < count; i++) {
                    LocalMedia media = images.get(i);
                    if (media == null
                            || TextUtils.isEmpty(media.getPath())) {
                        continue;
                    }
                    boolean eqImage = PictureMimeType.eqImage(media.getMimeType());
                    if (eqImage) {
                        imageNum++;
                    }
                    CutInfo cutInfo = new CutInfo();
                    cutInfo.setId(media.getId());
                    cutInfo.setPath(media.getPath());
                    cutInfo.setImageWidth(media.getWidth());
                    cutInfo.setImageHeight(media.getHeight());
                    cutInfo.setMimeType(media.getMimeType());
                    cutInfo.setDuration(media.getDuration());
                    cutInfo.setRealPath(media.getRealPath());
                    cuts.add(cutInfo);
                }
                if (imageNum <= 0) {
                    // ????????????
                    onResult(images);
                } else {
                    // ?????????????????????
                    startCrop(cuts);
                }
            }
        } else if (config.isCompress) {
            int size = images.size();
            int imageNum = 0;
            for (int i = 0; i < size; i++) {
                LocalMedia media = images.get(i);
                boolean eqImage = PictureMimeType.eqImage(media.getMimeType());
                if (eqImage) {
                    imageNum++;
                    break;
                }
            }
            if (imageNum <= 0) {
                // ?????????????????????
                onResult(images);
            } else {
                // ???????????????
                compressImage(images);
            }
        } else {
            onResult(images);
        }
    }

    /**
     * ??????????????????????????????????????????
     *
     * @param eqImg
     * @param images
     */
    private void separateMimeTypeWith(boolean eqImg, List<LocalMedia> images) {
        LocalMedia image = images.size() > 0 ? images.get(0) : null;
        if (config.enableCrop && eqImg) {
            if (config.selectionMode == PictureConfig.SINGLE) {
                config.originalPath = image.getPath();
                startCrop(config.originalPath);
            } else {
                // ????????????????????????????????????????????????????????????
                ArrayList<CutInfo> cuts = new ArrayList<>();
                int count = images.size();
                for (int i = 0; i < count; i++) {
                    LocalMedia media = images.get(i);
                    if (media == null
                            || TextUtils.isEmpty(media.getPath())) {
                        continue;
                    }
                    CutInfo cutInfo = new CutInfo();
                    cutInfo.setId(media.getId());
                    cutInfo.setPath(media.getPath());
                    cutInfo.setImageWidth(media.getWidth());
                    cutInfo.setImageHeight(media.getHeight());
                    cutInfo.setMimeType(media.getMimeType());
                    cutInfo.setDuration(media.getDuration());
                    cutInfo.setRealPath(media.getRealPath());
                    cuts.add(cutInfo);
                }
                startCrop(cuts);
            }
        } else if (config.isCompress
                && eqImg) {
            // ??????????????????????????????
            compressImage(images);
        } else {
            onResult(images);
        }
    }

    /**
     * ????????????
     *
     * @param path
     */
    private void audioDialog(final String path) {
        if (!isFinishing()) {
            audioDialog = new PictureCustomDialog(getContext(), R.layout.picture_audio_dialog);
            audioDialog.getWindow().setWindowAnimations(R.style.Picture_Theme_Dialog_AudioStyle);
            mTvMusicStatus = audioDialog.findViewById(R.id.tv_musicStatus);
            mTvMusicTime = audioDialog.findViewById(R.id.tv_musicTime);
            musicSeekBar = audioDialog.findViewById(R.id.musicSeekBar);
            mTvMusicTotal = audioDialog.findViewById(R.id.tv_musicTotal);
            mTvPlayPause = audioDialog.findViewById(R.id.tv_PlayPause);
            mTvStop = audioDialog.findViewById(R.id.tv_Stop);
            mTvQuit = audioDialog.findViewById(R.id.tv_Quit);
            if (mHandler != null) {
                mHandler.postDelayed(() -> initPlayer(path), 30);
            }
            mTvPlayPause.setOnClickListener(new audioOnClick(path));
            mTvStop.setOnClickListener(new audioOnClick(path));
            mTvQuit.setOnClickListener(new audioOnClick(path));
            musicSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser == true) {
                        mediaPlayer.seekTo(progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            audioDialog.setOnDismissListener(dialog -> {
                if (mHandler != null) {
                    mHandler.removeCallbacks(mRunnable);
                }
                new Handler().postDelayed(() -> stop(path), 30);
                try {
                    if (audioDialog != null
                            && audioDialog.isShowing()) {
                        audioDialog.dismiss();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            if (mHandler != null) {
                mHandler.post(mRunnable);
            }
            audioDialog.show();
        }
    }

    //  ?????? Handler ?????? UI ??????????????????
    public Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (mediaPlayer != null) {
                    mTvMusicTime.setText(DateUtils.formatDurationTime(mediaPlayer.getCurrentPosition()));
                    musicSeekBar.setProgress(mediaPlayer.getCurrentPosition());
                    musicSeekBar.setMax(mediaPlayer.getDuration());
                    mTvMusicTotal.setText(DateUtils.formatDurationTime(mediaPlayer.getDuration()));
                    if (mHandler != null) {
                        mHandler.postDelayed(mRunnable, 200);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    /**
     * ???????????????????????????
     *
     * @param path
     */
    private void initPlayer(String path) {
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(path);
            mediaPlayer.prepare();
            mediaPlayer.setLooping(true);
            playAudio();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ????????????????????????
     */
    public class audioOnClick implements View.OnClickListener {
        private String path;

        public audioOnClick(String path) {
            super();
            this.path = path;
        }

        @Override
        public void onClick(View v) {
            int id = v.getId();
            if (id == R.id.tv_PlayPause) {
                playAudio();
            }
            if (id == R.id.tv_Stop) {
                mTvMusicStatus.setText(getString(R.string.picture_stop_audio));
                mTvPlayPause.setText(getString(R.string.picture_play_audio));
                stop(path);
            }
            if (id == R.id.tv_Quit) {
                if (mHandler != null) {
                    mHandler.postDelayed(() -> stop(path), 30);
                    try {
                        if (audioDialog != null
                                && audioDialog.isShowing()) {
                            audioDialog.dismiss();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    mHandler.removeCallbacks(mRunnable);
                }
            }
        }
    }

    /**
     * ????????????
     */
    private void playAudio() {
        if (mediaPlayer != null) {
            musicSeekBar.setProgress(mediaPlayer.getCurrentPosition());
            musicSeekBar.setMax(mediaPlayer.getDuration());
        }
        String ppStr = mTvPlayPause.getText().toString();
        if (ppStr.equals(getString(R.string.picture_play_audio))) {
            mTvPlayPause.setText(getString(R.string.picture_pause_audio));
            mTvMusicStatus.setText(getString(R.string.picture_play_audio));
            playOrPause();
        } else {
            mTvPlayPause.setText(getString(R.string.picture_play_audio));
            mTvMusicStatus.setText(getString(R.string.picture_pause_audio));
            playOrPause();
        }
        if (isPlayAudio == false) {
            if (mHandler != null) {
                mHandler.post(mRunnable);
            }
            isPlayAudio = true;
        }
    }

    /**
     * ????????????
     *
     * @param path
     */
    public void stop(String path) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.reset();
                mediaPlayer.setDataSource(path);
                mediaPlayer.prepare();
                mediaPlayer.seekTo(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * ????????????
     */
    public void playOrPause() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                } else {
                    mediaPlayer.start();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onItemClick(boolean isCameraFolder, String folderName, List<LocalMedia> images) {
        boolean camera = config.isCamera ? isCameraFolder : false;
        mAdapter.setShowCamera(camera);
        mTvPictureTitle.setText(folderName);
        folderWindow.dismiss();
        mAdapter.bindImagesData(images);
        mPictureRecycler.smoothScrollToPosition(0);
    }

    @Override
    public void onTakePhoto() {
        // ??????????????????,????????????????????????????????????
        if (PermissionChecker.checkSelfPermission(this, Manifest.permission.CAMERA)) {
            startCamera();
        } else {
            PermissionChecker
                    .requestPermissions(this,
                            new String[]{Manifest.permission.CAMERA}, PictureConfig.APPLY_CAMERA_PERMISSIONS_CODE);
        }
    }

    @Override
    public void onChange(List<LocalMedia> selectImages) {
        changeImageNumber(selectImages);
    }

    @Override
    public void onPictureClick(LocalMedia media, int position) {
        if (config.selectionMode == PictureConfig.SINGLE && config.isSingleDirectReturn) {
            List<LocalMedia> list = new ArrayList<>();
            list.add(media);
            if (config.enableCrop && PictureMimeType.eqImage(media.getMimeType()) && !config.isCheckOriginalImage) {
                mAdapter.bindSelectImages(list);
                startCrop(media.getPath());
            } else {
                handlerResult(list);
            }
        } else {
            List<LocalMedia> images = mAdapter.getImages();
            startPreview(images, position);
        }
    }

    /**
     * preview image and video
     *
     * @param previewImages
     * @param position
     */
    public void startPreview(List<LocalMedia> previewImages, int position) {
        LocalMedia media = previewImages.get(position);
        String mimeType = media.getMimeType();
        Bundle bundle = new Bundle();
        List<LocalMedia> result = new ArrayList<>();
        if (PictureMimeType.eqVideo(mimeType)) {
            // video
            if (config.selectionMode == PictureConfig.SINGLE && !config.enPreviewVideo) {
                result.add(media);
                onResult(result);
            } else {
                if (config.customVideoPlayCallback != null) {
                    config.customVideoPlayCallback.startPlayVideo(media);
                } else {
                    bundle.putParcelable(PictureConfig.EXTRA_MEDIA_KEY, media);
                    JumpUtils.startPictureVideoPlayActivity(getContext(), bundle, PictureConfig.PREVIEW_VIDEO_CODE);
                }
            }
        } else if (PictureMimeType.eqAudio(mimeType)) {
            // audio
            if (config.selectionMode == PictureConfig.SINGLE) {
                result.add(media);
                onResult(result);
            } else {
                audioDialog(media.getPath());
            }
        } else {
            // image
            List<LocalMedia> selectedImages = mAdapter.getSelectedImages();
            ImagesObservable.getInstance().savePreviewMediaData(new ArrayList<>(previewImages));
            bundle.putParcelableArrayList(PictureConfig.EXTRA_SELECT_LIST, (ArrayList<? extends Parcelable>) selectedImages);
            bundle.putInt(PictureConfig.EXTRA_POSITION, position);
            bundle.putBoolean(PictureConfig.EXTRA_CHANGE_ORIGINAL, config.isCheckOriginalImage);
            JumpUtils.startPicturePreviewActivity(getContext(), config.isWeChatStyle, bundle,
                    config.selectionMode == PictureConfig.SINGLE ? UCrop.REQUEST_CROP : UCrop.REQUEST_MULTI_CROP);
            overridePendingTransition(config.windowAnimationStyle != null
                    && config.windowAnimationStyle.activityPreviewEnterAnimation != 0
                    ? config.windowAnimationStyle.activityPreviewEnterAnimation : R.anim.picture_anim_enter, R.anim.picture_anim_fade_in);
        }
    }


    /**
     * change image selector state
     *
     * @param selectImages
     */
    protected void changeImageNumber(List<LocalMedia> selectImages) {
        // ???????????????????????????????????????
        if (config.chooseMode == PictureMimeType.ofAudio()) {
            mTvPicturePreview.setVisibility(View.GONE);
        } else {
            if (config.isOriginalControl) {
                mCbOriginal.setVisibility(View.VISIBLE);
                mCbOriginal.setChecked(config.isCheckOriginalImage);
            }
        }
        boolean enable = selectImages.size() != 0;
        if (enable) {
            mTvPictureOk.setEnabled(true);
            mTvPictureOk.setSelected(true);
            mTvPicturePreview.setEnabled(true);
            mTvPicturePreview.setSelected(true);
            if (config.style != null) {
                if (config.style.pictureCompleteTextColor != 0) {
                    mTvPictureOk.setTextColor(config.style.pictureCompleteTextColor);
                }
                if (config.style.picturePreviewTextColor != 0) {
                    mTvPicturePreview.setTextColor(config.style.picturePreviewTextColor);
                }
            }
            if (config.style != null && !TextUtils.isEmpty(config.style.picturePreviewText)) {
                mTvPicturePreview.setText(config.style.picturePreviewText);
            } else {
                mTvPicturePreview.setText(getString(R.string.picture_preview_num, selectImages.size()));
            }
            if (numComplete) {
                initCompleteText(selectImages.size());
            } else {
                if (!isStartAnimation) {
                    mTvPictureImgNum.startAnimation(animation);
                }
                mTvPictureImgNum.setVisibility(View.VISIBLE);
                mTvPictureImgNum.setText(String.valueOf(selectImages.size()));
                if (config.style != null && !TextUtils.isEmpty(config.style.pictureCompleteText)) {
                    mTvPictureOk.setText(config.style.pictureCompleteText);
                } else {
                    mTvPictureOk.setText(getString(R.string.picture_completed));
                }
                isStartAnimation = false;
            }
        } else {
            mTvPictureOk.setEnabled(config.returnEmpty);
            mTvPictureOk.setSelected(false);
            mTvPicturePreview.setEnabled(false);
            mTvPicturePreview.setSelected(false);
            if (config.style != null) {
                if (config.style.pictureUnCompleteTextColor != 0) {
                    mTvPictureOk.setTextColor(config.style.pictureUnCompleteTextColor);
                }
                if (config.style.pictureUnPreviewTextColor != 0) {
                    mTvPicturePreview.setTextColor(config.style.pictureUnPreviewTextColor);
                }
            }
            if (config.style != null && !TextUtils.isEmpty(config.style.pictureUnPreviewText)) {
                mTvPicturePreview.setText(config.style.pictureUnPreviewText);
            } else {
                mTvPicturePreview.setText(getString(R.string.picture_preview));
            }
            if (numComplete) {
                initCompleteText(selectImages.size());
            } else {
                mTvPictureImgNum.setVisibility(View.INVISIBLE);
                if (config.style != null && !TextUtils.isEmpty(config.style.pictureUnCompleteText)) {
                    mTvPictureOk.setText(config.style.pictureUnCompleteText);
                } else {
                    mTvPictureOk.setText(getString(R.string.picture_please_select));
                }
            }
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case PictureConfig.PREVIEW_VIDEO_CODE:
                    if (data != null) {
                        List<LocalMedia> list = data.getParcelableArrayListExtra(PictureConfig.EXTRA_SELECT_LIST);
                        if (list != null && list.size() > 0) {
                            onResult(list);
                        }
                    }
                    break;
                case UCrop.REQUEST_CROP:
                    singleCropHandleResult(data);
                    break;
                case UCrop.REQUEST_MULTI_CROP:
                    multiCropHandleResult(data);
                    break;
                case PictureConfig.REQUEST_CAMERA:
                    requestCamera(data);
                    break;
                default:
                    break;
            }
        } else if (resultCode == RESULT_CANCELED) {
            previewCallback(data);
        } else if (resultCode == UCrop.RESULT_ERROR) {
            if (data != null) {
                Throwable throwable = (Throwable) data.getSerializableExtra(UCrop.EXTRA_ERROR);
                ToastUtils.s(getContext(), throwable.getMessage());
            }
        }
    }

    /**
     * ????????????????????????
     *
     * @param data
     */
    private void previewCallback(Intent data) {
        if (data == null) {
            return;
        }
        if (config.isOriginalControl) {
            boolean isCheckOriginal = data.getBooleanExtra(PictureConfig.EXTRA_CHANGE_ORIGINAL, config.isCheckOriginalImage);
            config.isCheckOriginalImage = isCheckOriginal;
            mCbOriginal.setChecked(config.isCheckOriginalImage);
        }
        // ??????????????????????????????????????????????????????
        List<LocalMedia> list = data.getParcelableArrayListExtra(PictureConfig.EXTRA_SELECT_LIST);
        if (mAdapter != null && list != null) {
            // ?????????????????????????????????????????????????????????????????????
            boolean isCompleteOrSelected = data.getBooleanExtra(PictureConfig.EXTRA_COMPLETE_SELECTED, false);
            if (isCompleteOrSelected) {
                onChangeData(list);
                if (config.isWithVideoImage) {
                    // ????????????
                    int size = list.size();
                    int imageSize = 0;
                    for (int i = 0; i < size; i++) {
                        LocalMedia media = list.get(i);
                        if (PictureMimeType.eqImage(media.getMimeType())) {
                            imageSize++;
                            break;
                        }
                    }
                    if (imageSize <= 0 || !config.isCompress || config.isCheckOriginalImage) {
                        // ????????????
                        onResult(list);
                    } else {
                        // ?????????
                        compressImage(list);
                    }
                } else {
                    // ?????????1?????????????????????????????????????????????????????????????????????????????????????????????
                    String mimeType = list.size() > 0 ? list.get(0).getMimeType() : "";
                    if (config.isCompress && PictureMimeType.eqImage(mimeType)
                            && !config.isCheckOriginalImage) {
                        compressImage(list);
                    } else {
                        onResult(list);
                    }
                }
            } else {
                // ??????????????????????????????????????????
                isStartAnimation = true;
            }
            mAdapter.bindSelectImages(list);
            mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * ??????????????????????????????
     *
     * @param list
     */
    protected void onChangeData(List<LocalMedia> list) {

    }

    /**
     * singleDirectReturn??????????????????????????????
     *
     * @param mimeType
     */
    private void singleDirectReturnCameraHandleResult(String mimeType) {
        boolean eqImg = PictureMimeType.eqImage(mimeType);
        if (config.enableCrop && eqImg) {
            // ?????????
            config.originalPath = config.cameraPath;
            startCrop(config.cameraPath);
        } else if (config.isCompress && eqImg) {
            // ?????????
            List<LocalMedia> selectedImages = mAdapter.getSelectedImages();
            compressImage(selectedImages);
        } else {
            // ????????? ????????? ??????????????????
            onResult(mAdapter.getSelectedImages());
        }
    }

    /**
     * ?????????????????????
     *
     * @param data
     */

    private void requestCamera(Intent data) {
        // on take photo success
        String mimeType = null;
        long duration = 0;
        boolean isAndroidQ = SdkVersionUtils.checkedAndroid_Q();
        if (config.chooseMode == PictureMimeType.ofAudio()) {
            // ??????????????????
            config.cameraPath = getAudioPath(data);
            if (TextUtils.isEmpty(config.cameraPath)) {
                return;
            }
            mimeType = PictureMimeType.MIME_TYPE_AUDIO;
            duration = MediaUtils.extractDuration(getContext(), isAndroidQ, config.cameraPath);
        }
        if (TextUtils.isEmpty(config.cameraPath) || new File(config.cameraPath) == null) {
            return;
        }
        long size = 0;
        int[] newSize = new int[2];
        if (!isAndroidQ) {
            if (config.isFallbackVersion3) {
                new PictureMediaScannerConnection(getContext(), config.cameraPath,
                        () -> {
                        });
            } else {
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(new File(config.cameraPath))));
            }
        }
        LocalMedia media = new LocalMedia();
        if (config.chooseMode != PictureMimeType.ofAudio()) {
            // ????????????????????????
            if (config.cameraPath.startsWith("content://")) {
                String path = PictureFileUtils.getPath(getApplicationContext(), Uri.parse(config.cameraPath));
                File file = new File(path);
                size = file.length();
                mimeType = PictureMimeType.getMimeType(file);
                if (PictureMimeType.eqImage(mimeType)) {
                    newSize = MediaUtils.getLocalImageSizeToAndroidQ(this, config.cameraPath);
                } else {
                    newSize = MediaUtils.getLocalVideoSize(this, Uri.parse(config.cameraPath));
                    duration = MediaUtils.extractDuration(getContext(), true, config.cameraPath);
                }
                int lastIndexOf = config.cameraPath.lastIndexOf("/") + 1;
                media.setId(lastIndexOf > 0 ? ValueOf.toLong(config.cameraPath.substring(lastIndexOf)) : -1);
                media.setRealPath(path);
            } else {
                File file = new File(config.cameraPath);
                mimeType = PictureMimeType.getMimeType(file);
                size = file.length();
                if (PictureMimeType.eqImage(mimeType)) {
                    int degree = PictureFileUtils.readPictureDegree(this, config.cameraPath);
                    BitmapUtils.rotateImage(degree, config.cameraPath);
                    newSize = MediaUtils.getLocalImageWidthOrHeight(config.cameraPath);
                } else {
                    newSize = MediaUtils.getLocalVideoSize(config.cameraPath);
                    duration = MediaUtils.extractDuration(getContext(), false, config.cameraPath);
                }
                // ????????????????????????id
                media.setId(System.currentTimeMillis());
            }
        }
        media.setDuration(duration);
        media.setWidth(newSize[0]);
        media.setHeight(newSize[1]);
        media.setPath(config.cameraPath);
        media.setMimeType(mimeType);
        media.setSize(size);
        media.setChooseModel(config.chooseMode);
        if (mAdapter != null) {
            images.add(0, media);
            if (checkVideoLegitimacy(media)) {
                if (config.selectionMode == PictureConfig.SINGLE) {
                    // ?????????????????????????????????
                    if (config.isSingleDirectReturn) {
                        List<LocalMedia> selectedImages = mAdapter.getSelectedImages();
                        selectedImages.add(media);
                        mAdapter.bindSelectImages(selectedImages);
                        singleDirectReturnCameraHandleResult(mimeType);
                    } else {
                        List<LocalMedia> selectedImages = mAdapter.getSelectedImages();
                        mimeType = selectedImages.size() > 0 ? selectedImages.get(0).getMimeType() : "";
                        boolean mimeTypeSame = PictureMimeType.isMimeTypeSame(mimeType, media.getMimeType());
                        // ??????????????????????????????????????????????????????
                        if (mimeTypeSame || selectedImages.size() == 0) {
                            // ??????????????????????????????????????????????????????(???????????????)
                            singleRadioMediaImage();
                            selectedImages.add(media);
                            mAdapter.bindSelectImages(selectedImages);
                        }
                    }
                } else {
                    // ????????????
                    List<LocalMedia> selectedImages = mAdapter.getSelectedImages();
                    int count = selectedImages.size();
                    mimeType = count > 0 ? selectedImages.get(0).getMimeType() : "";
                    boolean mimeTypeSame = PictureMimeType.isMimeTypeSame(mimeType, media.getMimeType());
                    if (config.isWithVideoImage) {
                        // ????????????
                        int videoSize = 0;
                        int imageSize = 0;
                        for (int i = 0; i < count; i++) {
                            LocalMedia m = selectedImages.get(i);
                            if (PictureMimeType.eqVideo(m.getMimeType())) {
                                videoSize++;
                            } else {
                                imageSize++;
                            }
                        }
                        if (PictureMimeType.eqVideo(media.getMimeType()) && config.maxVideoSelectNum > 0) {
                            // ???????????????
                            if (videoSize < config.maxVideoSelectNum) {
                                selectedImages.add(media);
                                mAdapter.bindSelectImages(selectedImages);
                            } else {
                                ToastUtils.s(getContext(), StringUtils.getMsg(getContext(), media.getMimeType(),
                                        config.maxVideoSelectNum));
                            }
                        } else {
                            // ???????????????
                            if (imageSize < config.maxSelectNum) {
                                selectedImages.add(media);
                                mAdapter.bindSelectImages(selectedImages);
                            } else {
                                ToastUtils.s(getContext(), StringUtils.getMsg(getContext(), media.getMimeType(),
                                        config.maxSelectNum));
                            }
                        }

                    } else {
                        if (PictureMimeType.eqVideo(mimeType) && config.maxVideoSelectNum > 0) {
                            // ??????????????????????????????????????????????????????
                            if (count < config.maxVideoSelectNum) {
                                if (mimeTypeSame || count == 0) {
                                    if (selectedImages.size() < config.maxVideoSelectNum) {
                                        selectedImages.add(media);
                                        mAdapter.bindSelectImages(selectedImages);
                                    }
                                }
                            } else {
                                ToastUtils.s(getContext(), StringUtils.getMsg(getContext(), mimeType,
                                        config.maxVideoSelectNum));
                            }
                        } else {
                            // ???????????????????????? ??????????????????????????????
                            if (count < config.maxSelectNum) {
                                // ??????????????????????????????????????????????????????
                                if (mimeTypeSame || count == 0) {
                                    if (count < config.maxSelectNum) {
                                        selectedImages.add(media);
                                        mAdapter.bindSelectImages(selectedImages);
                                    }
                                }
                            } else {
                                ToastUtils.s(getContext(), StringUtils.getMsg(getContext(), mimeType,
                                        config.maxSelectNum));
                            }
                        }
                    }
                }
            }
            mAdapter.notifyItemInserted(config.isCamera ? 1 : 0);
            mAdapter.notifyItemRangeChanged(config.isCamera ? 1 : 0, images.size());
            // ???????????????????????????Intent.ACTION_MEDIA_SCANNER_SCAN_FILE????????????????????????????????????
            manualSaveFolder(media);
            // ?????????????????????????????????????????????DCIM????????????????????????????????????
            if (!isAndroidQ && PictureMimeType.eqImage(media.getMimeType())) {
                int lastImageId = getLastImageId(media.getMimeType());
                if (lastImageId != -1) {
                    removeMedia(lastImageId);
                }
            }
            mTvEmpty.setVisibility(images.size() > 0 || config.isSingleDirectReturn ? View.INVISIBLE : View.VISIBLE);
        }
    }

    /**
     * ????????????????????????
     *
     * @param media
     * @return
     */
    private boolean checkVideoLegitimacy(LocalMedia media) {
        boolean isEnterNext = true;
        if (PictureMimeType.eqVideo(media.getMimeType())) {
            // ??????????????????????????????
            if (config.videoMinSecond > 0 && config.videoMaxSecond > 0) {
                // ??????????????????????????????????????????????????????????????????????????????
                if (media.getDuration() < config.videoMinSecond || media.getDuration() > config.videoMaxSecond) {
                    isEnterNext = false;
                    ToastUtils.s(getContext(), getString(R.string.picture_choose_limit_seconds, config.videoMinSecond / 1000, config.videoMaxSecond / 1000));
                }
            } else if (config.videoMinSecond > 0 && config.videoMaxSecond <= 0) {
                // ??????????????????????????????????????????
                if (media.getDuration() < config.videoMinSecond) {
                    isEnterNext = false;
                    ToastUtils.s(getContext(), getString(R.string.picture_choose_min_seconds, config.videoMinSecond / 1000));
                }
            } else if (config.videoMinSecond <= 0 && config.videoMaxSecond > 0) {
                // ??????????????????????????????????????????
                if (media.getDuration() > config.videoMaxSecond) {
                    isEnterNext = false;
                    ToastUtils.s(getContext(), getString(R.string.picture_choose_max_seconds, config.videoMaxSecond / 1000));
                }
            }
        }
        return isEnterNext;
    }

    /**
     * ??????????????????
     *
     * @param data
     */
    private void singleCropHandleResult(Intent data) {
        if (data == null) {
            return;
        }
        List<LocalMedia> result = new ArrayList<>();
        Uri resultUri = UCrop.getOutput(data);
        String cutPath = resultUri.getPath();
        if (mAdapter != null) {
            List<LocalMedia> list = data.getParcelableArrayListExtra(PictureConfig.EXTRA_SELECT_LIST);
            if (list != null) {
                mAdapter.bindSelectImages(list);
                mAdapter.notifyDataSetChanged();
            }
            // ?????????????????????????????????path????????????
            List<LocalMedia> mediaList = mAdapter.getSelectedImages();
            LocalMedia media = mediaList != null && mediaList.size() > 0 ? mediaList.get(0) : null;
            if (media != null) {
                config.originalPath = media.getPath();
                media.setCutPath(cutPath);
                media.setSize(new File(cutPath).length());
                media.setChooseModel(config.chooseMode);
                media.setCut(true);
                if (SdkVersionUtils.checkedAndroid_Q()) {
                    media.setAndroidQToPath(cutPath);
                }
                result.add(media);
                handlerResult(result);
            } else {
                // ??????????????????????????????????????????
                media = list != null && list.size() > 0 ? list.get(0) : null;
                config.originalPath = media.getPath();
                media.setCutPath(cutPath);
                media.setSize(new File(cutPath).length());
                media.setChooseModel(config.chooseMode);
                media.setCut(true);
                if (SdkVersionUtils.checkedAndroid_Q()) {
                    media.setAndroidQToPath(cutPath);
                }
                result.add(media);
                handlerResult(result);
            }
        }
    }

    /**
     * ??????????????????
     *
     * @param data
     */
    protected void multiCropHandleResult(Intent data) {
        if (data == null) {
            return;
        }
        List<CutInfo> mCuts = UCrop.getMultipleOutput(data);
        if (mCuts == null || mCuts.size() == 0) {
            return;
        }
        int size = mCuts.size();
        boolean isAndroidQ = SdkVersionUtils.checkedAndroid_Q();
        List<LocalMedia> list = data.getParcelableArrayListExtra(PictureConfig.EXTRA_SELECT_LIST);
        if (list != null) {
            mAdapter.bindSelectImages(list);
            mAdapter.notifyDataSetChanged();
        }
        int oldSize = mAdapter != null ? mAdapter.getSelectedImages().size() : 0;
        if (oldSize == size) {
            List<LocalMedia> result = mAdapter.getSelectedImages();
            for (int i = 0; i < size; i++) {
                CutInfo c = mCuts.get(i);
                LocalMedia media = result.get(i);
                media.setCut(TextUtils.isEmpty(c.getCutPath()) ? false : true);
                media.setPath(c.getPath());
                media.setMimeType(c.getMimeType());
                media.setCutPath(c.getCutPath());
                media.setWidth(c.getImageWidth());
                media.setHeight(c.getImageHeight());
                media.setSize(new File(TextUtils.isEmpty(c.getCutPath())
                        ? c.getPath() : c.getCutPath()).length());
                media.setAndroidQToPath(isAndroidQ ? c.getCutPath() : media.getAndroidQToPath());
            }
            handlerResult(result);
        } else {
            // ????????????
            List<LocalMedia> result = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                CutInfo c = mCuts.get(i);
                LocalMedia media = new LocalMedia();
                media.setId(c.getId());
                media.setCut(TextUtils.isEmpty(c.getCutPath()) ? false : true);
                media.setPath(c.getPath());
                media.setCutPath(c.getCutPath());
                media.setMimeType(c.getMimeType());
                media.setWidth(c.getImageWidth());
                media.setHeight(c.getImageHeight());
                media.setDuration(c.getDuration());
                media.setSize(new File(TextUtils.isEmpty(c.getCutPath())
                        ? c.getPath() : c.getCutPath()).length());
                media.setChooseModel(config.chooseMode);
                media.setAndroidQToPath(isAndroidQ ? c.getCutPath() : null);
                result.add(media);
            }
            handlerResult(result);
        }
    }

    /**
     * ????????????
     */
    private void singleRadioMediaImage() {
        List<LocalMedia> selectImages = mAdapter.getSelectedImages();
        if (selectImages != null
                && selectImages.size() > 0) {
            LocalMedia media = selectImages.get(0);
            int position = media.getPosition();
            selectImages.clear();
            mAdapter.notifyItemChanged(position);
        }
    }

    /**
     * ???????????????????????????????????????????????????????????????
     *
     * @param media
     */
    private void manualSaveFolder(LocalMedia media) {
        try {
            createNewFolder(foldersList);
            LocalMediaFolder folder = getImageFolder(media.getPath(), foldersList);
            LocalMediaFolder cameraFolder = foldersList.size() > 0 ? foldersList.get(0) : null;
            if (cameraFolder != null && folder != null) {
                // ????????????
                cameraFolder.setFirstImagePath(media.getPath());
                cameraFolder.setImages(images);
                cameraFolder.setImageNum(cameraFolder.getImageNum() + 1);
                // ????????????
                int num = folder.getImageNum() + 1;
                folder.setImageNum(num);
                folder.getImages().add(0, media);
                folder.setFirstImagePath(config.cameraPath);
                folderWindow.bindFolder(foldersList);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ????????????????????????
     *
     * @param imageFolders
     */
    private void updateMediaFolder(List<LocalMediaFolder> imageFolders, LocalMedia media) {
        File imageFile = new File(media.getPath().startsWith("content://")
                ? PictureFileUtils.getPath(getContext(), Uri.parse(media.getPath())) : media.getPath());
        File folderFile = imageFile.getParentFile();
        int size = imageFolders.size();
        for (int i = 0; i < size; i++) {
            LocalMediaFolder folder = imageFolders.get(i);
            // ???????????????????????????????????????????????????????????????
            String name = folder.getName();
            if (TextUtils.isEmpty(name)) {
                continue;
            }
            if (name.equals(folderFile.getName())) {
                folder.setFirstImagePath(config.cameraPath);
                folder.setImageNum(folder.getImageNum() + 1);
                folder.setCheckedNum(1);
                folder.getImages().add(0, media);
                break;
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        closeActivity();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (animation != null) {
            animation.cancel();
            animation = null;
        }
        if (mediaPlayer != null && mHandler != null) {
            mHandler.removeCallbacks(mRunnable);
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    public void onItemClick(int position) {
        switch (position) {
            case PhotoItemSelectedDialog.IMAGE_CAMERA:
                // ??????
                startOpenCamera();
                break;
            case PhotoItemSelectedDialog.VIDEO_CAMERA:
                // ?????????
                startOpenCameraVideo();
                break;
            default:
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PictureConfig.APPLY_STORAGE_PERMISSIONS_CODE:
                // ????????????
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    readLocalMedia();
                } else {
                    ToastUtils.s(getContext(), getString(R.string.picture_jurisdiction));
                    onBackPressed();
                }
                break;
            case PictureConfig.APPLY_CAMERA_PERMISSIONS_CODE:
                // ????????????
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    onTakePhoto();
                } else {
                    ToastUtils.s(getContext(), getString(R.string.picture_camera));
                }
                break;
            case PictureConfig.APPLY_RECORD_AUDIO_PERMISSIONS_CODE:
                // ????????????
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startCustomCamera();
                } else {
                    ToastUtils.s(getContext(), getString(R.string.picture_camera));
                }
                break;
        }
    }
}

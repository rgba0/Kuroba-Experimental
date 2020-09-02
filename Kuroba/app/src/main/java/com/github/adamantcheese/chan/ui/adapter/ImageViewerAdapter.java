/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.ui.adapter;

import android.view.View;
import android.view.ViewGroup;

import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.ui.view.MultiImageView;
import com.github.adamantcheese.chan.ui.view.ViewPagerAdapter;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;

public class ImageViewerAdapter
        extends ViewPagerAdapter {
    private static final String TAG = "ImageViewerAdapter";

    private final List<PostImage> images;
    private final MultiImageView.Callback multiImageViewCallback;

    private List<MultiImageView> loadedViews = new ArrayList<>(3);
    private List<ModeChange> pendingModeChanges = new ArrayList<>();

    public ImageViewerAdapter(
            List<PostImage> images,
            MultiImageView.Callback multiImageViewCallback
    ) {
        this.images = images;
        this.multiImageViewCallback = multiImageViewCallback;
    }

    public void onDestroy() {
        for (MultiImageView loadedView : loadedViews) {
            loadedView.unbindPostImage();
        }

        loadedViews.clear();
    }

    @Override
    public View getView(int position, ViewGroup parent) {
        PostImage postImage = images.get(position);
        MultiImageView view = new MultiImageView(parent.getContext());

        // hacky but good enough
        view.bindPostImage(
                postImage,
                multiImageViewCallback,
                images.get(0) == postImage
        );

        loadedViews.add(view);
        return view;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        super.destroyItem(container, position, object);

        ((MultiImageView) object).unbindPostImage();
        loadedViews.remove(object);
    }

    @Override
    public int getCount() {
        return images.size();
    }

    public MultiImageView find(PostImage postImage) {
        for (MultiImageView view : loadedViews) {
            if (view.getPostImage() == postImage) {
                return view;
            }
        }
        return null;
    }

    @Override
    public void finishUpdate(ViewGroup container) {
        for (ModeChange change : pendingModeChanges) {
            MultiImageView view = find(change.postImage);
            if (view == null || view.getWindowToken() == null) {
                Logger.w(TAG, "finishUpdate setMode view still not found");
            } else {
                view.setMode(change.mode, change.center);
            }
        }
        pendingModeChanges.clear();
    }

    public void setMode(final PostImage postImage, MultiImageView.Mode mode, boolean center) {
        MultiImageView view = find(postImage);
        if (view == null || view.getWindowToken() == null) {
            pendingModeChanges.add(new ModeChange(mode, postImage, center));
        } else {
            view.setMode(mode, center);
        }
    }

    public void setVolume(PostImage postImage, boolean muted) {
        // It must be loaded, or the user is not able to click the menu item.
        MultiImageView view = find(postImage);
        if (view != null) {
            view.setVolume(muted);
        }
    }

    public MultiImageView.Mode getMode(PostImage postImage) {
        MultiImageView view = find(postImage);
        if (view == null) {
            Logger.w(TAG, "getMode view not found");
            return null;
        } else {
            return view.getMode();
        }
    }

    public void onSystemUiVisibilityChange(boolean visible) {
        for (MultiImageView view : loadedViews) {
            view.onSystemUiVisibilityChange(visible);
        }
    }

    public void toggleTransparency(PostImage postImage) {
        MultiImageView view = find(postImage);
        if (view != null) {
            view.toggleTransparency();
        }
    }

    public void rotateImage(PostImage postImage, int degrees) {
        MultiImageView view = find(postImage);
        if (view != null) {
            view.rotateImage(degrees);
        }
    }

    public void onImageSaved(PostImage postImage) {
        MultiImageView view = find(postImage);
        if (view != null) {
            view.setImageAlreadySaved();
        }
    }

    private static class ModeChange {
        public MultiImageView.Mode mode;
        public PostImage postImage;
        public boolean center;

        private ModeChange(MultiImageView.Mode mode, PostImage postImage, boolean center) {
            this.mode = mode;
            this.postImage = postImage;
            this.center = center;
        }
    }
}

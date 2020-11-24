package com.github.k1rakishou.chan.features.settings.screens.delegate.base_directory

import android.content.Context
import android.widget.Toast
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.showToast
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import com.github.k1rakishou.fsaf.file.ExternalFile

class SharedLocationSetupDelegate(
  private val context: Context,
  private val callbacks: SaveLocationSetupDelegate.MediaControllerCallbacks,
  private val presenter: MediaSettingsControllerPresenter,
  private val fileManager: FileManager,
  private val dialogFactory: DialogFactory
) : SharedLocationSetupDelegateCallbacks {
  private var loadingViewController: LoadingViewController? = null

  override fun askUserIfTheyWantToMoveOldSavedFilesToTheNewDirectory(
    oldBaseDirectory: AbstractFile?,
    newBaseDirectory: AbstractFile
  ) {
    BackgroundUtils.ensureMainThread()

    if (oldBaseDirectory == null) {
      showToast(context, R.string.done, Toast.LENGTH_LONG)
      return
    }

    val isOldBaseDirExternal = oldBaseDirectory is ExternalFile
    val isNewBaseDirExternal = newBaseDirectory is ExternalFile

    if (isOldBaseDirExternal xor isNewBaseDirExternal) {
      // oldBaseDirectory and newBaseDirectory do not use the same provider (one of them uses a
      // RawFile and the other one uses ExternalFile). It's kinda hard to determine whether
      // they are the same directory or whether one is a parent of the other. So we are just
      // not gonna do in such case.
      showToast(context, R.string.done, Toast.LENGTH_LONG)
      return
    }

    if (fileManager.areTheSame(oldBaseDirectory, newBaseDirectory)) {
      showToast(context, R.string.done, Toast.LENGTH_LONG)
      return
    }

    if (fileManager.isChildOfDirectory(oldBaseDirectory, newBaseDirectory)) {
      showToast(context, R.string.done, Toast.LENGTH_LONG)
      return
    }

    val moveFilesDescription = getString(
      R.string.media_settings_move_saved_files_to_new_dir_description,
      oldBaseDirectory.getFullPath(),
      newBaseDirectory.getFullPath()
    )

    dialogFactory.createSimpleConfirmationDialog(
      context = context,
      titleTextId = R.string.media_settings_move_saved_files_to_new_dir,
      descriptionText = moveFilesDescription,
      positiveButtonText = getString(R.string.move),
      onPositiveButtonClickListener = {
        presenter.moveOldFilesToTheNewDirectory(oldBaseDirectory, newBaseDirectory)
      },
      negativeButtonText = getString(R.string.do_not),
      onNegativeButtonClickListener = { dialog -> dialog.dismiss() }
    )
  }

  override fun updateLoadingViewText(text: String) {
    BackgroundUtils.ensureMainThread()
    loadingViewController?.updateWithText(text)
  }

  override fun updateSaveLocationViewText(newLocation: String) {
    BackgroundUtils.ensureMainThread()
    callbacks.updateSaveLocationViewText(newLocation)
  }

  override fun showCopyFilesDialog(
    filesCount: Int,
    oldBaseDirectory: AbstractFile,
    newBaseDirectory: AbstractFile
  ) {
    BackgroundUtils.ensureMainThread()

    if (loadingViewController != null) {
      loadingViewController!!.stopPresenting()
      loadingViewController = null
    }

    dialogFactory.createSimpleConfirmationDialog(
      context = context,
      titleTextId = R.string.media_settings_copy_files,
      descriptionText = getString(R.string.media_settings_do_you_want_to_copy_files, filesCount),
      positiveButtonText = getString(R.string.media_settings_copy_files),
      onPositiveButtonClickListener = {
        loadingViewController = LoadingViewController(context, false).apply {
          callbacks.presentController(this)
        }

        presenter.moveFilesInternal(oldBaseDirectory, newBaseDirectory)
      },
      negativeButtonText = getString(R.string.do_not),
      onNegativeButtonClickListener = { dialog -> dialog.dismiss() }
    )
  }

  override fun onCopyDirectoryEnded(
    oldBaseDirectory: AbstractFile,
    newBaseDirectory: AbstractFile,
    result: Boolean
  ) {
    BackgroundUtils.ensureMainThread()

    if (loadingViewController != null) {
      loadingViewController!!.stopPresenting()
      loadingViewController = null
    }

    if (!result) {
      showToast(context, R.string.media_settings_could_not_copy_files, Toast.LENGTH_LONG)
    } else {
      showDeleteOldFilesDialog(oldBaseDirectory)
      showToast(context, R.string.media_settings_files_copied, Toast.LENGTH_LONG)
    }
  }

  private fun showDeleteOldFilesDialog(oldBaseDirectory: AbstractFile) {
    dialogFactory.createSimpleConfirmationDialog(
      context = context,
      titleTextId = R.string.media_settings_would_you_like_to_delete_file_in_old_dir,
      descriptionText = getString(R.string.media_settings_file_have_been_copied, oldBaseDirectory.getFullPath()),
      positiveButtonText = getString(R.string.delete),
      onPositiveButtonClickListener = { onDeleteOldFilesClicked(oldBaseDirectory) },
      negativeButtonText = getString(R.string.do_not),
      onNegativeButtonClickListener = { dialog -> dialog.dismiss() }
    )
  }

  private fun onDeleteOldFilesClicked(oldBaseDirectory: AbstractFile) {
    if (!fileManager.deleteContent(oldBaseDirectory)) {
      showToast(context, R.string.media_settings_could_not_delete_files_in_old_dir, Toast.LENGTH_LONG)
      return
    }

    showToast(context, R.string.media_settings_old_files_deleted, Toast.LENGTH_LONG)
  }

}
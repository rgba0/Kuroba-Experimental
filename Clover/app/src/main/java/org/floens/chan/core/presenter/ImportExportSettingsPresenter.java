/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.core.presenter;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.floens.chan.core.repository.BoardRepository;
import org.floens.chan.core.repository.ImportExportRepository;

import java.io.File;

import javax.inject.Inject;

import static org.floens.chan.Chan.inject;

public class ImportExportSettingsPresenter {
    @Nullable
    private ImportExportSettingsCallbacks callbacks;

    @Inject
    ImportExportRepository importExportRepository;

    @Inject
    BoardRepository boardRepository;

    public ImportExportSettingsPresenter(
            @NonNull ImportExportSettingsCallbacks callbacks
    ) {
        inject(this);
        this.callbacks = callbacks;
    }

    public void onDestroy() {
        this.callbacks = null;
    }

    public void doExport(File cacheDir) {
        importExportRepository.exportTo(cacheDir, new ImportExportRepository.ImportExportCallbacks() {
            @Override
            public void onSuccess(ImportExportRepository.ImportExport importExport) {
                //called on background thread

                if (callbacks != null) {
                    callbacks.onSuccess(importExport);
                }
            }

            @Override
            public void onNothingToImportExport(ImportExportRepository.ImportExport importExport) {
                //called on background thread

                if (callbacks != null) {
                    callbacks.showToast("There is nothing to export");
                }
            }

            @Override
            public void onError(Throwable error, ImportExportRepository.ImportExport importExport) {
                //called on background thread

                if (callbacks != null) {
                    callbacks.showToast("Error while trying to export settings = " + error.getMessage());
                }
            }
        });
    }

    public void doImport(File cacheDir) {
        importExportRepository.importFrom(cacheDir, new ImportExportRepository.ImportExportCallbacks() {
            @Override
            public void onSuccess(ImportExportRepository.ImportExport importExport) {
                //called on background thread

                if (callbacks != null) {
                    callbacks.onSuccess(importExport);
                }
            }

            @Override
            public void onNothingToImportExport(ImportExportRepository.ImportExport importExport) {
                //called on background thread

                if (callbacks != null) {
                    callbacks.showToast("There is nothing to import");
                }
            }

            @Override
            public void onError(Throwable error, ImportExportRepository.ImportExport importExport) {
                //called on background thread

                if (callbacks != null) {
                    callbacks.showToast("Error while trying to import settings = " + error.getMessage());
                }
            }
        });
    }

    public interface ImportExportSettingsCallbacks {
        void onSuccess(ImportExportRepository.ImportExport importExport);

        void showToast(String message);
    }
}

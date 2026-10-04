package com.ascode.android.keystore;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.List;

import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import mod.hey.studios.util.Helper;
import com.ascode.android.R;
import com.ascode.android.databinding.ActivityKeystoreManagerBinding;
import com.ascode.android.databinding.DialogImportKeystoreBinding;
import com.ascode.android.databinding.ItemKeystoreBinding;
import com.ascode.android.utility.AscodeUtil;

/**
 * Settings → Keystore manager. Imports .jks/.keystore/.bks/.p12 files into the app's private
 * storage and remembers their alias/passwords (encrypted, {@link KeystoreStore}).
 */
public class KeystoreManagerActivity extends BaseAppCompatActivity {

    private ActivityKeystoreManagerBinding binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = ActivityKeystoreManagerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.topAppBar.setTitle(R.string.app_settings_keystore_manager);
        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));

        binding.importKeystore.setOnClickListener(v -> pickKeystoreFile());

        refreshList();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            refreshList();
        }
    }

    private void refreshList() {
        binding.keystoreContainer.removeAllViews();
        List<KeystoreStore.Entry> entries = KeystoreStore.list(this);

        binding.emptyHint.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (KeystoreStore.Entry entry : entries) {
            ItemKeystoreBinding item = ItemKeystoreBinding.inflate(inflater, binding.keystoreContainer, false);
            item.keystoreName.setText(entry.getName());
            item.keystoreAlias.setText(getString(R.string.auto_java_alias_label, entry.getAlias()));
            String fingerprint = KeystoreStore.certificateSha256(entry, this);
            item.keystoreFingerprint.setText(getString(R.string.auto_java_sha256_label, fingerprint));
            item.getRoot().setOnClickListener(v -> showEntryOptions(entry));
            binding.keystoreContainer.addView(item.getRoot());
        }
    }

    private void showEntryOptions(KeystoreStore.Entry entry) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(entry.getName())
                .setItems(new String[]{getString(R.string.auto_java_show_certificate), getString(R.string.common_word_delete)}, (dialog, which) -> {
                    if (which == 0) {
                        showCertificate(entry);
                    } else {
                        confirmDelete(entry);
                    }
                })
                .show();
    }

    private void showCertificate(KeystoreStore.Entry entry) {
        String sha256 = KeystoreStore.certificateSha256(entry, this);
        String subject = KeystoreStore.certificateSubject(entry, this);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.auto_java_certificate)
                .setMessage(getString(R.string.auto_java_certificate_msg, entry.getAlias(), subject, sha256, entry.file(this).getAbsolutePath()))
                .setPositiveButton(R.string.common_word_close, null)
                .show();
    }

    private void confirmDelete(KeystoreStore.Entry entry) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.auto_java_delete_named, entry.getName()))
                .setMessage(R.string.auto_java_delete_keystore_msg)
                .setPositiveButton(R.string.common_word_delete, (dialog, which) -> {
                    KeystoreStore.delete(this, entry.getId());
                    refreshList();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void pickKeystoreFile() {
        FilePickerOptions options = new FilePickerOptions();
        options.setExtensions(new String[]{"jks", "keystore", "bks", "p12", "pfx"});
        options.setTitle(getString(R.string.auto_java_select_keystore));

        FilePickerCallback callback = new FilePickerCallback() {
            @Override
            public void onFileSelected(File file) {
                showImportDialog(file);
            }
        };

        new FilePickerDialogFragment(options, callback).show(getSupportFragmentManager(), "keystore_picker");
    }

    private void showImportDialog(File file) {
        DialogImportKeystoreBinding dialogBinding = DialogImportKeystoreBinding.inflate(getLayoutInflater());
        dialogBinding.etName.setText(file.getName());
        dialogBinding.etAlgorithm.setText("SHA256withRSA");

        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.auto_java_import_named, file.getName()))
                .setView(dialogBinding.getRoot())
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.common_word_import, null);

        androidx.appcompat.app.AlertDialog alertDialog = dialog.create();
        alertDialog.show();
        // Validate first: on failure the dialog must stay open so the user can fix the input.
        alertDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = Helper.getText(dialogBinding.etName);
            String alias = Helper.getText(dialogBinding.etAlias);
            String storePassword = Helper.getText(dialogBinding.etStorePassword);
            String keyPassword = Helper.getText(dialogBinding.etKeyPassword);
            String algorithm = Helper.getText(dialogBinding.etAlgorithm);

            if (TextUtils.isEmpty(name)) {
                dialogBinding.tilName.setError(getString(R.string.auto_java_name_empty));
                return;
            }
            if (TextUtils.isEmpty(alias)) {
                dialogBinding.tilAlias.setError(getString(R.string.auto_java_alias_empty));
                return;
            }
            if (TextUtils.isEmpty(storePassword)) {
                dialogBinding.tilStorePassword.setError(getString(R.string.auto_java_password_empty));
                return;
            }
            if (TextUtils.isEmpty(keyPassword)) {
                dialogBinding.tilKeyPassword.setError(getString(R.string.auto_java_password_empty));
                return;
            }

            KeystoreStore.Entry entry = KeystoreStore.importKeystore(this, name, alias, algorithm,
                    storePassword, keyPassword, file);
            if (entry == null) {
                AscodeUtil.toastError(getString(R.string.auto_java_keystore_copy_failed));
                return;
            }

            String result = KeystoreStore.certificateSha256(entry, this);
            if (result.contains(":")) {
                alertDialog.dismiss();
                AscodeUtil.toast(getString(R.string.auto_java_keystore_imported));
                refreshList();
            } else {
                // Credentials are wrong: don't keep a broken entry around.
                KeystoreStore.delete(this, entry.getId());
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.auto_java_keystore_read_failed)
                        .setMessage(result)
                        .setPositiveButton(R.string.auto_java_okay, null)
                        .show();
            }
        });
    }
}

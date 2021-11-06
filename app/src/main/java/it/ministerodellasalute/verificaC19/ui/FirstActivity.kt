/*
 *  ---license-start
 *  eu-digital-green-certificates / dgca-verifier-app-android
 *  ---
 *  Copyright (C) 2021 T-Systems International GmbH and all other contributors
 *  ---
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  ---license-end
 *
 */

package it.ministerodellasalute.verificaC19.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.observe
import dagger.hilt.android.AndroidEntryPoint
import it.ministerodellasalute.verificaC19.BuildConfig
import it.ministerodellasalute.verificaC19.R
import it.ministerodellasalute.verificaC19.VerificaApplication
import it.ministerodellasalute.verificaC19.databinding.ActivityFirstBinding
import it.ministerodellasalute.verificaC19.ui.main.MainActivity
import it.ministerodellasalute.verificaC19sdk.model.FirstViewModel
import it.ministerodellasalute.verificaC19sdk.util.ConversionUtility
import it.ministerodellasalute.verificaC19sdk.util.FORMATTED_DATE_LAST_SYNC
import it.ministerodellasalute.verificaC19sdk.util.TimeUtility.parseTo
import it.ministerodellasalute.verificaC19sdk.util.Utility


@AndroidEntryPoint
class FirstActivity : AppCompatActivity(), View.OnClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var binding: ActivityFirstBinding
    private lateinit var shared: SharedPreferences

    private val viewModel by viewModels<FirstViewModel>()

    //private var totalChunksSize = 0L

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                openQrCodeReader()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        binding = ActivityFirstBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.qrButton.setOnClickListener(this)
        binding.settings.setOnClickListener(this)

        val string = getString(R.string.version, BuildConfig.VERSION_NAME)
        val spannableString = SpannableString(string).also {
            it.setSpan(UnderlineSpan(), 0, it.length, 0)
            it.setSpan(StyleSpan(Typeface.BOLD), 0, it.length, 0)
        }
        binding.versionText.text = spannableString
        binding.dateLastSyncText.text = getString(R.string.loading)

        binding.updateProgressBar.max = viewModel.getTotalChunk().toInt()
        updateDownloadedPackagesCount()
        Log.i("viewModel.getauthorizedToDownload()", viewModel.getDownloadAvailable().toString())
        viewModel.getDownloadAvailable().let { isAuthorizedToDownload ->

            if (isAuthorizedToDownload == 0L && !viewModel.getIsPendingDownload())
                binding.initDownload.visibility = View.VISIBLE
            else {
                binding.initDownload.visibility = View.GONE
            }
        }
        Log.i("viewModel.getAuthResume()", viewModel.getResumeAvailable().toString())

        viewModel.getResumeAvailable().let {
            if (it == 0.toLong() || viewModel.getIsPendingDownload()) {
                binding.resumeDownload.visibility = View.VISIBLE
                binding.dateLastSyncText.text = getString(R.string.incompleteDownload)
                binding.chunkCount.visibility = View.VISIBLE
                binding.chunkSize.visibility = View.VISIBLE
                binding.updateProgressBar.visibility = View.VISIBLE
            } else {
                binding.resumeDownload.visibility = View.GONE
            }
        }

        shared = this.getSharedPreferences("dgca.verifier.app.pref", Context.MODE_PRIVATE)
        Log.i("Shared Preferences Info", shared.toString())

        viewModel.fetchStatus.observe(this) {
            if (it) {
                binding.qrButton.isEnabled = false
                binding.qrButton.background.alpha = 128
            } else {
                binding.qrButton.isEnabled = true
                if (!viewModel.getIsPendingDownload()) {
                    viewModel.getDateLastSync().let { date ->
                        binding.dateLastSyncText.text = getString(
                            R.string.lastSyncDate,
                            if (date == -1L) getString(R.string.notAvailable) else date.parseTo(
                                FORMATTED_DATE_LAST_SYNC
                            )
                        )
                    }
                    binding.qrButton.background.alpha = 255
                    binding.updateProgressBar.visibility = View.GONE
                    binding.chunkCount.visibility = View.GONE
                    binding.chunkSize.visibility = View.GONE
                }
            }
        }
        binding.privacyPolicyCard.setOnClickListener {
            val browserIntent =
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.dgc.gov.it/web/pn.html"))
            startActivity(browserIntent)
        }
        binding.faqCard.setOnClickListener {
            val browserIntent =
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.dgc.gov.it/web/faq.html"))
            startActivity(browserIntent)
        }
        binding.initDownload.setOnClickListener {
            viewModel.setShouldInitDownload(true)
            viewModel.setDownloadAsAvailable()
            binding.initDownload.visibility = View.GONE
            binding.dateLastSyncText.text = getString(R.string.updatingRevokedPass)
            val verificaApplication = VerificaApplication()
            verificaApplication.setWorkManager()
        }

        binding.resumeDownload.setOnClickListener {
            viewModel.setResumeAsAvailable()
            binding.resumeDownload.visibility = View.GONE
            binding.dateLastSyncText.text = getString(R.string.updatingRevokedPass)
            val verificaApplication = VerificaApplication()
            verificaApplication.setWorkManager()
        }


    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_DENIED
        ) {
            createPermissionAlert()
        } else {
            openQrCodeReader()
        }
    }

    private fun createPermissionAlert() {
        try {
            val builder = AlertDialog.Builder(this)
            builder.setTitle(getString(R.string.privacyTitle))
            builder.setMessage(getString(R.string.privacy))
            builder.setPositiveButton(getString(R.string.next)) { dialog, which ->
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            builder.setNegativeButton(getString(R.string.back)) { dialog, which ->
            }
            val dialog = builder.create()
            dialog.show()
        } catch (e: Exception) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun createDownloadAlert() {
        try {
            val builder = AlertDialog.Builder(this)
            var dialog: AlertDialog? = null
            builder.setTitle(
                getString(
                    R.string.titleDownloadAlert,
                    ConversionUtility.byteToMegaByte(viewModel.getTotalSizeInByte().toFloat())
                )
            )
            builder.setMessage(getString(R.string.messageDownloadAlert))
            builder.setPositiveButton(getString(R.string.label_download)) { _, _ ->
                dialog?.dismiss()
                val verificaApplication = VerificaApplication()
                verificaApplication.setWorkManager()
            }
            builder.setNegativeButton(getString(R.string.after_download)) { _, _ ->
                binding.resumeDownload.visibility = View.GONE
                binding.initDownload.visibility = View.VISIBLE
                binding.dateLastSyncText.text =
                    getString(
                        R.string.titleDownloadAlert,
                        ConversionUtility.byteToMegaByte(viewModel.getTotalSizeInByte().toFloat())
                    )
                dialog?.dismiss()
            }
            dialog = builder.create()
            dialog.show()
        } catch (e: Exception) {
        }
    }


    override fun onResume() {
        super.onResume()
        viewModel.getAppMinVersion().let {
            if (Utility.versionCompare(
                    it,
                    BuildConfig.VERSION_NAME
                ) > 0 || viewModel.isSDKVersionObsoleted()
            ) {
                createForceUpdateDialog()
            }
        }
    }

    private fun openQrCodeReader() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    override fun onClick(v: View?) {
        viewModel.getDateLastSync().let {
            if (it == -1L) {
                createNoSyncAlertDialog(getString(R.string.noKeyAlertMessage))
                return
            }
        }
        viewModel.getDrlDateLastSync().let {
            if (viewModel.getIsDrlSyncActive() && System.currentTimeMillis() >= it + 24 * 60 * 60 * 1000) {
                createNoSyncAlertDialog(getString(R.string.noKeyAlertMessageForDrl))
                return
            }
        }
        when (v?.id) {
            R.id.qrButton -> checkCameraPermission()
            R.id.settings -> openSettings()
        }
    }

    private fun createNoSyncAlertDialog(alertMessage: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.noKeyAlertTitle))
        builder.setMessage(alertMessage)
        builder.setPositiveButton(getString(R.string.ok)) { dialog, which ->
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun createForceUpdateDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.updateTitle))
        builder.setMessage(getString(R.string.updateMessage))

        builder.setPositiveButton(getString(R.string.updateLabel)) { dialog, which ->
            openGooglePlay()
        }
        val dialog = builder.create()
        dialog.setCancelable(false)
        dialog.show()
    }

    private fun openGooglePlay() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (e: ActivityNotFoundException) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                )
            )
        }
    }

    override fun onStart() {
        super.onStart()
        shared.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key != null) {
            if (key == "last_downloaded_chunk") {
                updateDownloadedPackagesCount()
                Log.i(key.toString(), viewModel.getLastDownloadedChunk().toString())
            }
            if (key == "total_chunk") {
                val totalChunk = viewModel.getTotalChunk().toInt()
                binding.updateProgressBar.max = totalChunk
                binding.updateProgressBar.visibility = View.VISIBLE
                binding.chunkCount.visibility = View.VISIBLE
                binding.chunkSize.visibility = View.VISIBLE
                updateDownloadedPackagesCount()
                Log.i("total_chunk", totalChunk.toString())
            }
            if (key == "auth_to_resume") {
                val authToResume = viewModel.getResumeAvailable().toInt()
                Log.i("auth_to_resume", authToResume.toString())

                if (viewModel.getResumeAvailable() == 0L) {
                    binding.resumeDownload.visibility = View.VISIBLE
                }
            }
            if (key == "size_over_thresold") {
                val isSizeOverThresold = viewModel.getIsSizeOverThreshold()
                if (isSizeOverThresold) {
                    createDownloadAlert()
                }
            }
            if (key == "drl_date_last_fetch") {
                viewModel.getDateLastSync().let { date ->
                    binding.dateLastSyncText.text = getString(
                        R.string.lastSyncDate,
                        if (date == -1L) getString(R.string.notAvailable) else date.parseTo(
                            FORMATTED_DATE_LAST_SYNC
                        )
                    )
                }
                binding.updateProgressBar.visibility = View.GONE
                binding.chunkCount.visibility = View.GONE
                binding.chunkSize.visibility = View.GONE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        shared.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun updateDownloadedPackagesCount() {
        val lastDownloadedChunk = viewModel.getLastDownloadedChunk().toInt()
        val lastChunk = viewModel.getTotalChunk().toInt()
        val singleChunkSize = viewModel.getSizeSingleChunkInByte()

        binding.updateProgressBar.progress = lastDownloadedChunk
        binding.chunkCount.text = getString(R.string.chunk_count, lastDownloadedChunk, lastChunk)
        binding.chunkSize.text = getString(
            R.string.chunk_size,
            ConversionUtility.byteToMegaByte((lastDownloadedChunk * singleChunkSize.toFloat())),
            ConversionUtility.byteToMegaByte(viewModel.getTotalSizeInByte().toFloat())
        )
    }
}

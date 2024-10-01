package com.letstalk.dopplee.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.letstalk.dopplee.databinding.FragmentHomeBinding
//import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null

    private var outputFilePath: String = ""
//    private lateinit var tflite: Interpreter

    private val handler = Handler(Looper.getMainLooper())
    private var recordingStartTime = 0L

    private val updateRecordingTimeRunnable = object : Runnable {
        override fun run() {
            val elapsedTime = System.currentTimeMillis() - recordingStartTime
            val seconds = (elapsedTime / 1000).toInt() % 60
            val minutes = (elapsedTime / (1000 * 60) % 60).toInt()
            binding.recordingTimeTextView.text = String.format("录音时间: %02d:%02d", minutes, seconds)
            handler.postDelayed(this, 1000)
        }
    }

    companion object {
        const val TAG = "HomeFragment"

        val MANDATORY_PERMISSIONS: Array<String> = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
        )
        private const val PERMISSION_REQUEST = 33003
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

//        startRecordingButton = findViewById(R.id.startRecordingButton)
//        stopRecordingButton = findViewById(R.id.stopRecordingButton)

        binding.startRecordingButton.setOnClickListener {
            Log.d(TAG, "startRecordingButton called()-1")
            if (checkPermissions()) {
                Log.d(TAG, "startRecordingButton called()-2")
                startRecording()
            } else {
                checkPermissions()
                Log.d(TAG, "startRecordingButton called()-3")
            }
        }

        binding.stopRecordingButton.setOnClickListener {
            stopRecording()
        }

        binding.playRecordingButton.setOnClickListener {
            togglePlayStop()
        }

//        binding.analyzeRecordingButton.setOnClickListener {
//            if (outputFilePath.isNotEmpty()) {
//                val hasHumanVoice = analyzeRecording(outputFilePath)
//                binding.recordInfoTextView.text = if (hasHumanVoice) "检测到人声" else "未检测到人声"
//            }
//        }

//        try {
//            tflite = Interpreter(loadModelFile())
//        } catch (e: IOException) {
//            e.printStackTrace()
//        }

        val textView: TextView = binding.textHome
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    private fun startRecording() {
        Log.d(TAG, "startRecording called()-1")
        outputFilePath = "${requireActivity().externalCacheDir?.absolutePath}/audiorecord.3gp"

        Log.d(TAG, "startRecording called()-2")
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(outputFilePath)

            try {
                prepare()
                Log.d(TAG, "startRecording called()-3")
                start()
                Log.d(TAG, "startRecording called()-4")
                binding.startRecordingButton.visibility = Button.GONE
                binding.stopRecordingButton.visibility = Button.VISIBLE
                binding.playRecordingButton.visibility = Button.GONE
                binding.recordInfoTextView.visibility = TextView.GONE
                binding.recordingTimeTextView.visibility = TextView.VISIBLE

                // 启动计时器
                recordingStartTime = System.currentTimeMillis()
                handler.post(updateRecordingTimeRunnable)
            } catch (e: IOException) {
                Log.d(TAG, "startRecording called()-5, exception:${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null

        binding.startRecordingButton.visibility = Button.VISIBLE
        binding.stopRecordingButton.visibility = Button.GONE
        binding.playRecordingButton.visibility = Button.VISIBLE

        // 停止计时器
        handler.removeCallbacks(updateRecordingTimeRunnable)

        // Display recording info
        displayRecordingInfo(outputFilePath)
    }

    private fun togglePlayStop() {
        if (mediaPlayer == null) {
            startPlayback()
        } else {
            stopPlayback()
        }
    }

    private fun startPlayback() {
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(outputFilePath)
                prepare()
                start()
                binding.playRecordingButton.text = "Stop Playback"
                setOnCompletionListener {
                    stopPlayback()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
        binding.playRecordingButton.text = "Play Recording"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startRecording()
        }
    }

    private fun displayRecordingInfo(filePath: String) {
        val file = File(filePath)
        val fileSize = file.length() // in bytes
        val duration = getAudioDuration(filePath) // in milliseconds

        val infoText = StringBuilder().apply {
            append("File Path: $filePath\n")
            append("File Size: ${fileSize / 1024} KB\n")
            append("Duration: ${duration / 1000} seconds\n")
        }.toString()

        binding.recordInfoTextView.text = infoText
        binding.recordInfoTextView.visibility = TextView.VISIBLE
    }

    private fun getAudioDuration(filePath: String): Long {
        val mediaPlayer = MediaPlayer()
        return try {
            mediaPlayer.setDataSource(filePath)
            mediaPlayer.prepare()
            val duration = mediaPlayer.duration.toLong()
            mediaPlayer.release()
            duration
        } catch (e: IOException) {
            e.printStackTrace()
            0L
        }
    }

    private fun checkPermissions() : Boolean {
        val permissionsToRequest = mutableListOf<String>()
        var isAllGranted = true
        for (permission in MANDATORY_PERMISSIONS) {
            // 检查权限是否已经授予
            if (ContextCompat.checkSelfPermission(requireActivity(), permission) != PackageManager.PERMISSION_GRANTED) {
                // 如果权限没有授予，先检查是否需要解释原因
                if (shouldShowRequestPermissionRationale(permission)) {
                    Log.d(TAG, "checkSelfPermission: shouldShowRequestPermissionRationale $permission")
                    // 在这里可以显示 UI 解释为什么需要该权限
                } else {
                    Log.d(TAG, "checkSelfPermission: requesting $permission")
                    // 将需要请求的权限加入到请求列表
                }
                permissionsToRequest.add(permission)
                isAllGranted = false
            } else {
                Log.d(TAG, "checkSelfPermission: PERMISSION_GRANTED $permission")
            }
        }

        // 请求权限
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(permissionsToRequest.toTypedArray(), PERMISSION_REQUEST)
        }
        return isAllGranted
    }

//    private fun loadModelFile(): MappedByteBuffer {
//        val assetManager = assets
//        val fileDescriptor = assetManager.openFd("vad_model.tflite")
//        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
//        val fileChannel = inputStream.channel
//        val startOffset = fileDescriptor.startOffset
//        val declaredLength = fileDescriptor.declaredLength
//        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
//    }

//    private fun analyzeRecording(filePath: String): Boolean {
//        // 使用 TarsosDSP 提取音频特征
//        val inputBuffer = extractFeatures(filePath)
//        val outputBuffer = Array(1) { FloatArray(1) }
//
//        tflite.run(inputBuffer, outputBuffer)
//
//        return outputBuffer[0][0] > 0.5 // 假设输出为1表示有人声，0表示无
//    }

//    private fun extractFeatures(filePath: String): FloatArray {
//        val sampleRate = 16000 // 假设采样率为16kHz
//        val bufferSize = 1024 // 假设缓冲区大小为1024
//        val bufferOverlap = 512 // 假设缓冲区重叠为512
//
//        val dispatcher = AudioDispatcherFactory.fromPipe(filePath, sampleRate, bufferSize, bufferOverlap)
//        val mfcc = MFCC(bufferSize, sampleRate, 13, 20, 300.0, 8000.0)
//
//        dispatcher.addAudioProcessor(mfcc)
//
//        val features = mutableListOf<FloatArray>()
//
//        dispatcher.run {
//            while (dispatcher.isRunning) {
//                val mfccValues = mfcc.mfcc
//                features.add(mfccValues)
//            }
//        }
//
//        return features.flatten().toFloatArray()
//    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
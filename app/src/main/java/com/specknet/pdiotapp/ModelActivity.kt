
package com.specknet.pdiotapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.RESpeckLiveData
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.scheduleAtFixedRate

import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


class ModelActivity : AppCompatActivity() {
    var TAG = "ModelActivity"

    // TODO 2.1: defines the model to be used
    var modelPath = "t_c2_res_accel_1017.tflite"

//    // TODO 2.2: defining the minimum threshold
//    var probabilityThreshold: Float = 0.3f

    var respeckPool = ArrayList<RESpeckLiveData>();
    lateinit var respeckReceiver: BroadcastReceiver

    lateinit var textView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model)

//        val REQUEST_RECORD_AUDIO = 1337
//        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)

        // TODO GET LIVE DATA
        respeckReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                val action = intent.action

                if (action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {

                    val liveData = intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
//                    Log.d("Live", "onReceive: liveData = " + liveData)
                    respeckPool.add(liveData);
                    if (respeckPool.size >= 25) {
                        val windowed_respeck_data = ArrayList(respeckPool);
                        respeckPool.clear();
                        // TODO
                        // some function to run model inference
                    }
//                    updateRespeckData(liveData)

//                    respeckOn = true

                }

            }
        }
    }
}



class MyTFLiteInference(context: Context) {

    private var interpreter: Interpreter

    init {
        // 初始化 TensorFlow Lite 解释器
        interpreter = Interpreter(loadModelFile(context))
    }

    // 加载模型文件
    private fun loadModelFile(context: Context): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd("your_model.tflite") // 替换为你的模型文件名
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // 执行推理
    fun runInference(inputData: FloatArray): FloatArray {
        val inputBuffer = ByteBuffer.allocateDirect(4 * inputData.size)
        inputBuffer.order(ByteOrder.nativeOrder())
        inputBuffer.rewind()
        inputBuffer.asFloatBuffer().put(inputData)

        val outputBuffer = ByteBuffer.allocateDirect(4 * outputSize) // 替换为你的输出张量大小

        interpreter.run(inputBuffer, outputBuffer)

        val outputData = FloatArray(outputSize)
        outputBuffer.rewind()
        outputBuffer.asFloatBuffer().get(outputData)

        return outputData
    }

    // 关闭 TensorFlow Lite 解释器
    fun close() {
        interpreter.close()
    }
}

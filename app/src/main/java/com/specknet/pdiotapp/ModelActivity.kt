
package com.specknet.pdiotapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
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

    var respeckPool = ArrayList<FloatArray>();
    lateinit var respeckReceiver: BroadcastReceiver
    val filterTestRespeck = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)
    lateinit var looperRespeck: Looper

    lateinit var textView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model)
        textView = findViewById<TextView>(R.id.output)
//        val outputStr = "fuck you";
//                        runOnUiThread { textView.text = "Predicted class: $maxIndex" }
//        runOnUiThread { textView.text = outputStr }
        Log.i("yes", "what")
        // GET LIVE DATA
        respeckReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                println("received")
                Log.i("yes", "received")
                val action = intent.action

//                val tempStr = "received";
//                runOnUiThread { textView.text = tempStr }

                if (action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {

                    val liveData =
                        intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
//                    Log.d("Live", "onReceive: liveData = " + liveData)
                    // TODO convert to xyz float only
                    val accelData = floatArrayOf(liveData.accelX, liveData.accelY, liveData.accelZ)
                    respeckPool.add(accelData);
//                    val tempStr = respeckPool.size.toString();
//                    runOnUiThread { textView.text = tempStr }
                    if (respeckPool.size >= 25) {
                        // Convert ArrayList<FloatArray> to 25x3 float array
                        val array2D = Array(25) { FloatArray(3) }
                        for (i in 0..24) {
                            array2D[i] = respeckPool[i]
                        }
                        // Clear respeckPool
                        respeckPool.clear()

                        val myTFLite = MyTFLiteInference(context)  // initialize your inference class

                        val outputData = myTFLite.runInference(array2D)  // directly pass your 25x3 2D array
                        // TODO: display max index
                        // Find the index of the maximum value in the outputData
                        val maxIndex = outputData.indices.maxByOrNull { outputData[it] } ?: -1

                        // Assuming you just want to display the index
                        val outputStr = "Predicted class: $maxIndex";
//                        runOnUiThread { textView.text = "Predicted class: $maxIndex" }
                        runOnUiThread { textView.text = outputStr }
                        // If you have an array of labels corresponding to the classes,
                        // you can display the corresponding label instead of the index.
                        // For example:
                        // val labels = arrayOf("Label1", "Label2", ... , "Label44")
                        // textView.text = "Predicted class: ${labels[maxIndex]}"

                    }
                }

            }
        }
        // register receiver on another thread
        val handlerThreadRespeck = HandlerThread("bgThreadRespeckLive")
        handlerThreadRespeck.start()
        looperRespeck = handlerThreadRespeck.looper
        val handlerRespeck = Handler(looperRespeck)
        this.registerReceiver(respeckReceiver, filterTestRespeck, null, handlerRespeck);
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
        val assetFileDescriptor = context.assets.openFd("t_c2_res_accel_1017.tflite") // 替换为你的模型文件名
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // 执行推理
    fun runInference(inputData: Array<FloatArray>): FloatArray {
        val inputBuffer = ByteBuffer.allocateDirect(4 * 25 * 3)
        inputBuffer.order(ByteOrder.nativeOrder())

        // Converting 2D array data into ByteBuffer format
        for (i in inputData.indices) {
            for (j in inputData[i].indices) {
                inputBuffer.putFloat(inputData[i][j])
            }
        }

        val outputBuffer = ByteBuffer.allocateDirect(4 * 44) // Assuming your output tensor remains the same size

        interpreter.run(inputBuffer, outputBuffer)

        val outputData = FloatArray(44)
        outputBuffer.rewind()
        outputBuffer.asFloatBuffer().get(outputData)

        return outputData
    }


    // 关闭 TensorFlow Lite 解释器
    fun close() {
        interpreter.close()
    }
}

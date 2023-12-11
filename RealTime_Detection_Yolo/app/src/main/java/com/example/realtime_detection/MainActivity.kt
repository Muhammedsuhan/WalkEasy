package com.example.realtime_detection

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.location.LocationListener
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.preference.PreferenceManager
import android.util.Log
import android.util.Range
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.realtime_detection.ml.SsdMobilenetV11Metadata1
import com.example.realtime_detection.ml.Yolov5sFp16320Metadata
import net.majorkernelpanic.streaming.SessionBuilder
import net.majorkernelpanic.streaming.gl.SurfaceView
import net.majorkernelpanic.streaming.rtsp.RtspServer
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    lateinit var bitMap: Bitmap;
    lateinit var imageView: ImageView;
    lateinit var textureView : TextureView;
    lateinit var cameraManager: CameraManager;
    lateinit var handler: Handler;
    lateinit var cameraDevice: CameraDevice;



    lateinit var ssd_model : SsdMobilenetV11Metadata1;
    lateinit var ssd_imageProcessor : ImageProcessor;
    lateinit var ssd_labels : List<String>;

    lateinit var yolo_model : Yolov5sFp16320Metadata;
    lateinit var yolo_imageProcessor : ImageProcessor;
    lateinit var yolo_labels : List<String>;

//    var client : TCP_Socket_Client = TCP_Socket_Client();
//    var client : UDP_Socket_Client = UDP_Socket_Client();
    val minAccuracy:Double = 0.5;
    val paint = Paint();
    val colors = listOf<Int>(Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK, Color.DKGRAY,
        Color.MAGENTA, Color.YELLOW);

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView);
        textureView = findViewById(R.id.textureView);

        ssd_model = SsdMobilenetV11Metadata1.newInstance(this@MainActivity)
        ssd_imageProcessor = ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build();
        ssd_labels = FileUtil.loadLabels(this, "labels.txt");

        yolo_model = Yolov5sFp16320Metadata.newInstance(this@MainActivity)
        yolo_labels = FileUtil.loadLabels(this, "coco_label.txt");
        yolo_imageProcessor = ImageProcessor.Builder().add(ResizeOp(320, 320, ResizeOp.ResizeMethod.BILINEAR)).build();


        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                //when available we just need to open camera
                openCamera();
            }
            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {
            }
            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false;
            }
            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                // grab the image
                bitMap = textureView.bitmap!!;

                // Creates inputs for reference.
                var image = TensorImage.fromBitmap(bitMap)
                // resize the image using the image processor because the ssd_model expects a 300*300 image
                image = yolo_imageProcessor.process(image);
//                image = ssd_imageProcessor.process(image);

//                client.addToQueue(image);

//                image = ssd_predict(image);
                image = yolo_predict(image);
                imageView.setImageBitmap(image.bitmap);
            }
        }

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager;
        val handlerThread = HandlerThread("streamThread");
        handlerThread.start();
        handler = Handler(handlerThread.looper);
//        client.run();

    }

    override fun onStop() {
        super.onStop()
        ssd_model.close();
//        client.socket?.close();
//        client.executor.shutdownNow()
        Log.d("StreamTag", "############# Executor stopped");
    }

    private fun yolo_predict(image: TensorImage): TensorImage? {
        var mutableBitMap = bitMap.copy(Bitmap.Config.ARGB_8888, true);

        // Runs model inference and gets result.
        val outputs = yolo_model.process(image)
        val prediction = outputs.predictionAsTensorBuffer

        val outputShape = prediction.shape
        val outputData = Array(outputShape[1]) { i ->
            FloatArray(outputShape[2]) { j ->
                prediction.getFloatValue(i * outputShape[2] + j)
            }
        }

        val imageHeight = mutableBitMap.height;
        val imageWidth = mutableBitMap.width;

        val rows = outputData.size;
        val columns = outputData[0].size;
        var canvas = Canvas(mutableBitMap);

        outputData.forEachIndexed { i, row ->
            // if the confidence score is less than 50% then most likely there's any object in the current kernel just skip it
            if(row.get(4) < 0.5) return@forEachIndexed

            val centerX = row.get(0) * imageWidth
            val centerY = row.get(1) * imageHeight
            val boxWidth = row.get(2) * imageWidth
            val boxHeight = row.get(3) * imageHeight

            val left = maxOf(0.0F, centerX - boxWidth/2);
            val top = maxOf(0.0F, centerY - boxHeight/2);
            val right = maxOf(0.0F, centerX + boxWidth/2);
            val bottom = maxOf(0.0F, centerY + boxHeight/2);

            val classProbabilities = row.sliceArray(5 until 85)
            val maxProbabilityIndex = classProbabilities.indices.maxByOrNull { classProbabilities[it] } ?: -1
            val score = classProbabilities.get(maxProbabilityIndex);


            paint.setColor(colors.get(i%9))
            paint.style = Paint.Style.STROKE;
            paint.textSize = imageHeight/15f;
            canvas.drawRect(RectF(left, top, right, bottom), paint);
            paint.style = Paint.Style.FILL;
            canvas.drawText(ssd_labels.get(maxProbabilityIndex) + " " + score.toString(), left, top, paint);
        }


        return TensorImage.fromBitmap(mutableBitMap)
    }


    private fun ssd_predict(image: TensorImage): TensorImage? {

        // Runs ssd_model inference and gets result.
        val outputs = ssd_model.process(image)
        val locations = outputs.locationsAsTensorBuffer.floatArray
        val classes = outputs.classesAsTensorBuffer.intArray
        val scores = outputs.scoresAsTensorBuffer.floatArray
        val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer.intArray

        var mutableBitMap = bitMap.copy(Bitmap.Config.ARGB_8888, true);
        var canvas = Canvas(mutableBitMap);

        val h = mutableBitMap.height;
        val w = mutableBitMap.width;

        paint.textSize = h/15f;


        var x = 0;
        scores.forEachIndexed { i, score ->
            // each 4 consecutive numbers in locations array correspond to same classified object (top, left, bottom, right]
            // so we will increment x by 4 times the index each time
            x = i * 4;
            if(score > minAccuracy){
                paint.setColor(colors.get(i))

                paint.style = Paint.Style.STROKE;
                // this the RectF(float left, float top, float right, float bottom) constructor
                // and this the locations array [top, left, bottom, right] also the values are float between 0 and 1
                // so they need to be scaled to the full width and height of the actual bitMap on the screen
                // for every left and right we will scale it by width and for every top and bottom scale by height
                // so we need to reorder and scale the locations array for the Rectf constructor
                val left = locations.get(x+1)*w;
                val top =  locations.get(x)*h;
                val bottom = locations.get(x+3)*w;
                val right =  locations.get(x+2)*h;
                canvas.drawRect(RectF(left, top, right, bottom), paint);

                paint.style = Paint.Style.FILL;
                canvas.drawText(ssd_labels.get(classes.get(i)) + " " + score.toString(), left, top, paint);
            }
        }
        return TensorImage.fromBitmap(mutableBitMap)
    }

    private fun openCamera() {
        // Request permission first if not granted yet
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101);
        }

        cameraManager.openCamera(cameraManager.cameraIdList[0], object: CameraDevice.StateCallback() {
            override fun onOpened(p0: CameraDevice) {
                //save the cameraDevice in the current activity state (for later) as soon as it gets passed
                cameraDevice = p0;

                var surface = Surface(textureView.surfaceTexture);

                var captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                captureRequest.set(CaptureRequest.JPEG_QUALITY, 10)
                val range = Range(24, 24)
                captureRequest.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range)
                captureRequest.addTarget(surface);

                cameraDevice.createCaptureSession(listOf(surface), object:CameraCaptureSession.StateCallback(){
                    override fun onConfigured(p0: CameraCaptureSession) {
                        p0.setRepeatingRequest(captureRequest.build(), null, null);
                    }

                    override fun onConfigureFailed(p0: CameraCaptureSession) {
                    }

                }, handler)
            }
            override fun onDisconnected(p0: CameraDevice) {
                TODO("Not yet implemented")
            }
            override fun onError(p0: CameraDevice, p1: Int) {
                TODO("Not yet implemented")
            }
        }, handler)
    }


    // this is just the call back for the permissions requested
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(grantResults[0] != PackageManager.PERMISSION_GRANTED){
            Toast.makeText(this, "permission not granted", Toast.LENGTH_SHORT).show();
        }
    }


}

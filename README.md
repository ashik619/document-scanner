# document-scanner
Document scanner for android using OpenCV (without the manager app)
Uses OpenCV 3.0
JNI files openCV library is available for all Core Architecture("armeabi", "armeabi-v7a", "arm64-v8a", "x86", "x86_64","MIPS")

# Usage
Start Scan
```
Intent intent = new Intent(context,ScanActivity.class);
intent.putExtra("path",photoPath); // photo patth is input image file
startActivityForResult(intent,SCAN_REQ);
```
Result
```
@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == SCAN_REQ&&resultCode == RESULT_OK){
            //scanned image availble at photoPath
        }
}

PHOTO SHEET MAKER - MEGESTIC MEMORIES (ANDROID STUDIO PROJECT)

This project keeps the existing HTML photo-sheet interface and adds a more reliable Android WebView wrapper.

FIXES INCLUDED
1. Multi-photo picker: uses ACTION_OPEN_DOCUMENT and the actual WebView file chooser mode. Multi-select is enabled for the HTML input marked `multiple`; the single-photo replacement input stays single-select.
2. URI handling: reads both ClipData (multi-select) and the single data Uri, removes duplicates, and returns null cleanly when the picker is cancelled.
3. Image saving: JPEG, PNG and WebP are decoded and saved off the UI thread. On Android 10+ the file is saved to the shared Pictures/Photo Sheet Maker folder through MediaStore, with IS_PENDING used until writing finishes.
4. Save feedback: the HTML page waits for the native Android save result before showing success. Failures are returned to the page and shown to the user.
5. Printing and existing HTML layout/features are retained.

BUILD THE APK
1. Extract this ZIP on your Windows PC.
2. Install Android Studio (if not already installed).
3. In Android Studio choose Open, then select this extracted project folder (the folder containing settings.gradle).
4. Allow Android Studio to install the required Android Gradle Plugin / SDK components and finish Gradle Sync.
5. Select Build > Build Bundle(s) / APK(s) > Build APK(s).
6. When the build finishes, use the APK link shown by Android Studio. The usual output is app/build/outputs/apk/debug/app-debug.apk.

WHERE EXPORTED IMAGES ARE SAVED
- Android 10 and newer: Gallery / Photos or Files > Pictures > Photo Sheet Maker.
- Android 6 to Android 9: app-specific storage at Android/data/com.megesticmemories.photosheetmaker/files/Pictures/Photo Sheet Maker. This older-Android fallback avoids requesting broad storage permission.

IMPORTANT TESTS ON A REAL PHONE
1. Open Browse & Select Photos and select 3-5 photos in one picker action. Confirm all selected photos appear in separate slots.
2. Fill some slots, then tap an empty slot and select one replacement photo. Confirm only that slot changes.
3. Export once as JPEG and once as PNG. Wait for the explicit “Image saved successfully” message; then check the folder above.
4. Open the saved files and confirm they are not zero-byte or corrupted.
5. Test print preview if printing is needed.

BUILD / TEST LIMITATION
The supplied source project was updated and source-level checks were performed. This workspace does not have the Android SDK or Gradle installed, so an APK could not be compiled or device-tested here. Android Studio must perform the final Gradle build on your PC.

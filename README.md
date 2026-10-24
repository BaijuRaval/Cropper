# Cropper 📄

An Android app concept for arranging multiple photos onto print-ready A4 sheets.

## Current MVP
- Pick multiple images from Android's document/photo picker.
- Preview selected images and remove any before printing.
- Automatically fit images into a clean two-column A4 layout (up to 10 images per page).
- Generate a PDF and open Android's native print dialog.

## Build
Open this repository in Android Studio (Giraffe or newer), allow Gradle sync, then run the `app` configuration on an Android device or emulator.

## Planned improvements
- Document/card edge detection and perspective correction for ID cards.
- A4 preview with drag, resize, rotate, spacing, and copies controls.
- Card presets (Aadhaar/PAN/passport photos) and print-density checks.
- Better handling for very large images and multi-page layouts.

## Privacy
Images are processed locally on-device. The app does not upload selected photos.

## Important
This initial version fits images into layout cells; it does not yet detect and crop card boundaries automatically. Review the sheet before printing.

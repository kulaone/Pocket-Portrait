# Pocket Portrait 📸🖨️

Pocket Portrait is an open-source Android application that transforms your photos into stylized monochrome art using Google's **Gemini API** and prints them directly to a portable **Bluetooth BLE Thermal Printer** in real time. 

Whether you want to capture a retro comic illustration, a vintage engraving, or 8-bit pixel art, Pocket Portrait processes the image, applies Floyd-Steinberg dithering for high-fidelity physical prints, and transmits the print data over Bluetooth Low Energy (BLE) to compatible pocket printers.

---

## ✨ Features

- **AI-Powered Portrait Stylization:** Leverage Google's `gemini-3.1-flash` model to transform portrait photos into high-contrast black-and-white art styles.
- **Built-in & Custom Styles:**
  - *Retro Comic Book:* Clean, bold black outlines and solid ink shadows.
  - *Vintage Engraving:* Hatching and crosshatched lines for a classic newspaper print.
  - *8-Bit Pixel Art:* Retro Game Boy cluster blocks.
  - *Cyberpunk Stencil:* High-contrast vector silhouettes.
  - *Fine Stippling:* Elegant pen-and-ink dotted shading.
  - *Custom Style Editor:* Draft, test, and save your own stylization prompts using the Gemini API.
- **Real-time Camera & Gallery Support:** Snap a new portrait using an integrated CameraX viewfinder or select an existing photo from the system gallery.
- **Hardware-Optimized Image Processing:**
  - **Contrast Stretching:** Automatically pre-normalizes exposure to prevent print washouts.
  - **Floyd-Steinberg Dithering:** A custom 1-bit error-diffusion dithering engine converts grayscale outputs into high-fidelity binary (black/white) pixel arrays.
- **Robust BLE Printer Integration:**
  - Automatic scanning and pairing with portable Bluetooth thermal receipt/photo printers.
  - Custom packaging of GATT print packets (384-pixel-wide line packing, packet headers, footer, and CRC-8 checksum verification).
  - Background print service with an active Android notification displaying transmission progress.
- **Secure On-Device Storage:** Uses Jetpack Security's `EncryptedSharedPreferences` to safely encrypt and store your Gemini API key and printer addresses on-device.

---

## 🏗️ Architecture & Tech Stack

The app is built using modern Android development practices:

- **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose) for a fully declarative and reactive UI.
- **Concurrency:** [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) & [StateFlow](https://kotlinlang.org/docs/flow.html) for responsive, asynchronous UI state management.
- **API Client:** OkHttp for fast, direct streaming communication with the Gemini Developer API.
- **Camera:** [CameraX](https://developer.android.com/training/camerax) for robust camera lifecycle management.
- **Database/Preferences:** [Jetpack Security (EncryptedSharedPreferences)](https://developer.android.com/topic/security/data) for secure data encryption at rest.

---

## 🚀 Getting Started

### Prerequisites

1. **Gemini API Key:** Get a free or pay-as-you-go API key from the [Google AI Studio](https://aistudio.google.com/).
2. **A BLE Thermal Printer:** Works with standard 2-inch (58mm) pocket thermal printers supporting 384-pixel width rows.
3. **Android Studio:** Android Studio Ladybug (or newer) with JDK 17+.

### How to Run

1. Clone this repository:
   ```bash
   git clone https://github.com/yourusername/pocket-portrait.git
   ```
2. Open the project in Android Studio.
3. Build and run the app on an Android device running Android 8.0 (API 26) or higher.
4. Input your Gemini API key in the app's **Settings** screen (the key is securely stored in your device's keystore).
5. Turn on your thermal printer, scan for devices in the app, and print!

---

## 🛠️ Code Highlights

### Floyd-Steinberg Dithering Engine
Thermal printers can only print pure black or pure white pixels. Pocket Portrait includes a custom Floyd-Steinberg error-diffusion engine that distributes grayscale quantization errors to adjacent pixels:

```kotlin
fun dither(grayscale: FloatArray, width: Int, height: Int, threshold: Int = 128): IntArray {
    val dithered = IntArray(grayscale.size)
    val pixels = grayscale.clone()

    for (y in 0 until height) {
        for (x in 0 until width) {
            val idx = y * width + x
            val oldVal = pixels[idx]
            val newVal = if (oldVal < threshold) 0 else 255
            dithered[idx] = newVal

            val err = oldVal - newVal

            // Propagate error to neighboring pixels
            if (x + 1 < width) {
                pixels[idx + 1] += err * (7.0f / 16.0f)
            }
            if (x - 1 >= 0 && y + 1 < height) {
                pixels[(y + 1) * width + (x - 1)] += err * (3.0f / 16.0f)
            }
            if (y + 1 < height) {
                pixels[(y + 1) * width + x] += err * (5.0f / 16.0f)
            }
            if (x + 1 < width && y + 1 < height) {
                pixels[(y + 1) * width + (x + 1)] += err * (1.0f / 16.0f)
            }
        }
    }
    return dithered
}
```

### Bluetooth BLE Packets
The app packages dithered pixel rows into 48-byte frames (corresponding to the printer's 384-dot head size), wrapping them with custom GATT headers and a CRC-8 checksum:

```kotlin
fun wrapPacket(commandId: Int, payload: ByteArray): ByteArray {
    val size = payload.size
    val packet = ByteArray(8 + size)
    packet[0] = 0x51.toByte() // Start Magic 1
    packet[1] = 0x78.toByte() // Start Magic 2
    packet[2] = commandId.toByte()
    packet[3] = 0x00.toByte()
    packet[4] = (size and 0xFF).toByte()
    packet[5] = ((size shr 8) and 0xFF).toByte()
    System.arraycopy(payload, 0, packet, 6, size)
    packet[6 + size] = calculateCrc8(payload).toByte() // CRC-8 Checksum
    packet[7 + size] = 0xFF.toByte() // End Footer
    return packet
}
```

---

## 🤝 Contributing

Contributions are welcome! Please feel free to open issues or submit pull requests.
1. Fork the Project.
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`).
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`).
4. Push to the Branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.

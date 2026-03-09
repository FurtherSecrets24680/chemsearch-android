# ChemSearch (for Android)

**ChemSearch** is a native Android application designed for chemists, students, and researchers to quickly search and visualize chemical compounds. It provides a comprehensive set of data, including identifiers, structural models, and AI-generated descriptions, all within a clean, modern Material 3 interface.

---

This project is the native Android version of the original **ChemSearch** web application.

- **Live Web App:** [chemsearch.netlify.app](https://chemsearch.netlify.app/)
- **Web App Repository:** [FurtherSecrets24680/chemsearch](https://github.com/FurtherSecrets24680/chemsearch)

---

## 📱 Requirements

### **For Users (Installing the APK)**
- **Android Version:** Android 8.0 (Oreo) or higher (API level 26+).
- **Internet Connection:** Required for searching compounds, loading structures, and generating AI descriptions.
- **Storage:** Minimal space for app installation and local search history caching.

### **For Developers (Building from Source)**
- **IDE:** Android Studio Hedgehog (2023.1.1) or newer.
- **JDK:** Version 11.
- **Android SDK:** API Level 34 (Compile & Target).
- **Internet:** Required for Gradle sync and library dependencies.

---

## 🚀 Features

### **Intelligent Search**
- **Compound Lookup:** Search by common name, IUPAC name, or CID using the PubChem PUG REST API.
- **Autosuggestions:** Real-time search suggestions as you type (can be toggled in settings).
- **Recent History:** Quickly revisit previous searches with one-tap access.

### **Detailed Chemical Data**
- **Title Card:** Displays the compound name and molecular formula (with proper subscripts).
- **Key Identifiers:** Vertical layout for **Molecular Weight**, **CID**, **CAS Number**, and **Charge**.
- **One-Tap Copy:** Every identifier in the title card and the detailed identifiers section is copyable to the clipboard.
- **Comprehensive Set:** Access IUPAC names, SMILES (Full & Connectivity), InChI, and InChIKey.

### **Visualization & Analysis**
- **Structure Viewer:**
    - **2D Structure:** High-quality PNG images from PubChem.
    - **3D Model:** Interactive 3D visualization using `3Dmol.js` in a WebView, allowing rotation and inspection of molecular geometry.
- **Elemental Analysis:** Visual bar charts showing the percentage composition of every element in the compound, calculated using high-precision atomic weights.

### **Multi-Source Descriptions**
Choose where you get your information:
- **PubChem:** Scientific descriptions directly from the source.
- **Wikipedia:** Quick summaries for general context.
- **AI (Gemini):** Concise, AI-generated descriptions including real-world applications (requires a free API key).

### **Customization**
- **Themes:** Full support for Dark and Light modes.
- **Settings:** Configure your default description source, toggle autosuggestions, and manage your Gemini API key.

---

### ⚠️ Known issues
- 3D model viewer is not properly working
- Autosuggestions is very glitchy (disable it in the settings)
## 🛠️ Tech Stack

| Library | Purpose |
|---|---|
| **Kotlin** | Primary programming language. |
| **Jetpack Compose** | Modern toolkit for building native UI. |
| **Material 3** | Latest Android design system. |
| **Retrofit 2** | Type-safe HTTP client for API interactions. |
| **Coroutines & Flow** | Asynchronous programming and reactive state management. |
| **Coil** | Image loading for 2D structures. |
| **WebView** | Used for hosting the interactive 3D molecular viewer. |

---

## 🏗️ Building from Source

### **Setup**
1. Clone the repository:
   ```bash
   git clone https://github.com/FurtherSecrets24680/chemsearch-android
   ```
2. Open the project in Android Studio.
3. Sync Gradle and build the project.
4. Run on a physical device or emulator (API 26+).

---

## 🔑 AI Descriptions (Optional)
To use AI descriptions, you'll need a Google Gemini API key. You can get a free key at [aistudio.google.com](https://aistudio.google.com). Once obtained, enter it in the app's Settings menu.

---

## 🛡️ Privacy
- **Direct API Calls:** Data is fetched directly from PubChem, Wikipedia, and Google Gemini APIs. No intermediary servers are used.
- **Local Storage:** Your API key and search history are stored locally on your device using `SharedPreferences`.
- **Transparency:** No tracking or telemetry is included.

---

## 📚 Data Sources
- **[PubChem](https://pubchem.ncbi.nlm.nih.gov/)**
- **[Wikipedia](https://en.wikipedia.org/)**
- **[Google Gemini](https://ai.google.dev/)**

---

## 📜 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

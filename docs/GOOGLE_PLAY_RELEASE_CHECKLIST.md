# Google Play release checklist

This checklist documents the Android release configuration and the declarations that still need to be completed accurately in Play Console. It is not a guarantee of approval; Google evaluates the binary and the store listing together.

## Binary

- Package name: `com.audiobookreader` (do not change).
- `targetSdk` and `compileSdk`: API 36.
- Release signing: use the private upload key referenced by the ignored `signing/keystore.properties` file.
- Confirm the AAB upload-certificate SHA-1 is `4F:55:A9:13:DC:D6:26:E5:B7:E0:FA:29:4A:68:DF:A4:8B:12:30:7E` before uploading.
- Upload only a version code greater than the previous Play release.
- Run unit tests, `lintRelease`, 16 KB ZIP-alignment verification, and signature verification for every release.

## App content declarations

- Ads: **No**.
- App access: no account, login, subscription, or restricted area.
- Target audience: general audiobook utility; not designed for children. Select only the actual intended age groups in Play Console and keep the store listing consistent.
- Content rating: answer the questionnaire using the document reader, local/online TTS, user-imported content, and voice-cloning capabilities.
- Data deletion: no account exists. Users can delete generated audio, individual models, or all local app data in Settings; uninstalling also removes private app data.
- Foreground-service declaration: `mediaPlayback` is used for audible playback and progressive TTS preparation while playback continues with the app backgrounded or the screen locked. The persistent Media3 notification gives pause, seek, and stop controls.
- AI-generated content: disclose that the app synthesizes speech. The app warns users to clone only authorized voices and offers a synthesized-audio reporting link in Settings.

## Data safety

Review the final Play form against Google's current definitions. A conservative declaration for this build is:

- No ads, analytics, account identifiers, developer-operated backend, sale of data, or developer tracking.
- Files and documents are selected for app functionality and ordinarily remain on-device.
- Voice-reference audio remains on-device for local PocketTTS/ZipVoice processing.
- If the user expressly chooses Edge TTS, selected text fragments and voice/language/speed parameters are transmitted over TLS directly to Microsoft for app functionality. Declare this third-party transmission in the form wherever Google's current questionnaire requires it, even though the developer does not receive it and it is used transiently for synthesis.
- Connections to GitHub/Hugging Face download only public model files and do not include document text or voice-reference audio.
- Data is encrypted in transit; Android cloud backup is disabled.

## Privacy and store listing

- Privacy-policy URL: `https://github.com/kroryan/Audiobookreader/blob/main/PRIVACY_POLICY.md`
- The policy is also linked from Settings.
- Store screenshots and descriptions must match the current build and must not imply that every model works on every device.
- Mention that local models can be large and computationally demanding, while Edge requires Internet access and sends selected fragments to Microsoft after consent.
- Keep third-party license attribution available at `THIRD_PARTY_NOTICES.md`; models with additional conditions present accept/reject terms before download.


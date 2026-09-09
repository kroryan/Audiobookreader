# Privacy Policy for audiobookreader

Effective date: September 9, 2026

audiobookreader (`com.audiobookreader`) is a free audiobook reader maintained by kroryan. It does not require an account and contains no advertising, analytics, or developer-operated tracking.

## Information stored on your device

The app stores imported-library references, extracted covers and text held in memory, listening progress, bookmarks, preferences, downloaded TTS model files, generated audio cache, and any voice-reference recording you choose to import. These items are kept in the app's private storage. Android cloud backup is disabled for the app.

The original PDF, EPUB, HTML, or text document remains where you selected it. audiobookreader receives persistent read access through Android's system document picker; it does not upload the full document to the developer.

## Local speech providers

Piper, Coqui, Mimic3, Kokoro, Supertonic, PocketTTS, ZipVoice, and imported ONNX models run locally. Text and voice-reference recordings used with these providers do not leave the device through audiobookreader.

Model packages are downloaded from the upstream URLs identified in the model catalogue, primarily GitHub and Hugging Face. Those services receive ordinary connection information such as an IP address and user agent. Model downloads do not include your book text, bookmarks, or voice recordings.

## Optional online Edge TTS

Edge TTS is optional and is disabled for synthesis until you accept its in-app disclosure. When you play a book with an Edge voice, audiobookreader sends only the current text fragments and the selected voice, language, and speed settings directly to Microsoft over an encrypted connection so Microsoft can return synthesized audio. Microsoft may receive ordinary network information such as your IP address. The developer does not receive or retain this information.

Your complete document, library, bookmarks, listening history, local model files, and voice-cloning recordings are not sent to Edge TTS. You can decline Edge and use local models, or revoke Edge consent in Settings.

Microsoft handles information under its own terms and privacy practices. Edge voice availability and endpoint behaviour are controlled by Microsoft.

## Voice cloning

PocketTTS and ZipVoice can use a WAV recording as a local voice reference. The recording remains in private app storage and is not uploaded by audiobookreader. Only import and clone a voice when you have permission from the person concerned. Do not use synthesized audio to impersonate, deceive, defraud, or harm anyone.

## Retention and deletion

Generated audio is a bounded cache that you can clear per book or globally. Individual downloaded model packages can be deleted in Models. Settings also provides **Delete all local data**, which removes the library references, progress, bookmarks, models, generated audio, covers, voice references, and preferences. It does not delete original documents outside the app. Uninstalling the app also removes its private data.

## Security

audiobookreader uses Android private app storage and rejects cleartext network traffic. No security measure is absolute, but the app limits access and transmission to what is needed for the feature you choose.

## Children

audiobookreader is a general-audience utility and is not directed to children under 13. It does not knowingly collect children's personal information.

## Changes and contact

Material changes will be published in this document and reflected by a new effective date. Questions, privacy requests, and synthesized-content reports can be submitted through the project's issue tracker:

https://github.com/kroryan/Audiobookreader/issues


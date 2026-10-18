# Optional Media

[简体中文](../媒体模组安装.md) | **English** · [Documentation](README.md)

The core mod handles ordinary tasks, images and basic audio without a video backend.
Video and additional formats use optional backends such as local FFmpeg, a supported
MediaPlayer adapter or the separate Xero Quest Media module.
Backend availability, operating system and codecs determine which files can play.

## Choose A Backend

| Need | Starting point |
| --- | --- |
| Quest text, item icons, images | Core mod |
| Basic supported audio | Core decoder |
| Video/extended audio | A compatible optional media backend |
| Platform-native distribution | Separate media package for that platform |

Keep the core and optional modules compatible.
Do not install source archives or verification files as mods.
Use the media picker for imported references and test playback in the intended game instance.
An imported path does not prove that all clients or a remote server received the media.

## Build

Example platform:

```powershell
.\gradlew.bat build verifyMediaAddonPackaging -Pmedia_platform=windows-x86_64 --no-daemon
```

The 5,000,000-byte size check applies to the core JAR rather than optional native codecs.
Dependencies and third-party media retain their own licenses:
[media notices](../MEDIA-NOTICES.txt).
The [Chinese backend reference](../媒体模组安装.md) contains platform-specific details.

## Diagnose Playback

Check media kind/source, backend installation, supported codec, file availability and logs.
Try a known local supported image/audio first, then the target video.
Changing chapter/background should release the prior playback.
Remote media distribution and all backend/platform combinations are not fully verified.
